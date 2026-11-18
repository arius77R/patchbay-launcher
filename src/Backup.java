import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * One file with what the user set up, to bring back later or on another computer: which mods are switched on
 * and which theme is picked (Patchbay's two settings files), the theme files in the active Patchbay's
 * components/ folder, the launcher's sources and the starred mods and devices. Mods and Patchbay themselves are not in it: the launcher
 * downloads those.
 */
final class Backup {
    private Backup() { }

    static final String MARK = "patchbay-launcher-backup.properties";
    private static final List<String> PATCHBAY_FILES = List.of("enabled-mods.properties", "selected-components.properties");
    /** The launcher's own settings that go in the mark: the starred mods and devices. */
    private static final List<String> FAVOURITES = List.of("favouriteMods", "favouriteDevices");
    private static final long BIGGEST = 5L * 1024 * 1024; // a theme file is a few KB; nothing bigger belongs here

    static String defaultName() { return "Patchbay-Launcher-settings-" + LocalDate.now() + ".zip"; }

    /** Writes the backup; returns how many files went in (without the mark). */
    static int save(Path zip, Path patchbaySettings, Path components, Path sources) throws IOException {
        int count = 0;
        Path temp = zip.resolveSibling(zip.getFileName() + ".tmp");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(temp))) {
            Properties mark = new Properties();
            mark.setProperty("format", "1");
            mark.setProperty("launcher", PatchbayLauncher.VERSION);
            mark.setProperty("saved", LocalDate.now().toString());
            for (String key : FAVOURITES) { String kept = I18n.setting(key); if (kept != null) mark.setProperty(key, kept); }
            out.putNextEntry(new ZipEntry(MARK));
            mark.store(out, "Patchbay Launcher settings backup");
            out.closeEntry();
            for (String name : PATCHBAY_FILES) count += put(out, "patchbay/" + name, patchbaySettings.resolve(name));
            count += put(out, "launcher/sources.json", sources);
            if (components != null && Files.isDirectory(components)) {
                List<Path> files;
                try (Stream<Path> walk = Files.walk(components)) { files = walk.filter(Files::isRegularFile).sorted().toList(); }
                for (Path f : files) {
                    String rel = components.relativize(f).toString().replace('\\', '/');
                    if (rel.startsWith(".") || rel.contains("/.")) continue;
                    count += put(out, "components/" + rel, f);
                }
            }
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
        Files.move(temp, zip, StandardCopyOption.REPLACE_EXISTING);
        return count;
    }

    private static int put(ZipOutputStream out, String name, Path file) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) > BIGGEST) return 0;
        out.putNextEntry(new ZipEntry(name));
        Files.copy(file, out);
        out.closeEntry();
        return 1;
    }

    /**
     * Puts a backup's files back; returns how many. components may be null (no Patchbay installed): the theme
     * files are then left out. An IOException says why a file isn't a backup.
     */
    static int restore(Path zip, Path patchbaySettings, Path components, Path sources) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) names.add(e.getName());
        }
        if (!names.contains(MARK)) throw new IOException(PatchbayLauncher.t("To nie jest kopia ustawień Patchbay Launchera.",
            "This isn't a Patchbay Launcher settings backup."));
        int count = 0;
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                String name = e.getName();
                if (name.equals(MARK)) {
                    Properties mark = new Properties();
                    mark.load(new ByteArrayInputStream(in.readNBytes(64 * 1024)));
                    for (String key : FAVOURITES) if (mark.getProperty(key) != null) I18n.remember(key, mark.getProperty(key));
                    continue;
                }
                if (e.isDirectory()) continue;
                Path target = target(name, patchbaySettings, components, sources);
                if (target == null) continue;
                Files.createDirectories(target.getParent());
                Path temp = Files.createTempFile(target.getParent(), ".restore-", ".tmp");
                try {
                    copy(in, temp);
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(temp);
                }
                count++;
            }
        }
        return count;
    }

    /** Where a backup's entry goes, or null for one that is unknown, unsafe ("..") or has no place on this computer. */
    static Path target(String name, Path patchbaySettings, Path components, Path sources) {
        if (name.contains("..") || name.contains("\\") || name.startsWith("/")) return null;
        if (name.equals("launcher/sources.json")) return sources;
        if (name.startsWith("patchbay/")) {
            String leaf = name.substring("patchbay/".length());
            return PATCHBAY_FILES.contains(leaf) ? patchbaySettings.resolve(leaf) : null;
        }
        if (name.startsWith("components/") && components != null) {
            Path target = components.resolve(name.substring("components/".length())).normalize();
            return target.startsWith(components) && !target.equals(components) ? target : null;
        }
        return null;
    }

    private static void copy(InputStream in, Path to) throws IOException {
        try (OutputStream out = Files.newOutputStream(to)) {
            byte[] buffer = new byte[8192];
            long total = 0;
            for (int n; (n = in.read(buffer)) > 0; ) {
                total += n;
                if (total > BIGGEST) throw new IOException("backup entry too large");
                out.write(buffer, 0, n);
            }
        }
    }
}
