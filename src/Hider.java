import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * "Hide the Patchbay window when Bitwig starts": the launcher closes a moment after it starts Patchbay,
 * so a small process of its own (this class, run with --hide-watch) waits for Bitwig to appear and then
 * hides the Patchbay window. Patchbay keeps running: on macOS its Dock icon brings it back, on Windows
 * and Linux the taskbar does. Patchbay itself is not changed. The watcher ends with Patchbay.
 */
final class Hider {
    private Hider() { }

    static final String SETTING = "hidePatchbay";
    /** The X11 window class Java gives the Patchbay window (its main class, dots as dashes). */
    static final String LINUX_CLASS = "dev-bitwigre-bml-BmlControlApp";

    /** On unless the user switched it off. */
    static boolean enabled() { return !"false".equals(I18n.setting(SETTING)); }

    static void setEnabled(boolean on) { I18n.remember(SETTING, Boolean.toString(on)); }

    /** Linux needs xdotool to minimise another program's window. */
    static boolean available() {
        if (!Platform.LINUX) return true;
        for (String dir : String.valueOf(System.getenv("PATH")).split(":"))
            if (!dir.isEmpty() && Files.isExecutable(Path.of(dir, "xdotool"))) return true;
        return Files.isExecutable(Path.of("/usr/bin/xdotool"));
    }

    private static Path log() { return PatchbayLauncher.data().resolve("hide-patchbay.log"); }

    /** Starts the watcher as a process of its own, on the Java the launcher runs on, so it outlives the launcher. */
    static void startWatcher() {
        try {
            String java = ProcessHandle.current().info().command().orElse(null);
            if (java == null) return;
            Path jar = Path.of(Hider.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            List<String> cmd = new ArrayList<>(List.of(java, "-Djava.awt.headless=true", "-Dapple.awt.UIElement=true"));
            for (String key : List.of("launcher.data", "launcher.os", "launcher.bmlsettings"))
                if (System.getProperty(key) != null) cmd.add("-D" + key + "=" + System.getProperty(key));
            cmd.addAll(List.of("-cp", jar.toString(), "PatchbayLauncher", "--hide-watch"));
            Files.createDirectories(log().getParent());
            new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.to(log().toFile())).start();
        } catch (IOException | URISyntaxException | RuntimeException ignored) {
            // without the watcher Patchbay simply stays visible
        }
    }

    /** The watcher: finds the Patchbay window's process, then hides it each time Bitwig starts, until Patchbay quits. */
    static void watch() throws InterruptedException {
        List<Long> pids = List.of();
        long deadline = System.currentTimeMillis() + 90_000;
        while (pids.isEmpty()) {
            if (System.currentTimeMillis() > deadline) { say("Patchbay did not start; nothing to watch."); return; }
            Thread.sleep(1500);
            pids = Platform.patchbayPids();
        }
        say("Watching Patchbay " + pids);
        boolean bitwigWas = Platform.bitwigRunning();
        while (pids.stream().anyMatch(p -> ProcessHandle.of(p).map(ProcessHandle::isAlive).orElse(false))) {
            boolean bitwig = Platform.bitwigRunning();
            if (bitwig && !bitwigWas && enabled()) {
                Thread.sleep(3500); // let Bitwig's own window come up first
                for (long pid : pids) hide(pid);
            }
            bitwigWas = bitwig;
            Thread.sleep(2000);
        }
        say("Patchbay closed.");
    }

    /** The command that hides (macOS) or minimises (Windows, Linux) the windows of one process. */
    static List<String> command(long pid) {
        if (Platform.MAC) return List.of("/usr/bin/osascript", "-e",
            "tell application \"System Events\" to set visible of (first process whose unix id is " + pid + ") to false");
        if (Platform.WINDOWS) return List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
            "Add-Type -Namespace PL -Name Win -MemberDefinition '[DllImport(\"user32.dll\")] public static extern bool ShowWindowAsync(System.IntPtr h, int c);'; "
                + "$h = (Get-Process -Id " + pid + ").MainWindowHandle; if ($h -ne 0) { [void][PL.Win]::ShowWindowAsync($h, 6) }");
        // Java doesn't put its process ID on its X11 windows, so they are found by their class: Patchbay's main class
        return List.of("xdotool", "search", "--onlyvisible", "--class", "^" + LINUX_CLASS + "$", "windowminimize", "%@");
    }

    static void hide(long pid) {
        try {
            Process p = new ProcessBuilder(command(pid)).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            int rc = p.waitFor();
            say("Hid Patchbay (" + pid + "): exit " + rc + (out.isEmpty() ? "" : ", " + out));
            if (out.contains("-1743")) say("macOS did not allow it: System Settings > Privacy & Security > Automation, allow Patchbay Launcher to control System Events.");
        } catch (IOException | InterruptedException e) {
            say("Could not hide Patchbay: " + e.getMessage());
        }
    }

    private static void say(String line) {
        System.out.println(java.time.LocalTime.now().withNano(0) + " " + line);
    }

    /** The texts for the option, worded for this system. */
    static String title() {
        return PatchbayLauncher.t("Chowaj okno Patchbay, gdy Bitwig wystartuje", "Hide the Patchbay window when Bitwig starts");
    }

    static String explanation() {
        if (Platform.MAC) return PatchbayLauncher.t(
            "Po kliknięciu Load okno Patchbay schowa się, a jego ikona zostanie w Docku. Patchbay działa dalej: kliknij ikonę, żeby przywrócić okno, np. żeby zamknąć Bitwiga przyciskiem Stop.",
            "After you click Load, the Patchbay window hides and its icon stays in the Dock. Patchbay keeps running: click the icon to bring the window back, for example to quit Bitwig with Stop.");
        return PatchbayLauncher.t(
            "Po kliknięciu Load okno Patchbay zminimalizuje się na pasek zadań. Patchbay działa dalej: kliknij go na pasku zadań, żeby przywrócić okno, np. żeby zamknąć Bitwiga przyciskiem Stop.",
            "After you click Load, the Patchbay window minimises to the taskbar. Patchbay keeps running: click it in the taskbar to bring the window back, for example to quit Bitwig with Stop.");
    }

    /** A line under the option, or null: the macOS question, or a missing xdotool on Linux. */
    static String note() {
        if (Platform.MAC) return PatchbayLauncher.t(
            "Za pierwszym razem macOS zapyta, czy Patchbay Launcher może sterować „System Events”. Kliknij OK, inaczej okno nie zniknie.",
            "The first time, macOS asks whether Patchbay Launcher may control \"System Events\". Click OK, or the window won't hide.");
        if (Platform.LINUX && !available()) return PatchbayLauncher.t(
            "Do tego potrzebny jest program xdotool (np. sudo apt install xdotool); bez niego okno nie zniknie.",
            "This needs the xdotool program (for example sudo apt install xdotool); without it the window won't hide.");
        return null;
    }
}
