import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;

/**
 * The launcher updating itself from its own GitHub releases (arius77R/patchbay-launcher, "Latest").
 * The new package is downloaded next to the installed one, checked against the SHA-256 GitHub lists
 * for it and unpacked there; a small script then waits for the launcher to close, puts the new copy
 * in place of the old one (the old one comes back if that fails) and starts it. The launcher's data
 * (Patchbay versions, settings) lives elsewhere and is not touched.
 */
final class SelfUpdate {
    private SelfUpdate() { }

    static final String REPO = "arius77R/patchbay-launcher";

    /** A launcher release: its version, its page, and this system's package. */
    /** notes: the release's description as written on GitHub (Markdown), or null. */
    record Release(String version, String page, String url, String sha256, String notes) { }

    /** This system's package in a release. */
    static String assetSuffix() { return Platform.WINDOWS ? "-windows.zip" : Platform.LINUX ? "-linux.tar.gz" : "-macos.zip"; }

    /** The folder or app the package unpacks to. */
    static String packageRoot() { return Platform.WINDOWS ? "Patchbay Launcher" : Platform.LINUX ? "Patchbay-Launcher" : "Patchbay Launcher.app"; }

    /** The newest release from GitHub's /releases/latest answer, with this system's package; null without one. */
    static Release parse(String json) {
        Map<String, Object> root = Mods.Json.object(Mods.Json.parse(json));
        String tag = Mods.Json.text(root, "tag_name");
        if (tag == null) return null;
        String version = tag.startsWith("v") ? tag.substring(1) : tag;
        for (Object a : Mods.Json.array(root.get("assets"))) {
            Map<String, Object> asset = Mods.Json.object(a);
            String name = Mods.Json.text(asset, "name"), url = Mods.Json.text(asset, "browser_download_url");
            if (name == null || url == null || !name.startsWith("Patchbay-Launcher-") || !name.endsWith(assetSuffix())) continue;
            String digest = Mods.Json.text(asset, "digest");
            String sha = digest != null && digest.startsWith("sha256:") ? digest.substring(7).toLowerCase(Locale.ROOT) : null;
            return new Release(version, Mods.Json.text(root, "html_url"), url, sha, Mods.Json.text(root, "body"));
        }
        return null;
    }

    /**
     * The "What's new" part of a release description as simple HTML: the lines under a "## What's new…" heading up to the next
     * heading (the whole description when there is no such heading). Lists, **bold**, `code`, [links](…) and > notes are kept.
     */
    static String whatsNewHtml(String notes) {
        if (notes == null || notes.isBlank()) return null;
        String[] lines = notes.replace("\r", "").split("\n");
        int from = 0, to = lines.length;
        for (int i = 0; i < lines.length; i++) if (lines[i].toLowerCase(Locale.ROOT).startsWith("## what's new")) { from = i + 1; break; }
        for (int i = from; i < lines.length; i++) if (from > 0 && lines[i].startsWith("## ")) { to = i; break; }
        StringBuilder html = new StringBuilder();
        boolean list = false;
        for (int i = from; i < to; i++) {
            String line = lines[i].strip();
            boolean item = line.startsWith("- ") || line.startsWith("* ");
            if (list && !item) { html.append("</ul>"); list = false; }
            if (line.isEmpty()) continue;
            if (item) {
                if (!list) { html.append("<ul style='margin-left:16px'>"); list = true; }
                html.append("<li>").append(inline(line.substring(2))).append("</li>");
            } else if (line.startsWith(">")) {
                html.append("<p><i>").append(inline(line.substring(1).strip())).append("</i></p>");
            } else if (line.startsWith("#")) {
                html.append("<p><b>").append(inline(line.replaceFirst("^#+\\s*", ""))).append("</b></p>");
            } else {
                html.append("<p>").append(inline(line)).append("</p>");
            }
        }
        if (list) html.append("</ul>");
        return html.toString();
    }

    private static String inline(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>")
            .replaceAll("`(.+?)`", "<code>$1</code>")
            .replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1");
    }

    /** The newest release, or null; a GitHub error is passed on. */
    static Release latest() throws IOException {
        String f = System.getProperty("launcher.fixtures"); // offline test
        Path fx = f == null ? null : Path.of(f).resolve("launcher-latest.json");
        if (fx != null) return Files.isRegularFile(fx) ? parse(Files.readString(fx)) : null;
        return parse(PatchbayLauncher.httpText("https://api.github.com/repos/" + REPO + "/releases/latest"));
    }

    static boolean newer(Release r) { return r != null && PatchbayLauncher.compareVersions(r.version(), PatchbayLauncher.VERSION) > 0; }

    /** Where the running launcher is installed (its .app on macOS, its folder elsewhere); null when it runs from somewhere else. */
    static Path installRoot() {
        try {
            Path jar = Path.of(SelfUpdate.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return rootFor(jar);
        } catch (URISyntaxException | RuntimeException e) {
            return null;
        }
    }

    /** The install that holds this launcher.jar: Patchbay Launcher.app/Contents/Resources/ on macOS, &lt;folder&gt;/app/ elsewhere. */
    static Path rootFor(Path jar) {
        if (jar == null || !jar.getFileName().toString().equals("launcher.jar")) return null;
        Path dir = jar.toAbsolutePath().normalize().getParent();
        if (Platform.MAC) {
            if (dir == null || !dir.getFileName().toString().equals("Resources")) return null;
            Path contents = dir.getParent();
            if (contents == null || !contents.getFileName().toString().equals("Contents")) return null;
            Path app = contents.getParent();
            return app != null && app.getFileName().toString().endsWith(".app") ? app : null;
        }
        if (dir == null || !dir.getFileName().toString().equals("app")) return null;
        Path root = dir.getParent();
        String starter = Platform.WINDOWS ? "Patchbay Launcher.exe" : "patchbay-launcher";
        return root != null && Files.isRegularFile(root.resolve(starter)) ? root : null;
    }

    /** The jar inside an unpacked package, to check it is a launcher package. */
    static Path jarIn(Path root) {
        return Platform.MAC ? root.resolve("Contents/Resources/launcher.jar") : root.resolve("app/launcher.jar");
    }

    /**
     * Downloads and unpacks the release next to the installed launcher and starts the script that
     * swaps it in once this launcher has quit. The caller quits the launcher right after.
     */
    static void prepare(Release r, Path root, Consumer<String> status) throws IOException, InterruptedException {
        Path parent = root.getParent();
        if (parent == null || !Files.isWritable(parent))
            throw new IOException(PatchbayLauncher.t("Brak uprawnień do zapisu w folderze ", "No permission to write in the folder ") + parent
                + PatchbayLauncher.t(". Pobierz nową wersję ze strony projektu.", ". Download the new version from the project page."));
        Path stage = parent.resolve(".patchbay-launcher-update");
        Themes.remove(stage);
        Files.createDirectories(stage);
        String name = r.url().substring(r.url().lastIndexOf('/') + 1);
        Path archive = stage.resolve(name);
        status.accept(PatchbayLauncher.t("Pobieranie Launchera ", "Downloading the launcher ") + r.version() + "…");
        String f = System.getProperty("launcher.fixtures");
        if (f != null && Files.isRegularFile(Path.of(f).resolve(name))) Files.copy(Path.of(f).resolve(name), archive);
        else PatchbayLauncher.download(r.url(), archive, null);
        if (r.sha256() != null && !r.sha256().equals(sha256(archive)))
            throw new IOException(PatchbayLauncher.t("Pobrany plik różni się od wydania na GitHubie. Spróbuj ponownie.",
                "The downloaded file differs from the release on GitHub. Try again."));
        status.accept(PatchbayLauncher.t("Rozpakowywanie…", "Unpacking…"));
        Path unpacked = stage.resolve("new");
        Files.createDirectories(unpacked);
        if (name.endsWith(".tar.gz")) {
            int code = new ProcessBuilder("tar", "-xzf", archive.toString(), "-C", unpacked.toString())
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor();
            if (code != 0) throw new IOException("tar: " + code);
        } else {
            Platform.unzip(archive, unpacked);
        }
        Path fresh = unpacked.resolve(packageRoot());
        if (!Files.isRegularFile(jarIn(fresh)))
            throw new IOException(PatchbayLauncher.t("W pobranym pliku nie ma Launchera.", "The downloaded file holds no launcher."));
        Files.deleteIfExists(archive);
        Path script = stage.resolve(Platform.WINDOWS ? "swap.ps1" : "swap.sh");
        long pid = ProcessHandle.current().pid();
        if (Platform.WINDOWS) {
            // PowerShell reads a script with a byte-order mark as UTF-8, so folder names with ł, ż… survive
            Files.write(script, ("﻿" + windowsScript(pid, root, fresh, stage, failedNote())).getBytes(StandardCharsets.UTF_8));
            start(List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-File", script.toString()), stage);
        } else {
            Files.writeString(script, unixScript(pid, root, fresh, stage, failedNote()));
            start(List.of("/bin/sh", script.toString()), stage);
        }
    }

    /**
     * Starts the swap script in the folder that holds the install, not inside it: Windows won't rename a
     * folder that a running program has as its working folder, and the launcher's own is the install.
     */
    private static void start(List<String> cmd, Path stage) throws IOException {
        Path log = PatchbayLauncher.data().resolve("logs").resolve("self-update.log");
        Files.createDirectories(log.getParent());
        Files.deleteIfExists(failedNote());
        new ProcessBuilder(cmd).directory(stage.getParent().toFile())
            .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile())).start();
    }

    /** Written by the swap script when the new copy could not be put in place; the launcher reads it on its next start. */
    static Path failedNote() {
        return PatchbayLauncher.data().resolve("logs").resolve("self-update-failed.txt").toAbsolutePath(); // the script works in another folder
    }

    /** Why the last update failed, once (the note is removed), or null. */
    static String takeFailure() {
        try {
            Path note = failedNote();
            if (!Files.isRegularFile(note)) return null;
            String why = Files.readString(note, StandardCharsets.UTF_8).replace("\ufeff", "").strip();
            Files.deleteIfExists(note);
            return why.isEmpty() ? "?" : why;
        } catch (IOException e) {
            return null;
        }
    }

    private static String sh(Path p) { return "'" + p.toString().replace("'", "'\\''") + "'"; }
    private static String ps(Path p) { return "'" + p.toString().replace("'", "''") + "'"; }

    /** Waits for the launcher to quit, swaps the folders (the old one comes back if that fails), starts the launcher. */
    static String unixScript(long pid, Path root, Path fresh, Path stage, Path failed) {
        Path old = root.resolveSibling(root.getFileName() + ".old-update");
        String start = Platform.MAC ? "/usr/bin/open " + sh(root)
            : "nohup " + sh(root.resolve("patchbay-launcher")) + " >/dev/null 2>&1 &";
        return "#!/bin/sh\n"
            + "# Patchbay Launcher update: wait for the launcher to quit, then put the new copy in place.\n"
            + "cd " + sh(root.getParent()) + "\n"
            + "n=0\nwhile kill -0 " + pid + " 2>/dev/null && [ $n -lt 120 ]; do sleep 0.5; n=$((n+1)); done\n"
            + "rm -rf " + sh(old) + "\n"
            + "fail=\n"
            + "if mv " + sh(root) + " " + sh(old) + "; then\n"
            + "  if mv " + sh(fresh) + " " + sh(root) + "; then rm -rf " + sh(old) + "; echo \"updated\"\n"
            + "  else mv " + sh(old) + " " + sh(root) + "; fail=\"could not put the new copy in place\"; fi\n"
            + "else fail=\"could not move the old copy\"; fi\n"
            + "if [ -n \"$fail\" ]; then echo \"$fail\"; echo \"$fail\" > " + sh(failed) + "; fi\n"
            + "rm -rf " + sh(stage) + "\n"
            + start + "\n";
    }

    /**
     * The Windows swap: works from the folder that holds the install, and tries the rename for about ten
     * seconds, as an antivirus or Explorer may still hold the old folder for a moment after the launcher quits.
     */
    static String windowsScript(long pid, Path root, Path fresh, Path stage, Path failed) {
        String oldName = root.getFileName() + ".old-update";
        Path old = root.resolveSibling(oldName);
        return "# Patchbay Launcher update: wait for the launcher to quit, then put the new copy in place.\r\n"
            + "Set-Location -LiteralPath " + ps(root.getParent()) + "\r\n"
            + "try { Wait-Process -Id " + pid + " -Timeout 60 -ErrorAction Stop } catch { }\r\n"
            + "Start-Sleep -Milliseconds 700\r\n"
            + "if (Test-Path -LiteralPath " + ps(old) + ") { Remove-Item -LiteralPath " + ps(old) + " -Recurse -Force -ErrorAction SilentlyContinue }\r\n"
            + "$moved = $false; $fail = $null; $why = $null\r\n"
            + "for ($i = 0; $i -lt 10 -and -not $moved; $i++) {\r\n"
            + "  try { Rename-Item -LiteralPath " + ps(root) + " -NewName " + ps(Path.of(oldName)) + " -ErrorAction Stop; $moved = $true }\r\n"
            + "  catch { $why = $_; Start-Sleep -Seconds 1 }\r\n"
            + "}\r\n"
            + "if ($moved) {\r\n"
            + "  try { Move-Item -LiteralPath " + ps(fresh) + " -Destination " + ps(root) + " -ErrorAction Stop; "
            + "Remove-Item -LiteralPath " + ps(old) + " -Recurse -Force -ErrorAction SilentlyContinue; 'updated' }\r\n"
            + "  catch { Rename-Item -LiteralPath " + ps(old) + " -NewName " + ps(root.getFileName()) + "; $fail = 'could not put the new copy in place: ' + $_ }\r\n"
            + "} else { $fail = 'could not move the old copy: ' + $why }\r\n"
            + "if ($fail) { $fail; Set-Content -LiteralPath " + ps(failed) + " -Value $fail -Encoding UTF8 }\r\n"
            + "Remove-Item -LiteralPath " + ps(stage) + " -Recurse -Force -ErrorAction SilentlyContinue\r\n"
            + "Start-Process -FilePath " + ps(root.resolve("Patchbay Launcher.exe")) + " -WorkingDirectory " + ps(root) + "\r\n";
    }

    static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[1 << 16];
            for (int n; (n = in.read(buf)) > 0; ) d.update(buf, 0, n);
            return HexFormat.of().formatHex(d.digest());
        } catch (java.security.NoSuchAlgorithmException never) {
            throw new IOException(never);
        }
    }
}
