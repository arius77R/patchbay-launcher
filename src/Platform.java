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
 * Everything that differs between macOS, Windows and Linux: where files live, how Patchbay starts,
 * how to tell whether Bitwig runs, notifications, and pinning the launcher (Dock, Start menu or the
 * applications menu). -Dlauncher.os=mac|windows|linux overrides the system's own name (the offline
 * tests run every system's code paths that way).
 */
final class Platform {
    private Platform() { }

    private static final String OS = System.getProperty("launcher.os", System.getProperty("os.name", "")).toLowerCase(Locale.ROOT);
    static final boolean WINDOWS = OS.startsWith("windows");
    static final boolean MAC = OS.startsWith("mac") || OS.startsWith("darwin");
    /** Linux and other Unix-likes, which Patchbay's Linux build serves. */
    static final boolean LINUX = !WINDOWS && !MAC;

    /** The word in Patchbay's release and folder names: BML-<version>-<suffix>. */
    static String suffix() { return WINDOWS ? "windows" : LINUX ? "linux" : "macos"; }
    static String releasePattern() { return "BML-*-" + suffix() + ".zip"; }
    /** The file that marks a complete Patchbay folder. */
    static String startScript() { return WINDOWS ? "bml-ui.cmd" : "bml-ui.sh"; }

    private static Path localAppData() {
        String v = System.getenv("LOCALAPPDATA");
        return v != null && !v.isBlank() ? Path.of(v) : Path.of(System.getProperty("user.home"), "AppData", "Local");
    }

    /** An XDG base folder ($XDG_DATA_HOME, $XDG_CONFIG_HOME), else its default under the home folder. */
    private static Path xdg(String variable, String fallback) {
        String v = System.getenv(variable);
        return v != null && v.startsWith("/") ? Path.of(v) : Path.of(System.getProperty("user.home"), fallback);
    }

    /** The launcher's own folder. */
    static Path data() {
        if (WINDOWS) return localAppData().resolve("PatchbayLauncher");
        if (LINUX) return xdg("XDG_DATA_HOME", ".local/share").resolve("PatchbayLauncher");
        return Path.of(System.getProperty("user.home"), "Library", "Application Support", "PatchbayLauncher");
    }

    /** Patchbay's per-user settings folder (its BmlPaths; on Linux $XDG_CONFIG_HOME/bml, as in its platform.sh). */
    static Path patchbaySettings() {
        if (WINDOWS) return localAppData().resolve("BML");
        if (LINUX) return xdg("XDG_CONFIG_HOME", ".config").resolve("bml");
        return Path.of(System.getProperty("user.home"), "Library", "Application Support", "BML");
    }

    // ---------- unpacking ----------

    /**
     * Unpacks a ZIP into an empty folder: the system's unzip on macOS and Linux (keeps the scripts
     * runnable), Java elsewhere (on macOS and Linux it then marks the .sh scripts runnable itself).
     */
    static void unzip(Path zip, Path into) throws IOException, InterruptedException {
        Path unzip = Files.isExecutable(Path.of("/usr/bin/unzip")) ? Path.of("/usr/bin/unzip") : Path.of("/bin/unzip");
        if (!WINDOWS && Files.isExecutable(unzip)) {
            int code = new ProcessBuilder(unzip.toString(), "-q", "-o", zip.toString(), "-d", into.toString())
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
                if (!WINDOWS && out.getFileName().toString().endsWith(".sh")) out.toFile().setExecutable(true, false);
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

    /** The Bitwig executable chosen in Patchbay's settings (bitwig.executable in bml.properties), or null. */
    static Path chosenBitwig() {
        Path props = patchbaySettings().resolve("bml.properties");
        if (!Files.isRegularFile(props)) return null;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(props)) {
            p.load(in);
            String chosen = p.getProperty("bitwig.executable");
            return chosen != null && Files.exists(Path.of(chosen)) ? Path.of(chosen) : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Bitwig Studio.exe: the one chosen in Patchbay's settings, else the default install place. */
    static Path windowsBitwigExe() {
        Path chosen = chosenBitwig();
        if (chosen != null && Files.isRegularFile(chosen)) return chosen;
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
            List<String> cmd = new ArrayList<>(List.of(Files.isExecutable(Path.of("/usr/bin/pgrep")) ? "/usr/bin/pgrep" : "pgrep"));
            cmd.addAll(List.of(args));
            return new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor() == 0;
        } catch (IOException | InterruptedException e) { return false; }
    }

    /** Windows: the processes Patchbay itself looks for (BitwigStudioApp.exe, Bitwig Studio.exe). */
    static boolean bitwigRunning() {
        if (MAC) return pgrep("-x", "BitwigStudio");
        if (LINUX) {
            // as Patchbay's platform.sh: this user's JVM whose command line names Bitwig's main class
            String me = System.getProperty("user.name");
            return ProcessHandle.allProcesses().anyMatch(ph -> me.equals(ph.info().user().orElse(""))
                && ph.info().commandLine().orElse("").contains("com.bitwig.flt.app.BitwigStudioMain"));
        }
        return ProcessHandle.allProcesses().anyMatch(ph -> {
            String cmd = ph.info().command().orElse("").replace('\\', '/').toLowerCase(Locale.ROOT);
            return cmd.endsWith("/bitwigstudioapp.exe") || cmd.endsWith("/bitwig studio.exe");
        });
    }

    /** Windows hides other processes' arguments from Java, so the Java processes are asked through PowerShell. */
    static boolean patchbayRunning() {
        if (MAC) return pgrep("-f", "bml-control.jar");
        if (LINUX) {
            String me = System.getProperty("user.name");
            return ProcessHandle.allProcesses().anyMatch(ph -> me.equals(ph.info().user().orElse(""))
                && ph.info().commandLine().orElse("").contains("bml-control.jar"));
        }
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
        if (LINUX) return linuxBitwigVersion();
        // macOS: the Bitwig chosen in Patchbay first (…/Bitwig Studio.app/Contents/MacOS/BitwigStudio), so both look at the same one
        List<String> apps = new ArrayList<>();
        for (Path p = chosenBitwig(); p != null; p = p.getParent()) {
            if (p.getFileName() != null && p.getFileName().toString().endsWith(".app")) { apps.add(p.toString()); break; }
        }
        apps.add("/Applications/Bitwig Studio.app");
        apps.add(System.getProperty("user.home") + "/Applications/Bitwig Studio.app");
        for (String app : apps) {
            Path plist = Path.of(app, "Contents", "Info.plist");
            if (!Files.isRegularFile(plist)) continue;
            try {
                String v = cleanVersion(run("/usr/bin/plutil", "-extract", "CFBundleShortVersionString", "raw", plist.toString()));
                if (v != null) return v;
            } catch (IOException | InterruptedException ignored) { }
        }
        return null;
    }

    /** Linux: what the package manager installed (.deb, .rpm, or the Flathub app); null when none says. */
    private static String linuxBitwigVersion() {
        String[][] asks = {
            { "dpkg-query", "-W", "-f=${Version}", "bitwig-studio" },
            { "rpm", "-q", "--qf", "%{VERSION}", "bitwig-studio" },
            { "flatpak", "info", "com.bitwig.BitwigStudio" },
        };
        for (String[] ask : asks) {
            try {
                String out = run(ask);
                if (ask[0].equals("flatpak")) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^\\s*Version:\\s*(\\S+)").matcher(out);
                    out = m.find() ? m.group(1) : "";
                }
                String v = cleanVersion(out);
                if (v != null) return v;
            } catch (IOException | InterruptedException ignored) { } // that package manager isn't there, or has no Bitwig
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
        if (LINUX) {
            try {
                new ProcessBuilder("notify-send", "--app-name=Patchbay Launcher", "Patchbay Launcher", text)
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            } catch (IOException ignored) { } // no notification daemon tool installed
            return;
        }
        try {
            String safe = text.replace("\\", "").replace("\"", "'");
            new ProcessBuilder("/usr/bin/osascript", "-e",
                "display notification \"" + safe + "\" with title \"Patchbay Launcher\"")
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException ignored) { }
    }

    // ---------- opening folders and web pages ----------

    /** Opens a folder or a web address in the system's own app; on Linux xdg-open when Java's Desktop can't. */
    static void open(String target) {
        try {
            java.net.URI uri = target.startsWith("http") ? java.net.URI.create(target) : null;
            if (Desktop.isDesktopSupported()) {
                Desktop d = Desktop.getDesktop();
                if (uri != null && d.isSupported(Desktop.Action.BROWSE)) { d.browse(uri); return; }
                if (uri == null && d.isSupported(Desktop.Action.OPEN)) { d.open(Path.of(target).toFile()); return; }
            }
        } catch (Exception tryTheSystem) { /* below */ }
        if (LINUX) {
            try { new ProcessBuilder("xdg-open", target).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start(); }
            catch (IOException ignored) { }
        }
    }

    // ---------- pinning: Dock on macOS, Start menu and desktop on Windows, applications menu on Linux ----------

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

    /** Windows: Patchbay Launcher.exe; Linux: the patchbay-launcher script. Each passes its own path when it starts the launcher. */
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
        if (LINUX) return PatchbayLauncher.t("Dodaj do menu aplikacji", "Add to the applications menu");
        return WINDOWS ? PatchbayLauncher.t("Dodaj skrót do menu Start", "Add to the Start menu") : PatchbayLauncher.t("Dodaj do Docka", "Add to Dock");
    }

    static String pinQuestion() {
        if (LINUX) return PatchbayLauncher.t("Dodać Patchbay Launcher do menu aplikacji?", "Add Patchbay Launcher to the applications menu?");
        return WINDOWS
            ? PatchbayLauncher.t("Dodać skrót do Patchbay Launcher w menu Start i na pulpicie?", "Add Patchbay Launcher to the Start menu and the desktop?")
            : PatchbayLauncher.t("Dodać Patchbay Launcher do Docka, żeby był pod jednym kliknięciem?", "Add Patchbay Launcher to the Dock, so it's one click away?");
    }

    static String pinnedMessage() {
        if (LINUX) return PatchbayLauncher.t("Patchbay Launcher jest teraz w menu aplikacji.", "Patchbay Launcher is in the applications menu now.");
        return WINDOWS ? PatchbayLauncher.t("Skrót jest w menu Start i na pulpicie.", "The shortcut is in the Start menu and on the desktop.")
            : PatchbayLauncher.t("Patchbay Launcher jest teraz w Docku.", "Patchbay Launcher is in the Dock now.");
    }

    static String pinFailedMessage() {
        if (LINUX) return PatchbayLauncher.t("Nie udało się dodać do menu aplikacji.", "Couldn't add it to the applications menu.");
        return WINDOWS ? PatchbayLauncher.t("Nie udało się utworzyć skrótu. Kliknij Patchbay Launcher.exe prawym przyciskiem i wybierz „Przypnij do menu Start”.",
                "Couldn't create the shortcut. Right-click Patchbay Launcher.exe and choose \"Pin to Start\".")
            : PatchbayLauncher.t("Nie udało się dodać do Docka. Przeciągnij Patchbay Launcher z Aplikacji na Dock.",
                "Couldn't add it to the Dock. Drag Patchbay Launcher from Applications onto the Dock instead.");
    }

    /** null when pinning can go ahead, else why not (macOS: the app must be in Applications). */
    static String pinBlocked() {
        if (!MAC) return launcherExe() == null ? "" : null;
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

    /** Linux: the launcher's entry in the applications menu (XDG desktop entry) and its icon. */
    static Path desktopFile() { return xdg("XDG_DATA_HOME", ".local/share").resolve("applications").resolve("patchbay-launcher.desktop"); }
    static Path desktopIcon() { return xdg("XDG_DATA_HOME", ".local/share").resolve("icons/hicolor/256x256/apps/patchbay-launcher.png"); }

    /** The desktop entry for the script at script; the path is quoted as the Desktop Entry spec asks. */
    static String desktopEntry(Path script, Path icon) {
        String quoted = "\"" + script.toAbsolutePath().normalize().toString().replace("%", "%%")
            .replace("\\", "\\\\").replace("\"", "\\\"").replace("`", "\\`").replace("$", "\\$") + "\"";
        return "[Desktop Entry]\nType=Application\nName=Patchbay Launcher\n"
            + "Comment=Install, update and start Patchbay for Bitwig Studio\n"
            + "Exec=" + quoted.replace("\\", "\\\\") + "\nIcon=" + icon.toAbsolutePath() + "\nTerminal=false\n"
            + "Categories=AudioVideo;Audio;\nStartupWMClass=PatchbayLauncher\n";
    }

    static boolean pinned() {
        if (WINDOWS) return Files.isRegularFile(startMenuShortcut());
        if (LINUX) return Files.isRegularFile(desktopFile());
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
        if (LINUX) {
            Path script = launcherExe();
            if (script == null) throw new IOException("patchbay-launcher");
            Files.createDirectories(desktopIcon().getParent());
            try (InputStream in = Platform.class.getResourceAsStream("/icon-256.png")) {
                if (in != null) Files.copy(in, desktopIcon(), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.createDirectories(desktopFile().getParent());
            Files.writeString(desktopFile(), desktopEntry(script, desktopIcon()));
            desktopFile().toFile().setExecutable(true, false);
            try { run("update-desktop-database", desktopFile().getParent().toString()); }
            catch (IOException | InterruptedException ignored) { } // most menus notice the new file on their own
            return;
        }
        Path app = appBundle();
        run("/usr/bin/defaults", "write", "com.apple.dock", "persistent-apps", "-array-add", dockEntry(app));
        run("/usr/bin/killall", "Dock");
    }

    /** macOS, once per launcher version: tells Finder to read the app's icon again (it keeps old icons of a replaced app). */
    static void refreshIcon(String version) {
        if (!MAC) return;
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
