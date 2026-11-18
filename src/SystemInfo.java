import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What a problem report needs, in one block of text: the launcher, the system, Java, Bitwig, the
 * active Patchbay with the Bitwig versions its maps cover, and the installed mods. Versions and names
 * only: no paths (they can hold the user's name) and nothing else about the user. In English, since
 * reports are read on GitHub. Each part is worked out on its own, so one that fails reads "unknown"
 * and the rest still comes through.
 */
final class SystemInfo {
    private SystemInfo() { }

    static String text() {
        StringBuilder out = new StringBuilder();
        out.append("Patchbay Launcher ").append(PatchbayLauncher.VERSION).append('\n');
        out.append("System: ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version"))
            .append(" (").append(System.getProperty("os.arch")).append(")\n");
        out.append("Java: ").append(System.getProperty("java.version")).append('\n');
        String bitwig = safe(Platform::bitwigVersion);
        out.append("Bitwig: ").append(bitwig == null ? "not found" : bitwig).append('\n');
        String patchbay = safe(PatchbayLauncher::currentVersion);
        out.append("Patchbay: ").append(patchbay == null ? "not installed" : patchbay).append('\n');
        if (patchbay != null) {
            Path root = PatchbayLauncher.folderOf(patchbay);
            List<String> maps = safe(() -> Compat.versions(root, Platform.suffix()));
            if (maps != null && !maps.isEmpty()) {
                out.append("Patchbay maps for Bitwig: ").append(String.join(", ", maps));
                if (bitwig != null) out.append(Compat.target(root, bitwig, Platform.suffix()) == null ? " (not this Bitwig)" : " (this Bitwig included)");
                out.append('\n');
            }
            Map<String, Mods.Installed> mods = safe(() -> Mods.installed(root.resolve("mods")));
            if (mods != null) {
                List<String> list = new ArrayList<>();
                for (Mods.Installed m : mods.values()) list.add(m.id() + (m.version().isEmpty() ? "" : " " + m.version()));
                out.append("Mods (").append(list.size()).append("): ").append(list.isEmpty() ? "none" : String.join(", ", list)).append('\n');
            }
        }
        out.append("Language: ").append(I18n.lang());
        return out.toString();
    }

    private interface Part<T> { T get() throws Exception; }

    private static <T> T safe(Part<T> part) {
        try {
            return part.get();
        } catch (Throwable unknown) {
            return null;
        }
    }
}
