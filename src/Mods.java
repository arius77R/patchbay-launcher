import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * The mods section: a catalogue built from the GitHub releases of the mod repositories, compared
 * with the mods in the active Patchbay install, and an installer that does what Patchbay's own
 * Add Mod does (check the ZIP whole, unpack to a staging folder in mods/, swap it in).
 */
final class Mods {
    private Mods() { }

    /** Repositories whose releases hold one ZIP per mod, named <mod-id>-<version>.zip. */
    static final Pattern ASSET = Pattern.compile("(.+?)-(\\d+\\.\\d+\\.\\d+(?:-[0-9A-Za-z.]+)?)\\.zip");
    static final String STAGING_PREFIX = ".patchbay-add-";
    private static final long MAX_UNPACKED = 2_000_000_000L;

    record Available(String id, String version, String url, String sha256, String repo, String page) { }
    /** What a mod's manifest says about it. */
    record Info(String name, String author, String description, List<Requirement> requires, String builtFor) { }

    /** Mod ID to its display name, from the table in a release's notes ("| Name | `file.zip` | notes |"). */
    static final Map<String, String> NAMES = new java.util.concurrent.ConcurrentHashMap<>();
    static final Pattern NOTES_ROW = Pattern.compile("^\\|\\s*([^|]+?)\\s*\\|\\s*`([^`|]+\\.zip)`\\s*\\|.*$");

    static void readNames(String releaseNotes) {
        if (releaseNotes == null) return;
        for (String line : releaseNotes.split("\n")) {
            Matcher row = NOTES_ROW.matcher(line.strip());
            if (!row.matches()) continue;
            Matcher file = ASSET.matcher(row.group(2));
            if (file.matches()) NAMES.put(file.group(1), row.group(1));
        }
    }

    static Info infoOf(Map<String, Object> manifest) {
        if (manifest == null || !(manifest.get("mod") instanceof Map)) return null;
        Map<String, Object> mod = Json.object(manifest.get("mod"));
        String name = Json.text(mod, "name"), author = Json.text(mod, "author"), description = Json.text(mod, "description");
        String builtFor = manifest.get("built_against") instanceof Map ? Json.text(Json.object(manifest.get("built_against")), "bitwig_version") : null;
        return new Info(name, author == null ? "" : author, description == null ? "" : description, requirements(manifest), builtFor);
    }

    /**
     * A mod's manifest from its source repository (the release list has file names only), kept in
     * the launcher's cache per version. Null when it can't be had; the list works without it.
     */
    static Info fetchInfo(Available a) {
        try {
            Path cached = PatchbayLauncher.data().resolve("cache").resolve("modinfo").resolve(a.id() + "-" + a.version() + ".json");
            if (!Files.isRegularFile(cached)) {
                String offline = System.getProperty("launcher.fixtures");
                if (offline != null) {
                    Path local = Path.of(offline, "modinfo", a.id() + ".json");
                    return Files.isRegularFile(local) ? infoOf(readManifest(local)) : null;
                }
                PatchbayLauncher.download("https://raw.githubusercontent.com/" + a.repo() + "/HEAD/" + a.id() + "/mod.json", cached, null);
            }
            return infoOf(readManifest(cached));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
    record Installed(String id, String name, String version) { }
    record Checked(Path zip, String id, String name, String version, Map<String, Object> manifest) { }

    // ---------- JSON (objects as Map, arrays as List, numbers as Double) ----------

    static final class Json {
        private final String s;
        private int i;
        private Json(String s) { this.s = s; }

        static Object parse(String text) {
            Json j = new Json(text);
            Object v = j.value();
            j.space();
            if (j.i != j.s.length()) throw new IllegalArgumentException("unexpected text after JSON at " + j.i);
            return v;
        }
        @SuppressWarnings("unchecked")
        static Map<String, Object> object(Object o) {
            if (o instanceof Map<?, ?> m) return (Map<String, Object>) m;
            throw new IllegalArgumentException("not a JSON object");
        }
        @SuppressWarnings("unchecked")
        static List<Object> array(Object o) {
            if (o instanceof List<?> l) return (List<Object>) l;
            return List.of();
        }
        static String text(Map<String, Object> m, String key) {
            return m.get(key) instanceof String v ? v : null;
        }

        /** JSON text for maps, lists, strings, numbers, booleans and null; whole numbers are written without a fraction. */
        static String write(Object value) {
            StringBuilder b = new StringBuilder();
            write(b, value, "");
            return b.toString();
        }
        private static void write(StringBuilder b, Object v, String indent) {
            if (v instanceof Map<?, ?> m) {
                if (m.isEmpty()) { b.append("{}"); return; }
                b.append("{\n");
                int n = 0;
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    b.append(indent).append("  ");
                    quote(b, String.valueOf(e.getKey()));
                    b.append(": ");
                    write(b, e.getValue(), indent + "  ");
                    b.append(++n < m.size() ? ",\n" : "\n");
                }
                b.append(indent).append('}');
            } else if (v instanceof List<?> l) {
                if (l.isEmpty()) { b.append("[]"); return; }
                b.append("[\n");
                for (int k = 0; k < l.size(); k++) {
                    b.append(indent).append("  ");
                    write(b, l.get(k), indent + "  ");
                    b.append(k + 1 < l.size() ? ",\n" : "\n");
                }
                b.append(indent).append(']');
            } else if (v instanceof String str) {
                quote(b, str);
            } else if (v instanceof Double d && d == Math.rint(d) && Math.abs(d) < 1e15) {
                b.append(d.longValue());
            } else {
                b.append(v);
            }
        }
        private static void quote(StringBuilder b, String str) {
            b.append('"');
            for (int k = 0; k < str.length(); k++) {
                char c = str.charAt(k);
                switch (c) {
                    case '"' -> b.append("\\\"");
                    case '\\' -> b.append("\\\\");
                    case '\n' -> b.append("\\n");
                    case '\r' -> b.append("\\r");
                    case '\t' -> b.append("\\t");
                    default -> { if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c); }
                }
            }
            b.append('"');
        }

        private void space() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        private char peek() {
            if (i >= s.length()) throw new IllegalArgumentException("unexpected end of JSON");
            return s.charAt(i);
        }
        private void expect(char c) {
            if (peek() != c) throw new IllegalArgumentException("expected " + c + " at " + i);
            i++;
        }
        private Object value() {
            space();
            char c = peek();
            if (c == '{') {
                i++;
                Map<String, Object> m = new LinkedHashMap<>();
                space();
                if (peek() == '}') { i++; return m; }
                while (true) {
                    space();
                    String k = string();
                    space();
                    expect(':');
                    m.put(k, value());
                    space();
                    if (peek() == ',') { i++; continue; }
                    expect('}');
                    return m;
                }
            }
            if (c == '[') {
                i++;
                List<Object> l = new ArrayList<>();
                space();
                if (peek() == ']') { i++; return l; }
                while (true) {
                    l.add(value());
                    space();
                    if (peek() == ',') { i++; continue; }
                    expect(']');
                    return l;
                }
            }
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (start == i) throw new IllegalArgumentException("unexpected character at " + i);
            return Double.valueOf(s.substring(start, i));
        }
        private String string() {
            expect('"');
            StringBuilder b = new StringBuilder();
            while (true) {
                char c = peek();
                i++;
                if (c == '"') return b.toString();
                if (c != '\\') { b.append(c); continue; }
                char e = peek();
                i++;
                switch (e) {
                    case 'n' -> b.append('\n');
                    case 't' -> b.append('\t');
                    case 'r' -> b.append('\r');
                    case 'b' -> b.append('\b');
                    case 'f' -> b.append('\f');
                    case 'u' -> { b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                    default -> b.append(e);
                }
            }
        }
    }

    // ---------- catalogue ----------

    /** The newest version of every mod in one repository's release list (GitHub API JSON). */
    static Map<String, Available> parseCatalog(String json, String repo) {
        return parseCatalog(json, repo, null);
    }

    /** As above, keeping only the release files that match the source's pattern (null: all). */
    static Map<String, Available> parseCatalog(String json, String repo, Pattern files) {
        Map<String, Available> out = new TreeMap<>();
        for (Object r : Json.array(Json.parse(json))) {
            Map<String, Object> release = Json.object(r);
            if (Boolean.TRUE.equals(release.get("draft"))) continue;
            String page = Json.text(release, "html_url");
            readNames(Json.text(release, "body"));
            for (Object a : Json.array(release.get("assets"))) {
                Map<String, Object> asset = Json.object(a);
                String name = Json.text(asset, "name"), url = Json.text(asset, "browser_download_url");
                if (name == null || url == null || !url.startsWith("https://")) continue;
                if (files != null && !files.matcher(name).matches()) continue;
                Matcher m = ASSET.matcher(name);
                if (!m.matches()) continue;
                String digest = Json.text(asset, "digest");
                String sha = digest != null && digest.startsWith("sha256:") ? digest.substring(7).toLowerCase(Locale.ROOT) : null;
                Available found = new Available(m.group(1), m.group(2), url, sha, repo, page);
                merge(out, found);
            }
        }
        return out;
    }

    private static void merge(Map<String, Available> into, Available a) {
        Available have = into.get(a.id());
        if (have == null || PatchbayLauncher.compareVersions(a.version(), have.version()) > 0) into.put(a.id(), a);
    }

    /** Releases fetched per page, and how many pages at most (older mods stay in the catalogue). */
    static final int PAGE = 100, MAX_PAGES = 5;

    /** A repository's releases, page by page, until a page comes back less than full. */
    static List<String> releasePages(String repo, String offline) throws IOException {
        if (offline != null) return List.of(Files.readString(Path.of(offline, repo.replace('/', '_') + ".json")));
        List<String> pages = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            String json;
            try {
                json = PatchbayLauncher.httpText("https://api.github.com/repos/" + repo + "/releases?per_page=" + PAGE + "&page=" + page);
            } catch (IOException later) {
                if (page == 1) throw later;
                break; // the newest releases are in hand; the older pages can wait for the next refresh
            }
            pages.add(json);
            if (!(Json.parse(json) instanceof List<?> list) || list.size() < PAGE) break;
        }
        return pages;
    }

    static Map<String, Available> fetchCatalog() throws IOException {
        Map<String, Available> all = new TreeMap<>();
        IOException failure = null;
        String offline = System.getProperty("launcher.fixtures"); // offline test: <repo name>.json files in this folder
        for (Sources.Source src : Sources.enabled(Sources.MODS)) {
            String repo = src.repo();
            try {
                for (String json : releasePages(repo, offline))
                    for (Available a : parseCatalog(json, repo, Sources.glob(src.filter())).values()) merge(all, a);
            } catch (IOException | RuntimeException ex) {
                failure = ex instanceof IOException io ? io : new IOException(repo + ": " + ex.getMessage());
            }
        }
        if (all.isEmpty() && failure != null) throw failure;
        return all;
    }

    // ---------- installed mods ----------

    static Map<String, Object> readManifest(Path modJson) {
        try {
            return Json.object(Json.parse(Files.readString(modJson)));
        } catch (IOException | RuntimeException bad) {
            return null;
        }
    }

    static Map<String, Installed> installed(Path modsDir) {
        Map<String, Installed> out = new TreeMap<>();
        if (modsDir == null || !Files.isDirectory(modsDir)) return out;
        try (Stream<Path> s = Files.list(modsDir)) {
            for (Path dir : s.filter(Files::isDirectory).toList()) {
                if (dir.getFileName().toString().startsWith(".")) continue;
                Map<String, Object> manifest = readManifest(dir.resolve("mod.json"));
                if (manifest == null || !(manifest.get("mod") instanceof Map)) continue;
                Map<String, Object> mod = Json.object(manifest.get("mod"));
                String id = Json.text(mod, "id");
                if (id == null) continue;
                String name = Json.text(mod, "name"), version = Json.text(mod, "version");
                out.put(id, new Installed(id, name == null ? id : name, version == null ? "" : version));
            }
        } catch (IOException ignored) { }
        return out;
    }

    // ---------- install ----------

    static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[1 << 16];
            for (int n; (n = in.read(buf)) >= 0; ) d.update(buf, 0, n);
            return HexFormat.of().formatHex(d.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    /** Checks a mod ZIP whole before anything is written; the exception's message is the reason it is refused. */
    static Checked check(Path zip, Path modsDir) throws IOException {
        String top = null;
        Set<String> seen = new HashSet<>();
        Map<String, Object> manifest = null;
        try (ZipFile z = new ZipFile(zip.toFile(), StandardCharsets.UTF_8)) {
            for (Enumeration<? extends ZipEntry> all = z.entries(); all.hasMoreElements(); ) {
                ZipEntry entry = all.nextElement();
                String name = entry.getName();
                if (name.isEmpty() || name.startsWith("/") || name.indexOf('\\') >= 0 || name.indexOf(':') >= 0)
                    throw new IOException("unsafe path in the ZIP (" + name + ")");
                String[] parts = name.split("/");
                for (String p : parts)
                    if (p.isEmpty() || p.equals(".") || p.equals("..")) throw new IOException("unsafe path in the ZIP (" + name + ")");
                if (top == null) top = parts[0];
                else if (!top.equals(parts[0])) throw new IOException("the ZIP has more than one top folder (" + top + ", " + parts[0] + ")");
                if (parts.length == 1 && !entry.isDirectory()) throw new IOException("the ZIP has a file outside the mod's folder (" + name + ")");
                if (!seen.add(name.toLowerCase(Locale.ROOT))) throw new IOException("the ZIP has two entries named " + name);
                if (name.equals(top + "/mod.json")) {
                    try (InputStream in = z.getInputStream(entry)) {
                        manifest = Json.object(Json.parse(new String(in.readNBytes(4_000_000), StandardCharsets.UTF_8)));
                    } catch (RuntimeException bad) {
                        throw new IOException("mod.json can't be read (" + bad.getMessage() + ")");
                    }
                }
            }
        } catch (ZipException notZip) {
            throw new IOException("the file isn't a readable ZIP");
        }
        if (top == null) throw new IOException("the ZIP is empty");
        if (top.startsWith(".")) throw new IOException("the mod's folder name starts with a dot");
        if (manifest == null) throw new IOException("there's no mod.json in " + top + "/");
        if (!(manifest.get("mod") instanceof Map)) throw new IOException("mod.json has no mod section");
        Map<String, Object> mod = Json.object(manifest.get("mod"));
        String id = Json.text(mod, "id"), name = Json.text(mod, "name"), version = Json.text(mod, "version");
        if (id == null) throw new IOException("mod.json has no mod.id");
        if (!id.equals(top)) throw new IOException("the folder " + top + " doesn't match the mod ID " + id);
        for (Installed other : installed(modsDir).values()) {
            if (other.id().equals(id) && !Files.isDirectory(modsDir.resolve(id)))
                throw new IOException(id + " is already installed in a folder with another name; remove that folder first");
        }
        return new Checked(zip, id, name == null ? id : name, version == null ? "" : version, manifest);
    }

    /** Unpacks a checked ZIP into a staging folder in mods/ and swaps it in; the old copy returns if the swap fails. */
    static void install(Checked c, Path modsDir) throws IOException {
        Files.createDirectories(modsDir);
        Path staging = Files.createTempDirectory(modsDir, STAGING_PREFIX);
        try {
            long total = 0;
            try (ZipFile z = new ZipFile(c.zip().toFile(), StandardCharsets.UTF_8)) {
                byte[] buf = new byte[1 << 16];
                for (Enumeration<? extends ZipEntry> all = z.entries(); all.hasMoreElements(); ) {
                    ZipEntry entry = all.nextElement();
                    Path out = staging.resolve(entry.getName()).normalize();
                    if (!out.startsWith(staging.resolve(c.id()))) throw new IOException("unsafe path in the ZIP (" + entry.getName() + ")");
                    if (entry.isDirectory()) { Files.createDirectories(out); continue; }
                    Files.createDirectories(out.getParent());
                    try (InputStream in = z.getInputStream(entry); OutputStream o = Files.newOutputStream(out, StandardOpenOption.CREATE_NEW)) {
                        for (int n; (n = in.read(buf)) != -1; ) {
                            total += n;
                            if (total > MAX_UNPACKED) throw new IOException("the ZIP unpacks to more than 2 GB");
                            o.write(buf, 0, n);
                        }
                    }
                }
            }
            Path fresh = staging.resolve(c.id()), target = modsDir.resolve(c.id()), old = staging.resolve("previous");
            if (!Files.isRegularFile(fresh.resolve("mod.json"))) throw new IOException("there's no mod.json in " + c.id() + "/");
            boolean replacing = Files.exists(target, LinkOption.NOFOLLOW_LINKS);
            if (replacing) Files.move(target, old);
            try {
                Files.move(fresh, target);
            } catch (IOException ex) {
                if (replacing) Files.move(old, target);
                throw new IOException(c.name() + " can't be moved into mods (" + ex.getMessage() + ")");
            }
        } finally {
            PatchbayLauncher.deleteTree(staging);
        }
    }

    record Requirement(String id, String minVersion) { }

    static List<Requirement> requirements(Map<String, Object> manifest) {
        List<Requirement> out = new ArrayList<>();
        for (Object r : Json.array(manifest.get("requires"))) {
            if (!(r instanceof Map)) continue;
            Map<String, Object> req = Json.object(r);
            String id = Json.text(req, "id");
            if (id != null) out.add(new Requirement(id, Json.text(req, "min_version")));
        }
        return out;
    }

    /** Where a mod ZIP comes from: the network in the app, a local folder in the offline test. */
    interface Fetcher { void fetch(Available a, Path target) throws IOException; }

    /**
     * Installs the picked mods and the mods they need that the catalogue has. Returns notes for
     * the user: needed mods that are neither installed nor in the catalogue.
     */
    static List<String> installAll(List<Available> picks, Map<String, Available> catalog, Path modsDir,
                                   Path downloads, Fetcher fetcher, Consumer<String> status) throws IOException {
        Deque<Available> queue = new ArrayDeque<>(picks);
        Set<String> done = new HashSet<>();
        List<String> notes = new ArrayList<>();
        Files.createDirectories(downloads);
        while (!queue.isEmpty()) {
            Available a = queue.poll();
            if (!done.add(a.id())) continue;
            status.accept(a.id() + " " + a.version());
            Path zip = downloads.resolve(a.id() + "-" + a.version() + ".zip");
            try {
                fetcher.fetch(a, zip);
                if (a.sha256() != null) {
                    String actual = sha256(zip);
                    if (!actual.equals(a.sha256()))
                        throw new IOException(a.id() + ": " + PatchbayLauncher.t("suma SHA-256 się nie zgadza, plik odrzucony.", "the SHA-256 doesn't match, the file was refused."));
                }
                Checked c = check(zip, modsDir);
                if (!c.id().equals(a.id())) throw new IOException(a.id() + ": the ZIP holds another mod (" + c.id() + ")");
                install(c, modsDir);
                Map<String, Installed> have = installed(modsDir);
                for (Requirement r : requirements(c.manifest())) {
                    Installed got = have.get(r.id());
                    boolean enough = got != null && (r.minVersion() == null || got.version().isEmpty()
                        || PatchbayLauncher.compareVersions(got.version(), r.minVersion()) >= 0);
                    if (enough || done.contains(r.id())) continue;
                    Available dep = catalog.get(r.id());
                    if (dep != null) queue.add(dep);
                    else notes.add(I18n.needs(c.name(), r.id(), r.minVersion(), got == null ? null : got.version()));
                }
            } finally {
                Files.deleteIfExists(zip);
            }
        }
        return notes;
    }

    // ---------- the section ----------

    static final class Panel extends JPanel {
        private record Row(String id, String name, String installed, Available available, int rank) { }

        private final List<Row> rows = new ArrayList<>();
        /** IDs of the ticked rows; only rows that can be installed or updated can be ticked. */
        private final Set<String> checked = new HashSet<>();
        private final AbstractTableModel model = new AbstractTableModel() {
            private final String[] columns = {
                "", "Mod", PatchbayLauncher.t("Zainstalowana", "Installed"),
                PatchbayLauncher.t("Dostępna", "Available"), PatchbayLauncher.t("Stan", "Status") };
            @Override public int getRowCount() { return rows.size(); }
            @Override public int getColumnCount() { return columns.length; }
            @Override public String getColumnName(int c) { return columns[c]; }
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? Boolean.class : String.class; }
            @Override public boolean isCellEditable(int r, int c) { return c == 0 && !busy && rows.get(r).rank() != 1; }
            @Override public void setValueAt(Object v, int r, int c) {
                if (c != 0) return;
                if (Boolean.TRUE.equals(v)) checked.add(rows.get(r).id()); else checked.remove(rows.get(r).id());
                fireTableCellUpdated(r, c);
                refreshButtons();
            }
            @Override public Object getValueAt(int r, int c) {
                Row row = rows.get(r);
                return switch (c) {
                    case 0 -> checked.contains(row.id());
                    case 1 -> nameOf(row);
                    case 2 -> row.installed() == null ? "–" : row.installed();
                    case 3 -> row.available() == null ? "" : row.available().version();
                    default -> switch (row.rank()) {
                        case 0 -> "update|" + PatchbayLauncher.t("aktualizacja", "update");
                        case 1 -> "installed|" + PatchbayLauncher.t("zainstalowany", "installed");
                        default -> "new|" + PatchbayLauncher.t("nowy", "new");
                    };
                };
            }
        };
        private final JTable table = new JTable(model);
        private final JLabel subtitle = Theme.label(" ", Theme.text(14f), Theme.MUTED);
        private final JPanel details = new Theme.Card(new BorderLayout(), Theme.FRAME);
        private final JButton installButton = Theme.button(" ", "primary");
        private final JButton checkAllButton = Theme.button(" ", "secondary");
        private final JButton updateAllButton = Theme.button(" ", "secondary");
        private final JButton refreshButton = Theme.button(PatchbayLauncher.t("Odśwież", "Refresh"), "secondary");
        private final JLabel status = Theme.label(" ", Theme.text(14f), Theme.MUTED);
        private final JProgressBar bar = new JProgressBar();
        private final Consumer<Integer> updatesFound;
        /** Manifest details by mod ID, filled in the background after the list is shown. */
        private final Map<String, Info> infos = new java.util.concurrent.ConcurrentHashMap<>();
        /** Name and version of the mods on disk, frameworks included, for the "Needs" list. */
        private final Map<String, Installed> onDisk = new HashMap<>();
        private Map<String, Available> catalog = Map.of();
        private boolean busy, notified, reloadAgain;
        private int infoRun;

        private String nameOf(Row row) {
            Info info = infos.get(row.id());
            if (info != null && info.name() != null) return info.name();
            String listed = NAMES.get(row.id());
            return listed != null ? listed : row.name();
        }

        /** updatesFound gets the number of installed mods with a newer version each time the list is rebuilt. */
        Panel(Consumer<Integer> updatesFound) {
            super(new BorderLayout());
            this.updatesFound = updatesFound;
            Theme.styleTable(table);
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.getColumnModel().getColumn(0).setMinWidth(40);
            table.getColumnModel().getColumn(0).setMaxWidth(40);
            table.getColumnModel().getColumn(1).setPreferredWidth(320);
            table.getColumnModel().getColumn(1).setCellRenderer(Theme.nameCells());
            table.getColumnModel().getColumn(2).setCellRenderer(Theme.monoCells(Theme.MUTED));
            table.getColumnModel().getColumn(3).setCellRenderer(Theme.monoCells(Theme.SOFT));
            table.getColumnModel().getColumn(4).setCellRenderer(Theme.pillCells());
            for (int c = 2; c <= 3; c++) {
                table.getColumnModel().getColumn(c).setMinWidth(96);
                table.getColumnModel().getColumn(c).setMaxWidth(104);
            }
            table.getColumnModel().getColumn(4).setMinWidth(104);
            table.getColumnModel().getColumn(4).setMaxWidth(112);
            table.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) showSelected(); });

            details.setPreferredSize(new Dimension(284, 10));
            details.setBorder(new EmptyBorder(18, 18, 18, 18));
            JPanel body = new JPanel(new BorderLayout(16, 0));
            body.setOpaque(false);
            body.add(Theme.framed(table), BorderLayout.CENTER);
            body.add(details, BorderLayout.EAST);

            bar.setPreferredSize(new Dimension(10, 4));
            bar.setVisible(false);
            JPanel left = new JPanel(new BorderLayout(0, 6));
            left.setOpaque(false);
            left.add(status, BorderLayout.CENTER);
            left.add(bar, BorderLayout.SOUTH);
            add(Theme.page(PatchbayLauncher.t("Mody", "Mods"), subtitle, Theme.row(8, checkAllButton, refreshButton), body,
                left, Theme.row(8, updateAllButton, installButton)), BorderLayout.CENTER);

            installButton.addActionListener(e -> installRows(checkedRows()));
            checkAllButton.addActionListener(e -> checkAll());
            updateAllButton.addActionListener(e -> installRows(rows.stream().filter(r -> r.rank() == 0).toList()));
            refreshButton.addActionListener(e -> reload());
            showSelected();
            refreshButtons();
        }

        private Path modsDir() {
            String v = PatchbayLauncher.currentVersion();
            return v == null ? null : PatchbayLauncher.folderOf(v).resolve("mods");
        }

        private List<Row> checkedRows() {
            return rows.stream().filter(r -> checked.contains(r.id())).toList();
        }

        private List<Row> checkable() {
            return rows.stream().filter(r -> r.rank() != 1).toList();
        }

        /** Ticks every row that can be installed or updated; unticks them all when they are all ticked. */
        private void checkAll() {
            List<Row> all = checkable();
            boolean allTicked = !all.isEmpty() && all.stream().allMatch(r -> checked.contains(r.id()));
            checked.clear();
            if (!allTicked) for (Row r : all) checked.add(r.id());
            model.fireTableDataChanged();
            refreshButtons();
        }

        private static JLabel left(JLabel l) {
            l.setAlignmentX(LEFT_ALIGNMENT);
            return l;
        }

        /** The side card: what the selected mod is, who wrote it, what it needs. */
        private void showSelected() {
            details.removeAll();
            JPanel col = new Theme.Column();
            int r = table.getSelectedRow();
            if (r < 0 || r >= rows.size()) {
                col.add(left(Theme.wrapped(PatchbayLauncher.t("Kliknij mod na liście, żeby zobaczyć opis i wymagania.", "Click a mod in the list to see what it does and needs."), 190, Theme.MUTED, 13f)));
                details.add(col, BorderLayout.NORTH);
                details.revalidate();
                details.repaint();
                return;
            }
            Row row = rows.get(r);
            Info info = infos.get(row.id());
            String name = nameOf(row);
            int colon = name.indexOf(':');
            if (colon > 0 && colon < 30) {
                col.add(left(Theme.label(name.substring(0, colon + 1), Theme.text(13f), Theme.MUTED)));
                col.add(Box.createVerticalStrut(4));
                name = name.substring(colon + 1).strip();
            }
            col.add(left(Theme.wrapped(name, 190, Theme.TEXT, 20f)));
            ((JLabel) col.getComponent(col.getComponentCount() - 1)).setFont(Theme.bold(20f));
            col.add(Box.createVerticalStrut(12));
            if (info != null && !info.description().isEmpty()) {
                col.add(left(Theme.wrapped(info.description(), 190, Theme.SOFT, 14f)));
                col.add(Box.createVerticalStrut(12));
            }
            List<String[]> facts = new ArrayList<>();
            if (info != null && !info.author().isEmpty()) facts.add(new String[] { PatchbayLauncher.t("Autor", "Author"), info.author() });
            if (info != null && info.builtFor() != null && !info.builtFor().isBlank())
                facts.add(new String[] { PatchbayLauncher.t("Zbudowany dla", "Built for"), "Bitwig " + info.builtFor() });
            if (row.installed() != null) facts.add(new String[] { PatchbayLauncher.t("Zainstalowana", "Installed"), row.installed(), "mono" });
            if (row.available() != null) {
                facts.add(new String[] { PatchbayLauncher.t("Dostępna", "Available"), row.available().version(), "mono" });
                facts.add(new String[] { PatchbayLauncher.t("Źródło", "Source"), row.available().repo() });
            }
            JPanel f = Theme.facts(facts.toArray(new String[0][]));
            f.setAlignmentX(LEFT_ALIGNMENT);
            f.setMaximumSize(new Dimension(Integer.MAX_VALUE, f.getPreferredSize().height));
            col.add(f);
            if (info != null && !info.requires().isEmpty()) {
                col.add(Box.createVerticalStrut(12));
                col.add(left(Theme.label(PatchbayLauncher.t("Wymaga", "Needs"), Theme.bold(13f), Theme.TEXT)));
                col.add(Box.createVerticalStrut(6));
                for (Requirement q : info.requires()) {
                    Installed got = onDisk.get(q.id());
                    boolean ok = got != null && (q.minVersion() == null || got.version().isEmpty()
                        || PatchbayLauncher.compareVersions(got.version(), q.minVersion()) >= 0);
                    String label = NAMES.getOrDefault(q.id(), got != null ? got.name() : q.id());
                    JLabel state = ok ? Theme.label("✓", Theme.bold(13f), Theme.ACCENT)
                        : catalog.containsKey(q.id()) ? Theme.label(PatchbayLauncher.t("dociągnie się", "will be added"), Theme.text(12f), Theme.MUTED)
                        : Theme.label(PatchbayLauncher.t("brak", "missing"), Theme.text(12f), Theme.FAULT_TEXT);
                    if (!ok && got != null) state = Theme.label(PatchbayLauncher.t("za stara", "too old"), Theme.text(12f), Theme.FAULT_TEXT);
                    JPanel line = new JPanel(new BorderLayout(8, 0));
                    line.setOpaque(false);
                    JLabel n = new JLabel(Theme.twoTone(label));
                    n.setFont(Theme.text(13f));
                    n.setForeground(Theme.TEXT);
                    line.add(n, BorderLayout.CENTER);
                    line.add(state, BorderLayout.EAST);
                    line.setAlignmentX(LEFT_ALIGNMENT);
                    line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
                    col.add(line);
                    col.add(Box.createVerticalStrut(4));
                }
            }
            JScrollPane scroll = new JScrollPane(col);
            scroll.setBorder(BorderFactory.createEmptyBorder());
            scroll.setOpaque(false);
            scroll.getViewport().setOpaque(false);
            scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.setViewportView(col);
            details.add(scroll, BorderLayout.CENTER);
            if (row.available() != null && row.available().page() != null) {
                JButton page = Theme.button(PatchbayLauncher.t("Strona wydania", "Release page"), "secondary");
                String url = row.available().page();
                page.addActionListener(e -> {
                    try { Desktop.getDesktop().browse(java.net.URI.create(url)); } catch (Exception ignored) { }
                });
                JPanel south = new JPanel(new BorderLayout());
                south.setOpaque(false);
                south.setBorder(new EmptyBorder(12, 0, 0, 0));
                south.add(page, BorderLayout.CENTER);
                details.add(south, BorderLayout.SOUTH);
            }
            details.revalidate();
            details.repaint();
        }

        /** Fills in names, descriptions and needs: from the mod on disk when installed, else from its repository. */
        private void loadInfos() {
            int run = ++infoRun;
            List<Available> todo = new ArrayList<>(catalog.values());
            Path mods = modsDir();
            new Thread(() -> {
                for (Available a : todo) {
                    if (run != infoRun) return;
                    if (infos.containsKey(a.id())) continue;
                    Info info = mods == null ? null : infoOf(readManifest(mods.resolve(a.id()).resolve("mod.json")));
                    if (info == null) info = fetchInfo(a);
                    if (info == null) continue;
                    infos.put(a.id(), info);
                    SwingUtilities.invokeLater(() -> {
                        for (int r = 0; r < rows.size(); r++) {
                            if (!rows.get(r).id().equals(a.id())) continue;
                            model.fireTableRowsUpdated(r, r);
                            if (table.getSelectedRow() == r) showSelected();
                        }
                    });
                }
            }, "mods-info").start();
        }

        /** Fetches the catalogue again and rebuilds the list. */
        void reload() {
            if (busy) { reloadAgain = true; return; }
            reloadAgain = false;
            if (modsDir() == null) {
                rows.clear();
                model.fireTableDataChanged();
                setBusy(false, PatchbayLauncher.t("Najpierw zainstaluj Patchbay w sekcji Patchbay.", "Install Patchbay in the Patchbay section first."));
                return;
            }
            setBusy(true, PatchbayLauncher.t("Pobieranie listy modów…", "Loading the mod list…"));
            new Thread(() -> {
                try {
                    Map<String, Available> found = fetchCatalog();
                    SwingUtilities.invokeLater(() -> { catalog = found; rebuild(); setBusy(false, ""); loadInfos(); reloadIfAsked(); });
                } catch (IOException ex) {
                    SwingUtilities.invokeLater(() -> { rebuild(); setBusy(false, ex.getMessage()); reloadIfAsked(); });
                }
            }, "mods-catalog").start();
        }

        /** A reload asked for while one was running (sources changed, say) runs once that one ends. */
        private void reloadIfAsked() {
            if (reloadAgain) reload();
        }

        /**
         * Patchbay was installed or updated: the list is rebuilt against the new mods folder, and the
         * catalogue is fetched if there is none yet (it can't be fetched before Patchbay is installed).
         */
        void patchbayChanged() {
            if (catalog.isEmpty()) reload();
            else rebuild();
        }

        /** Rebuilds the list from the catalogue in hand and the mods on disk (after a Patchbay update, say). */
        void rebuild() {
            Map<String, Installed> have = installed(modsDir());
            onDisk.clear();
            onDisk.putAll(have);
            String keep = table.getSelectedRow() >= 0 && table.getSelectedRow() < rows.size() ? rows.get(table.getSelectedRow()).id() : null;
            rows.clear();
            for (Available a : catalog.values()) {
                Installed i = have.get(a.id());
                int rank = i == null ? 2 : PatchbayLauncher.compareVersions(a.version(), i.version()) > 0 ? 0 : 1;
                rows.add(new Row(a.id(), i == null ? a.id() : i.name(), i == null ? null : i.version(), a, rank));
            }
            rows.sort(Comparator.comparingInt(Row::rank).thenComparing(Row::id));
            Set<String> canTick = new HashSet<>();
            for (Row r : checkable()) canTick.add(r.id());
            checked.retainAll(canTick);
            model.fireTableDataChanged();
            int updates = (int) rows.stream().filter(r -> r.rank() == 0).count();
            long installedCount = rows.stream().filter(r -> r.rank() != 2).count();
            subtitle.setText(catalog.isEmpty() ? " " : PatchbayLauncher.t("W katalogu: ", "In the catalogue: ") + catalog.size()
                + " · " + PatchbayLauncher.t("zainstalowane: ", "installed: ") + installedCount
                + " · " + PatchbayLauncher.t("aktualizacje: ", "updates: ") + updates);
            String select = System.getProperty("launcher.select"); // offline test: show this mod's details
            if (select == null) select = keep;
            int pick = rows.isEmpty() ? -1 : 0;
            for (int r = 0; select != null && r < rows.size(); r++) if (rows.get(r).id().equals(select)) pick = r;
            if (pick >= 0) table.setRowSelectionInterval(pick, pick);
            updateAllButton.setText(PatchbayLauncher.t("Aktualizuj wszystkie (", "Update all (") + updates + ")");
            updatesFound.accept(updates);
            if (updates > 0 && !notified) {
                notified = true;
                PatchbayLauncher.notifyUser(PatchbayLauncher.t("Aktualizacje modów: ", "Mod updates: ") + updates);
            }
            showSelected();
            refreshButtons();
        }

        private void refreshButtons() {
            boolean ready = !busy && modsDir() != null;
            List<Row> all = checkable();
            int ticked = checkedRows().size();
            boolean allTicked = !all.isEmpty() && ticked == all.size();
            checkAllButton.setText(allTicked ? PatchbayLauncher.t("Odznacz wszystkie", "Untick all") : PatchbayLauncher.t("Zaznacz wszystkie", "Tick all"));
            checkAllButton.setEnabled(ready && !all.isEmpty());
            installButton.setText(PatchbayLauncher.t("Zainstaluj zaznaczone (", "Install ticked (") + ticked + ")");
            installButton.setEnabled(ready && ticked > 0);
            updateAllButton.setEnabled(ready && rows.stream().anyMatch(r -> r.rank() == 0));
            refreshButton.setEnabled(!busy);
        }

        private void setBusy(boolean b, String text) {
            busy = b;
            status.setText(text == null || text.isEmpty() ? " " : text);
            bar.setIndeterminate(b);
            bar.setVisible(b);
            refreshButtons();
        }

        private void installRows(List<Row> chosen) {
            List<Available> picks = chosen.stream().filter(r -> r.rank() != 1 && r.available() != null).map(Row::available).toList();
            Path mods = modsDir();
            if (picks.isEmpty() || mods == null) return;
            if (PatchbayLauncher.bitwigRunning() || PatchbayLauncher.patchbayRunning()) {
                JOptionPane.showMessageDialog(this,
                    PatchbayLauncher.t("Zamknij Bitwig Studio i okno Patchbay, a potem spróbuj ponownie.",
                        "Quit Bitwig Studio and the Patchbay window, then try again."),
                    "Patchbay Launcher", JOptionPane.WARNING_MESSAGE);
                return;
            }
            setBusy(true, "");
            new Thread(() -> {
                try {
                    List<String> notes = installAll(picks, catalog, mods, PatchbayLauncher.data().resolve("downloads"),
                        (a, target) -> PatchbayLauncher.download(a.url(), target, null),
                        s -> SwingUtilities.invokeLater(() -> status.setText(PatchbayLauncher.t("Instalowanie: ", "Installing: ") + s)));
                    SwingUtilities.invokeLater(() -> {
                        checked.clear();
                        rebuild();
                        setBusy(false, PatchbayLauncher.t("Gotowe. Włącz mody w oknie Patchbay i kliknij Load.", "Done. Tick the mods in the Patchbay window and click Load."));
                        if (!notes.isEmpty())
                            JOptionPane.showMessageDialog(this, String.join("\n", notes), "Patchbay Launcher", JOptionPane.WARNING_MESSAGE);
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        rebuild();
                        setBusy(false, "");
                        JOptionPane.showMessageDialog(this, String.valueOf(ex.getMessage()), "Patchbay Launcher", JOptionPane.ERROR_MESSAGE);
                    });
                }
            }, "mods-install").start();
        }
    }
}
