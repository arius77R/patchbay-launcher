import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Everything that differs between macOS and Windows: where files live, how Patchbay starts, how
 * to tell whether Bitwig runs, notifications, and pinning the launcher (Dock or Start menu).
 * Anything that isn't Windows is treated as macOS (the offline tests run on Linux that way).
 */
final class Platform {
    private Platform() { }

    static final boolean WINDOWS = System.getProperty("launcher.os", System.getProperty("os.name", ""))
        .toLowerCase(Locale.ROOT).startsWith("windows");

    /** The word in Patchbay's release and folder names: BML-<version>-<suffix>. */
    static String suffix() { return WINDOWS ? "windows" : "macos"; }
    static String releasePattern() { return "BML-*-" + suffix() + ".zip"; }
    /** The file that marks a complete Patchbay folder. */
    static String startScript() { return WINDOWS ? "bml-ui.cmd" : "bml-ui.sh"; }

    private static Path localAppData() {
        String v = System.getenv("LOCALAPPDATA");
        return v != null && !v.isBlank() ? Path.of(v) : Path.of(System.getProperty("user.home"), "AppData", "Local");
    }

    /** The launcher's own folder. */
    static Path data() {
        return WINDOWS ? localAppData().resolve("PatchbayLauncher")
            : Path.of(System.getProperty("user.home"), "Library", "Application Support", "PatchbayLauncher");
    }

    /** Patchbay's per-user settings folder (its BmlPaths). */
    static Path patchbaySettings() {
        return WINDOWS ? localAppData().resolve("BML")
            : Path.of(System.getProperty("user.home"), "Library", "Application Support", "BML");
    }

    // ---------- unpacking ----------

    /** Unpacks a ZIP into an empty folder: the system's unzip on macOS (keeps the scripts runnable), Java on Windows. */
    static void unzip(Path zip, Path into) throws IOException, InterruptedException {
        if (!WINDOWS && Files.isExecutable(Path.of("/usr/bin/unzip"))) {
            int code = new ProcessBuilder("/usr/bin/unzip", "-q", "-o", zip.toString(), "-d", into.toString())
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor();
            if (code != 0) throw new IOException(PatchbayLauncher.t("Archiwum jest uszkodzone (unzip: ", "The archive is damaged (unzip: ") + code + ")");
            return;
        }
        Path root = into.toAbsolutePath().normalize();
        try (ZipFile z = new ZipFile(zip.toFile(), StandardCharsets.UTF_8)) {
            byte[] buf = new byte[1 << 16];
            for (Enumeration<? extends ZipEntry> all = z.entries(); all.hasMoreElements(); ) {
                ZipEntry e = all.nextElement();
                Path out = root.resolve(e.getName()).normalize();
                if (!out.startsWith(root)) throw new IOException("unsafe path in the ZIP (" + e.getName() + ")");
                if (e.isDirectory()) { Files.createDirectories(out); continue; }
                Files.createDirectories(out.getParent());
                try (InputStream in = z.getInputStream(e); var o = Files.newOutputStream(out)) {
                    for (int n; (n = in.read(buf)) != -1; ) o.write(buf, 0, n);
                }
            }
        } catch (java.util.zip.ZipException bad) {
            throw new IOException(PatchbayLauncher.t("Archiwum jest uszkodzone (unzip: ", "The archive is damaged (unzip: ") + bad.getMessage() + ")");
        }
    }

    // ---------- starting Patchbay ----------

    /** Starts the Patchbay window of the install in root, its output appended to log. */
    static void startPatchbay(Path root, Path log) throws IOException {
        ProcessBuilder pb;
        if (WINDOWS) {
            // what bml-ui.cmd does, without a console window: the window's jar on a Java without a console
            Path java = windowsJava();
            if (java == null) throw new IOException(PatchbayLauncher.t("Nie znaleziono Javy z Bitwig Studio.", "Bitwig Studio's Java wasn't found."));
            pb = new ProcessBuilder(java.toString(), "--enable-native-access=ALL-UNNAMED", "-Dbml.root=" + root,
                "-jar", root.resolve("loader").resolve("app").resolve("bml-control.jar").toString());
        } else {
            pb = new ProcessBuilder("/bin/sh", root.resolve("bml-ui.sh").toString());
        }
        pb.directory(root.toFile()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile())).start();
    }

    /** javaw.exe: the one running the launcher (Bitwig's), else Bitwig's in its usual place, else JAVA_HOME's. */
    static Path windowsJava() {
        List<Path> tries = new ArrayList<>();
        tries.add(Path.of(System.getProperty("java.home"), "bin", "javaw.exe"));
        Path bitwig = windowsBitwigExe();
        if (bitwig != null) tries.add(bitwig.getParent().resolve("jre").resolve("bin").resolve("javaw.exe"));
        String home = System.getenv("JAVA_HOME");
        if (home != null) tries.add(Path.of(home, "bin", "javaw.exe"));
        for (Path p : tries) if (Files.isRegularFile(p)) return p;
        return null;
    }

    /** Bitwig Studio.exe: the one chosen in Patchbay's settings, else the default install place. */
    static Path windowsBitwigExe() {
        Path props = patchbaySettings().resolve("bml.properties");
        if (Files.isRegularFile(props)) {
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(props)) {
                p.load(in);
                String chosen = p.getProperty("bitwig.executable");
                if (chosen != null && Files.isRegularFile(Path.of(chosen))) return Path.of(chosen);
            } catch (IOException | RuntimeException ignored) { }
        }
        String pf = System.getenv("ProgramFiles");
        Path def = Path.of(pf != null ? pf : "C:\\Program Files", "Bitwig Studio", "Bitwig Studio.exe");
        return Files.isRegularFile(def) ? def : null;
    }

    // ---------- processes ----------

    private static String run(String... cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (p.waitFor() != 0) throw new IOException(out.strip());
        return out;
    }

    private static boolean pgrep(String... args) {
        try {
            List<String> cmd = new ArrayList<>(List.of("/usr/bin/pgrep"));
            cmd.addAll(List.of(args));
            return new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor() == 0;
        } catch (IOException | InterruptedException e) { return false; }
    }

    /** Windows: the processes Patchbay itself looks for (BitwigStudioApp.exe, Bitwig Studio.exe). */
    static boolean bitwigRunning() {
        if (!WINDOWS) return pgrep("-x", "BitwigStudio");
        return ProcessHandle.allProcesses().anyMatch(ph -> {
            String cmd = ph.info().command().orElse("").replace('\\', '/').toLowerCase(Locale.ROOT);
            return cmd.endsWith("/bitwigstudioapp.exe") || cmd.endsWith("/bitwig studio.exe");
        });
    }

    /** Windows hides other processes' arguments from Java, so the Java processes are asked through PowerShell. */
    static boolean patchbayRunning() {
        if (!WINDOWS) return pgrep("-f", "bml-control.jar");
        try {
            String out = run("powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                "@(Get-CimInstance Win32_Process -Filter \"Name='javaw.exe' OR Name='java.exe'\" | "
                    + "Where-Object { $_.CommandLine -like '*bml-control.jar*' }).Count");
            return Integer.parseInt(out.strip()) > 0;
        } catch (IOException | InterruptedException | NumberFormatException e) {
            return false;
        }
    }

    // ---------- Bitwig's version ----------

    static String bitwigVersion() {
        String preview = System.getProperty("launcher.bitwig"); // offline preview: pretend this Bitwig version
        if (preview != null) return preview;
        if (WINDOWS) {
            Path exe = windowsBitwigExe();
            if (exe == null) return null;
            try {
                ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                    "$v = (Get-Item -LiteralPath $env:PL_BITWIG).VersionInfo; if ($v.ProductVersion) { $v.ProductVersion } else { $v.FileVersion }");
                pb.environment().put("PL_BITWIG", exe.toString());
                Process p = pb.redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                return p.waitFor() == 0 ? cleanVersion(out) : null;
            } catch (IOException | InterruptedException e) {
                return null;
            }
        }
        for (String app : new String[] { "/Applications/Bitwig Studio.app", System.getProperty("user.home") + "/Applications/Bitwig Studio.app" }) {
            Path plist = Path.of(app, "Contents", "Info.plist");
            if (!Files.isRegularFile(plist)) continue;
            try {
                String v = cleanVersion(run("/usr/bin/plutil", "-extract", "CFBundleShortVersionString", "raw", plist.toString()));
                if (v != null) return v;
            } catch (IOException | InterruptedException ignored) { }
        }
        return null;
    }

    /** "6.1.4", "6.1.4.123" or "6.1.4 (build)" to the leading dotted number; null when there is none. */
    static String cleanVersion(String raw) {
        if (raw == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+(?:\\.\\d+)+").matcher(raw.strip());
        if (!m.find()) return null;
        String v = m.group();
        String[] parts = v.split("\\.");
        return parts.length > 3 && parts[3].equals("0") ? parts[0] + "." + parts[1] + "." + parts[2] : v;
    }

    // ---------- notifications ----------

    static void notifyUser(String text) {
        if (WINDOWS) {
            try {
                if (!SystemTray.isSupported()) return;
                java.net.URL url = Platform.class.getResource("/icon-128.png");
                Image img = url != null ? Toolkit.getDefaultToolkit().getImage(url) : new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                TrayIcon icon = new TrayIcon(img, "Patchbay Launcher");
                icon.setImageAutoSize(true);
                SystemTray.getSystemTray().add(icon);
                icon.displayMessage("Patchbay Launcher", text, TrayIcon.MessageType.INFO);
                Thread t = new Thread(() -> {
                    try { Thread.sleep(12_000); } catch (InterruptedException ignored) { }
                    SystemTray.getSystemTray().remove(icon);
                }, "tray-cleanup");
                t.setDaemon(true);
                t.start();
            } catch (Exception ignored) { }
            return;
        }
        try {
            String safe = text.replace("\\", "").replace("\"", "'");
            new ProcessBuilder("/usr/bin/osascript", "-e",
                "display notification \"" + safe + "\" with title \"Patchbay Launcher\"")
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException ignored) { }
    }

    // ---------- pinning: Dock on macOS, Start menu and desktop on Windows ----------

    /** The .app this jar runs from (…/Patchbay Launcher.app/Contents/Resources/launcher.jar), or null. */
    static Path appBundle() {
        try {
            Path jar = Path.of(Platform.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path app = jar.getParent() == null || jar.getParent().getParent() == null ? null : jar.getParent().getParent().getParent();
            return app != null && app.getFileName().toString().endsWith(".app") ? app : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Windows: Patchbay Launcher.exe, which passes its own path when it starts the launcher. */
    static Path launcherExe() {
        String p = System.getProperty("launcher.exe");
        return p != null && Files.isRegularFile(Path.of(p)) ? Path.of(p) : null;
    }

    /** The Dock keeps a path; only an app in an Applications folder stays put, so only that is offered. */
    static boolean inApplications(Path app) {
        if (app == null) return false;
        Path parent = app.toAbsolutePath().normalize().getParent();
        return parent != null && (parent.equals(Path.of("/Applications"))
            || parent.equals(Path.of(System.getProperty("user.home"), "Applications")));
    }

    static String pinLabel() {
        return WINDOWS ? PatchbayLauncher.t("Dodaj skrót do menu Start", "Add to the Start menu") : PatchbayLauncher.t("Dodaj do Docka", "Add to Dock");
    }

    static String pinQuestion() {
        return WINDOWS
            ? PatchbayLauncher.t("Dodać skrót do Patchbay Launcher w menu Start i na pulpicie?", "Add Patchbay Launcher to the Start menu and the desktop?")
            : PatchbayLauncher.t("Dodać Patchbay Launcher do Docka, żeby był pod jednym kliknięciem?", "Add Patchbay Launcher to the Dock, so it's one click away?");
    }

    static String pinnedMessage() {
        return WINDOWS ? PatchbayLauncher.t("Skrót jest w menu Start i na pulpicie.", "The shortcut is in the Start menu and on the desktop.")
            : PatchbayLauncher.t("Patchbay Launcher jest teraz w Docku.", "Patchbay Launcher is in the Dock now.");
    }

    static String pinFailedMessage() {
        return WINDOWS ? PatchbayLauncher.t("Nie udało się utworzyć skrótu. Kliknij Patchbay Launcher.exe prawym przyciskiem i wybierz „Przypnij do menu Start”.",
                "Couldn't create the shortcut. Right-click Patchbay Launcher.exe and choose \"Pin to Start\".")
            : PatchbayLauncher.t("Nie udało się dodać do Docka. Przeciągnij Patchbay Launcher z Aplikacji na Dock.",
                "Couldn't add it to the Dock. Drag Patchbay Launcher from Applications onto the Dock instead.");
    }

    /** null when pinning can go ahead, else why not (macOS: the app must be in Applications). */
    static String pinBlocked() {
        if (WINDOWS) return launcherExe() == null ? "" : null;
        Path app = appBundle();
        if (app == null) return "";
        return inApplications(app) ? null : PatchbayLauncher.t("Najpierw przenieś Patchbay Launcher do folderu Aplikacje, a potem dodaj go do Docka.",
            "Move Patchbay Launcher to the Applications folder first, then add it to the Dock.");
    }

    static Path startMenuShortcut() {
        String appdata = System.getenv("APPDATA");
        Path base = appdata != null ? Path.of(appdata) : Path.of(System.getProperty("user.home"), "AppData", "Roaming");
        return base.resolve("Microsoft").resolve("Windows").resolve("Start Menu").resolve("Programs").resolve("Patchbay Launcher.lnk");
    }

    static boolean pinned() {
        if (WINDOWS) return Files.isRegularFile(startMenuShortcut());
        Path app = appBundle();
        if (app == null) return false;
        try {
            String apps = run("/usr/bin/defaults", "read", "com.apple.dock", "persistent-apps");
            String name = app.getFileName().toString();
            return apps.contains(name.replace(" ", "%20")) || apps.contains(name);
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    /** The entry appended to the Dock's list of apps; the Dock must be restarted to show it. */
    static String dockEntry(Path app) {
        String path = app.toAbsolutePath().normalize().toString()
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<dict><key>tile-data</key><dict><key>file-data</key><dict><key>_CFURLString</key><string>" + path
            + "</string><key>_CFURLStringType</key><integer>0</integer></dict></dict></dict>";
    }

    /** The PowerShell that writes both shortcuts; the exe's path comes in PL_EXE, so no quoting is needed. */
    static final String SHORTCUT_SCRIPT = "$s = New-Object -ComObject WScript.Shell; "
        + "foreach ($p in @($env:PL_START, (Join-Path ([Environment]::GetFolderPath('Desktop')) 'Patchbay Launcher.lnk'))) { "
        + "$l = $s.CreateShortcut($p); $l.TargetPath = $env:PL_EXE; $l.WorkingDirectory = (Split-Path -Parent $env:PL_EXE); "
        + "$l.IconLocation = $env:PL_EXE + ',0'; $l.Description = 'Patchbay Launcher'; $l.Save() }";

    static void pin() throws IOException, InterruptedException {
        if (WINDOWS) {
            Path exe = launcherExe();
            if (exe == null) throw new IOException("launcher.exe");
            Files.createDirectories(startMenuShortcut().getParent());
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", SHORTCUT_SCRIPT);
            pb.environment().put("PL_EXE", exe.toAbsolutePath().toString());
            pb.environment().put("PL_START", startMenuShortcut().toString());
            Process p = pb.redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (p.waitFor() != 0 || !Files.isRegularFile(startMenuShortcut())) throw new IOException(out.strip());
            return;
        }
        Path app = appBundle();
        run("/usr/bin/defaults", "write", "com.apple.dock", "persistent-apps", "-array-add", dockEntry(app));
        run("/usr/bin/killall", "Dock");
    }

    /** macOS, once per launcher version: tells Finder to read the app's icon again (it keeps old icons of a replaced app). */
    static void refreshIcon(String version) {
        if (WINDOWS) return;
        Path app = appBundle();
        if (app == null || version.equals(I18n.setting("iconRefreshed"))) return;
        try {
            run("/usr/bin/touch", app.toString());
            String ls = "/System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister";
            if (Files.isExecutable(Path.of(ls))) run(ls, "-f", app.toString());
            I18n.remember("iconRefreshed", version);
        } catch (IOException | InterruptedException ignored) {
            // the icon then refreshes the next time macOS rescans the app
        }
    }
}
