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

    /** published: when this version's file was put in the release (GitHub's ISO time), or "" when unknown. */
    record Available(String id, String version, String url, String sha256, String repo, String page, String published) { }

    /** True when every word typed in Search is somewhere in the given fields (name, id or type, author; case ignored); an empty search finds all. */
    static boolean matches(String typed, String... fields) {
        String where = String.join(" ", fields).toLowerCase(Locale.ROOT);
        for (String word : typed.toLowerCase(Locale.ROOT).trim().split("\\s+"))
            if (!word.isEmpty() && !where.contains(word)) return false;
        return true;
    }

    /** A mod's category: what stands before the colon in its name ("Arranger: Add Automation Points"), or "" without one. */
    static String category(String name) {
        int colon = name == null ? -1 : name.indexOf(": ");
        return colon > 0 ? name.substring(0, colon).strip() : "";
    }
    /** A mod's name without its category ("Add Automation Points"). */
    static String withoutCategory(String name) {
        String category = category(name);
        return category.isEmpty() ? name : name.substring(name.indexOf(": ") + 2).strip();
    }
    /** What a mod's manifest says about it. */
    record Info(String name, String author, String description, List<Requirement> requires, String builtFor, String builtPlatform, String builtArch) { }

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
        Map<String, Object> built = manifest.get("built_against") instanceof Map ? Json.object(manifest.get("built_against")) : Map.of();
        return new Info(name, author == null ? "" : author, description == null ? "" : description, requirements(manifest),
            Json.text(built, "bitwig_version"), Json.text(built, "platform"), Json.text(built, "architecture"));
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
            String page = Json.text(release, "html_url"), released = Json.text(release, "published_at");
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
                String at = Json.text(asset, "created_at");
                if (at == null) at = released;
                Available found = new Available(m.group(1), m.group(2), url, sha, repo, page, at == null ? "" : at);
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
        if (offline != null) {
            if (Files.exists(Path.of(offline, repo.replace('/', '_') + ".gone"))) throw new PatchbayLauncher.Gone(repo); // offline test: a deleted repository
            return List.of(Files.readString(Path.of(offline, repo.replace('/', '_') + ".json")));
        }
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

    /**
     * The mods a repository still holds: the ids with an <id>/mod.json on its default branch (PUBLISHING
     * rule 3). Null when the repository's files can't be had, or it has no such folder at all: then every mod in its releases counts.
     */
    static Set<String> modFolders(String repo, String offline) {
        try {
            String json;
            if (offline != null) {
                Path fx = Path.of(offline, repo.replace('/', '_') + ".tree.json");
                if (!Files.isRegularFile(fx)) return null;
                json = Files.readString(fx);
            } else {
                json = PatchbayLauncher.httpText("https://api.github.com/repos/" + repo + "/git/trees/HEAD?recursive=1");
            }
            Map<String, Object> root = Json.object(Json.parse(json));
            if (Boolean.TRUE.equals(root.get("truncated"))) return null; // too big to know for sure
            Set<String> out = new HashSet<>();
            for (Object o : Json.array(root.get("tree"))) {
                String path = Json.text(Json.object(o), "path");
                if (path != null && path.endsWith("/mod.json") && path.indexOf('/') == path.length() - "/mod.json".length())
                    out.add(path.substring(0, path.indexOf('/')));
            }
            return out.isEmpty() ? null : out; // keeps its mods' files elsewhere (src/, say): its folders say nothing about what was removed
        } catch (IOException | RuntimeException unknown) {
            return null;
        }
    }

    /**
     * Every mod in the enabled sources' releases; removed gets the ones whose folder is gone from their
     * repository, withdrawn the ones their author withdrew (withdrawn.json) with the reason.
     */
    static Map<String, Available> fetchCatalog(Set<String> removed, Map<String, String> withdrawn) throws IOException {
        Map<String, Available> all = new TreeMap<>();
        IOException failure = null;
        String offline = System.getProperty("launcher.fixtures"); // offline test: <repo name>.json files in this folder
        for (Sources.Source src : Sources.enabled(Sources.MODS)) {
            String repo = src.repo();
            try {
                Set<String> inRepo = modFolders(repo, offline);
                withdrawn.putAll(Sources.withdrawn(repo, "mods"));
                for (String json : releasePages(repo, offline))
                    for (Available a : parseCatalog(json, repo, Sources.glob(src.filter())).values()) {
                        if (inRepo != null && !inRepo.contains(a.id())) removed.add(a.id());
                        merge(all, a);
                    }
                Sources.answered(Sources.MODS, repo, null);
            } catch (IOException | RuntimeException ex) {
                failure = ex instanceof IOException io ? io : new IOException(repo + ": " + ex.getMessage());
                Sources.answered(Sources.MODS, repo, failure);
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

    // ---------- switching off and removing ----------

    /** Patchbay's list of the mods ticked in its window: mod.<id>=true. */
    static Path enabledFile() { return Devices.patchbaySettings().resolve("enabled-mods.properties"); }

    static boolean enabled(String id) {
        return "true".equals(Themes.load(enabledFile()).getProperty("mod." + id));
    }

    /** Ticks or unticks a mod the way Patchbay's own window does; the files stay. Patchbay must be closed. */
    static void setEnabled(String id, boolean on) throws IOException {
        Properties p = Themes.load(enabledFile());
        p.setProperty("mod." + id, Boolean.toString(on));
        Themes.store(enabledFile(), p, "BML enabled mods");
    }

    /** Deletes a mod's folder from mods/ and its tick from Patchbay's list. Patchbay must be closed. */
    static void uninstall(String id, Path modsDir) throws IOException {
        Path folder = modsDir.resolve(id).normalize();
        if (!folder.startsWith(modsDir.normalize()) || folder.equals(modsDir.normalize())) throw new IOException("unsafe mod ID: " + id);
        PatchbayLauncher.deleteTree(folder);
        Properties p = Themes.load(enabledFile());
        if (p.remove("mod." + id) != null) Themes.store(enabledFile(), p, "BML enabled mods");
    }

    /** The switched-on mods on disk that need this one: they stop working without it. */
    static List<String> neededBy(String id, Path modsDir) {
        List<String> out = new ArrayList<>();
        Properties on = Themes.load(enabledFile());
        for (Installed other : installed(modsDir).values()) {
            if (other.id().equals(id) || !"true".equals(on.getProperty("mod." + other.id()))) continue;
            Map<String, Object> manifest = readManifest(modsDir.resolve(other.id()).resolve("mod.json"));
            if (manifest != null && requirements(manifest).stream().anyMatch(q -> q.id().equals(id))) out.add(other.name());
        }
        return out;
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

        /** Every catalogue row; rows holds the ones the Show filter lets through, in display order. */
        private final List<Row> all = new ArrayList<>();
        private final List<Row> rows = new ArrayList<>();
        /** IDs of the mods marked with a star. */
        private final Set<String> favourites = SidePanel.favourites("favouriteMods");
        private final SidePanel side = new SidePanel("modsSide", key -> refilter());
        /** Show: 0 all, 1 works on my Bitwig, 2 installed, 3 updates, 4 not available, 5 gone from their repository. */
        private final JComboBox<String> showBox = new JComboBox<>();
        /** Sort: 0 by status (updates, installed, new), 1 by category then name, 2 newest version first. */
        private final JComboBox<String> sortBox = new JComboBox<>();
        private final Theme.HintField searchField = new Theme.HintField(PatchbayLauncher.t("Szukaj", "Search"));
        /** "Update available" above the list; a click shows only the mods with updates. */
        private final Theme.UpdateBar updateBar = new Theme.UpdateBar(() -> {
            if (showBox.getSelectedIndex() != SHOW_UPDATES) beforeUpdates = showBox.getSelectedIndex();
            showBox.setSelectedIndex(SHOW_UPDATES);
        });
        private static final int SHOW_UPDATES = 3, SHOW_GONE = 5, SHOW_OUTSIDE = 6;
        /** The Show choice to go back to when Updates has nothing left; All unless the update bar was clicked from another. */
        private int beforeUpdates;
        /** The Bitwig version when it can't be read from the system: chosen from the versions Patchbay has maps for. */
        private final JComboBox<String> bitwigBox = new JComboBox<>();
        private final JLabel bitwigLabel = Theme.label(PatchbayLauncher.t("Twój Bitwig:", "Your Bitwig:"), Theme.text(13f), Theme.MUTED);
        private String detectedBitwig;
        private boolean detecting = true, filling;
        private Compat.Target target;
        private final javax.swing.Timer refilterSoon = new javax.swing.Timer(250, e -> refilter());
        /** IDs of the ticked rows: ones to install or update, and installed ones to switch off or uninstall. */
        private final Set<String> checked = new HashSet<>();
        private final AbstractTableModel model = new AbstractTableModel() {
            private final String[] columns = {
                "", "", PatchbayLauncher.t("Kategoria", "Category"), "Mod", PatchbayLauncher.t("Zainstalowana", "Installed"),
                PatchbayLauncher.t("Dostępna", "Available"), PatchbayLauncher.t("Stan", "Status") };
            @Override public int getRowCount() { return rows.size(); }
            @Override public int getColumnCount() { return columns.length; }
            @Override public String getColumnName(int c) { return columns[c]; }
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? Boolean.class : String.class; }
            @Override public boolean isCellEditable(int r, int c) { return c == 0 && !busy && tickable(rows.get(r)); }
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
                    case 1 -> favourites.contains(row.id());
                    case 2 -> (unavailable(row) ? "~" : "") + Categories.of(nameOf(row));
                    case 3 -> (unavailable(row) ? "~" : "") + withoutCategory(nameOf(row));
                    case 4 -> row.installed() == null ? "–" : row.installed();
                    case 5 -> row.available() == null ? "" : row.available().version();
                    default -> withdrawn.containsKey(row.id()) && row.installed() != null ? "withdrawn|" + PatchbayLauncher.t("wycofany", "withdrawn")
                        : removed.contains(row.id()) && row.installed() != null ? "removed|" + PatchbayLauncher.t("usunięty", "removed")
                        : row.available() == null ? "outside|" + PatchbayLauncher.t("spoza źródeł", "not in sources")
                        : unavailable(row) ? "off|" + PatchbayLauncher.t("niezgodny", "incompatible") : switch (row.rank()) {
                        case 0 -> "update|" + PatchbayLauncher.t("aktualizacja", "update");
                        case 1 -> switchedOn.contains(row.id()) ? "installed|" + PatchbayLauncher.t("włączony", "on")
                            : "off|" + PatchbayLauncher.t("wyłączony", "off");
                        default -> "new|" + PatchbayLauncher.t("nowy", "new");
                    };
                };
            }
        };
        /** IDs of the mods ticked in Patchbay's window, read again with the mods on disk. */
        private final Set<String> switchedOn = new HashSet<>();
        private final JTable table = new JTable(model);
        private final JLabel subtitle = Theme.label(" ", Theme.text(14f), Theme.MUTED);
        private final JPanel details = new Theme.Card(new BorderLayout(), Theme.FRAME);
        private final JButton installButton = Theme.button(" ", "primary");
        private final JButton switchButton = Theme.button(PatchbayLauncher.t("Wyłącz", "Switch off"), "secondary");
        private final JButton removeButton = Theme.button(PatchbayLauncher.t("Odinstaluj", "Uninstall"), "danger");
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
        /**
         * Mods whose folder is gone from their repository, and mods their author withdrew (id to reason): not
         * offered any more, even if an old release still has the ZIP; one that is installed stays in the list,
         * marked, with no update.
         */
        private Set<String> removed = Set.of();
        private Map<String, String> withdrawn = Map.of();
        private boolean busy, notified, reloadAgain;
        private int infoRun;

        private String nameOf(Row row) {
            Info info = infos.get(row.id());
            if (info != null && info.name() != null) return info.name();
            String listed = NAMES.get(row.id());
            return listed != null ? listed : row.name();
        }

        /** The author from the mod's manifest, or "" until it has been read. */
        private String authorOf(Row row) {
            Info info = infos.get(row.id());
            return info == null || info.author() == null ? "" : info.author();
        }

        /** updatesFound gets the number of installed mods with a newer version each time the list is rebuilt. */
        Panel(Consumer<Integer> updatesFound) {
            super(new BorderLayout());
            this.updatesFound = updatesFound;
            Theme.styleTable(table);
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.getColumnModel().getColumn(0).setMinWidth(40);
            table.getColumnModel().getColumn(0).setMaxWidth(40);
            table.getColumnModel().getColumn(1).setMinWidth(30);
            table.getColumnModel().getColumn(1).setMaxWidth(30);
            table.getColumnModel().getColumn(1).setCellRenderer(Theme.starCells());
            Theme.typeColumn(table.getColumnModel().getColumn(2), Categories::colour);
            table.getColumnModel().getColumn(3).setPreferredWidth(260);
            table.getColumnModel().getColumn(3).setCellRenderer(Theme.nameCells());
            table.getColumnModel().getColumn(4).setCellRenderer(Theme.monoCells(Theme.MUTED));
            table.getColumnModel().getColumn(5).setCellRenderer(Theme.monoCells(Theme.SOFT));
            table.getColumnModel().getColumn(6).setCellRenderer(Theme.pillCells());
            for (int c = 4; c <= 5; c++) {
                table.getColumnModel().getColumn(c).setMinWidth(112);
                table.getColumnModel().getColumn(c).setMaxWidth(112);
            }
            // version columns wide enough for "6.0.0-dev.2.linux" only when the mod names keep 260 px
            table.addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override public void componentResized(java.awt.event.ComponentEvent e) {
                    javax.swing.table.TableColumnModel m = table.getColumnModel();
                    int others = m.getColumn(0).getWidth() + m.getColumn(1).getWidth() + m.getColumn(2).getWidth() + m.getColumn(6).getWidth();
                    int width = table.getWidth() - others - 2 * 140 >= 260 ? 140 : 112;
                    for (int c = 4; c <= 5; c++) {
                        javax.swing.table.TableColumn column = table.getColumnModel().getColumn(c);
                        if (column.getMaxWidth() == width) continue;
                        column.setMinWidth(Math.min(width, column.getMinWidth()));
                        column.setMaxWidth(width);
                        column.setMinWidth(width);
                    }
                }
            });
            table.getColumnModel().getColumn(6).setMinWidth(118);
            table.getColumnModel().getColumn(6).setMaxWidth(150);
            SidePanel.starClicks(table, 1, r -> rows.get(r).id(), favourites, "favouriteMods", this::refilter);
            table.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) showSelected(); });

            details.setBorder(new EmptyBorder(18, 18, 18, 18));

            bar.setPreferredSize(new Dimension(10, 4));
            bar.setVisible(false);
            JPanel left = new JPanel(new BorderLayout(0, 6));
            left.setOpaque(false);
            left.add(status, BorderLayout.CENTER);
            left.add(bar, BorderLayout.SOUTH);
            refilterSoon.setRepeats(false);
            for (String o : new String[] { PatchbayLauncher.t("Wszystkie", "All"), PatchbayLauncher.t("Zgodne", "Compatible"),
                    PatchbayLauncher.t("Zainstalowane", "Installed"), PatchbayLauncher.t("Aktualizacje", "Updates"),
                    PatchbayLauncher.t("Niezgodne", "Incompatible"), PatchbayLauncher.t("Usunięte z repo", "Removed from repo"),
                    PatchbayLauncher.t("Spoza źródeł", "Not in sources") })
                showBox.addItem(o);
            try { showBox.setSelectedIndex(Integer.parseInt(String.valueOf(I18n.setting("modsShow")))); } catch (RuntimeException none) { showBox.setSelectedIndex(1); } // first time: only the mods that can run
            showBox.addActionListener(e -> { if (!filling) { I18n.remember("modsShow", String.valueOf(showBox.getSelectedIndex())); refilter(); } });
            showBox.setPreferredSize(new Dimension(140, 32));
            for (String o : new String[] { PatchbayLauncher.t("Stan", "Status"), PatchbayLauncher.t("Kategoria", "Category"), PatchbayLauncher.t("Najnowsze", "Newest") })
                sortBox.addItem(o);
            try { sortBox.setSelectedIndex(Integer.parseInt(String.valueOf(I18n.setting("modsSort")))); } catch (RuntimeException none) { sortBox.setSelectedIndex(0); }
            sortBox.addActionListener(e -> { I18n.remember("modsSort", String.valueOf(sortBox.getSelectedIndex())); refilter(); });
            sortBox.setPreferredSize(new Dimension(120, 32));
            bitwigBox.setPreferredSize(new Dimension(120, 32));
            bitwigBox.getAccessibleContext().setAccessibleName(PatchbayLauncher.t("Twój Bitwig", "Your Bitwig"));
            bitwigBox.addActionListener(e -> {
                if (filling || bitwigBox.getSelectedIndex() < 0) return;
                I18n.remember("modsBitwig", bitwigBox.getSelectedIndex() == 0 ? "" : String.valueOf(bitwigBox.getSelectedItem()));
                rebuild();
            });
            bitwigLabel.setVisible(false);
            bitwigBox.setVisible(false);
            showBox.getAccessibleContext().setAccessibleName(PatchbayLauncher.t("Pokaż", "Show"));
            showBox.setToolTipText(PatchbayLauncher.t("Pokaż", "Show"));
            sortBox.getAccessibleContext().setAccessibleName(PatchbayLauncher.t("Sortuj", "Sort"));
            sortBox.setToolTipText(PatchbayLauncher.t("Sortuj", "Sort"));
            searchField.setPreferredSize(new Dimension(140, 32));
            searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { refilter(); }
                @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { refilter(); }
                @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { refilter(); }
            });
            JPanel tools = Theme.row(8, showBox, sortBox, searchField);
            JComponent body = Theme.split(tools, checkAllButton, SidePanel.beside(side, Theme.framed(table)), Theme.row(8, bitwigLabel, bitwigBox), refreshButton, details,
                left, Theme.row(8, switchButton, removeButton), Theme.row(8, updateAllButton, installButton));
            add(Theme.listPage(PatchbayLauncher.t("Mody", "Mods"), subtitle, updateBar, body, null, null), BorderLayout.CENTER);

            installButton.addActionListener(e -> installRows(checkedRows().stream().filter(this::installable).toList()));
            switchButton.addActionListener(e -> {
                List<Row> mine = checkedRows().stream().filter(r -> r.installed() != null).toList();
                switchMods(mine, mine.stream().noneMatch(r -> switchedOn.contains(r.id())));
            });
            removeButton.addActionListener(e -> uninstallMods(checkedRows().stream().filter(r -> r.installed() != null).toList()));
            checkAllButton.addActionListener(e -> checkAll());
            updateAllButton.addActionListener(e -> installRows(all.stream().filter(r -> r.rank() == 0 && !unavailable(r)).toList()));
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

        /**
         * Rows Tick all ticks: the ones that can be installed or updated; when the list shown has none (Not in sources,
         * Removed from repo, Installed with nothing to update), every installed one, to switch off or uninstall them together.
         */
        private List<Row> checkable() {
            List<Row> install = rows.stream().filter(this::installable).toList();
            return install.isEmpty() ? rows.stream().filter(this::tickable).toList() : install;
        }

        private boolean installable(Row r) { return r.rank() != 1 && r.available() != null && !unavailable(r); }

        /** A row can be ticked to install or update it, or, when installed, to switch it off or uninstall it. */
        private boolean tickable(Row r) { return installable(r) || r.installed() != null; }

        // ---------- compatibility with the user's Bitwig ----------

        /** The Bitwig the list is checked against: read from the system, else the one chosen in the list. */
        private String bitwig() {
            if (detectedBitwig != null) return detectedBitwig;
            Object chosen = I18n.setting("modsBitwig");
            return chosen == null || String.valueOf(chosen).isBlank() ? null : String.valueOf(chosen);
        }

        private Compat.Verdict verdict(Row row) {
            Info info = infos.get(row.id());
            String version = bitwig();
            if (version != null && target == null) return Compat.check(null, version, Platform.suffix(), null, null, null);
            if (info == null) return new Compat.Verdict(Compat.State.UNKNOWN, "");
            return Compat.check(target, version, Platform.suffix(), info.builtFor(), info.builtPlatform(), info.builtArch());
        }

        /** Gone from its repository: removed, or withdrawn by its author. */
        private boolean gone(Row row) {
            return removed.contains(row.id()) || withdrawn.containsKey(row.id());
        }

        /**
         * Once per mod: installed and switched-on mods their author withdrew are offered to be switched off
         * (with the reason); installed mods removed from their repository get one notice.
         */
        private void askAboutGone() {
            Set<String> asked = new HashSet<>(List.of(String.valueOf(I18n.setting("modsGoneSeen")).split(",")));
            List<Row> pulled = all.stream().filter(r -> r.installed() != null && withdrawn.containsKey(r.id()) && switchedOn.contains(r.id()) && !asked.contains(r.id())).toList();
            List<Row> fresh = all.stream().filter(r -> r.installed() != null && removed.contains(r.id()) && !withdrawn.containsKey(r.id()) && !asked.contains(r.id())).toList();
            if (pulled.isEmpty() && fresh.isEmpty()) return;
            for (Row r : pulled) asked.add(r.id());
            for (Row r : fresh) asked.add(r.id());
            asked.remove("null");
            I18n.remember("modsGoneSeen", String.join(",", asked));
            if (!fresh.isEmpty()) {
                String text = PatchbayLauncher.t("Usunięte z repozytorium (działają dalej, bez aktualizacji): ", "Removed from their repository (still work, no updates): ") + named(fresh);
                status.setText(text);
                PatchbayLauncher.notifyUser(text);
            }
            if (pulled.isEmpty()) return;
            StringBuilder why = new StringBuilder();
            for (Row r : pulled) why.append("\n• ").append(withoutCategory(nameOf(r))).append(withdrawn.get(r.id()).isEmpty() ? "" : ": " + withdrawn.get(r.id()));
            Object[] choice = { PatchbayLauncher.t("Wyłącz", "Switch off"), PatchbayLauncher.t("Zostaw", "Keep") };
            int pick = JOptionPane.showOptionDialog(this, Theme.message(PatchbayLauncher.t("Autor wycofał z powodu problemów:", "The author withdrew because of problems:") + why
                    + "\n\n" + PatchbayLauncher.t("Wyłączyć teraz? Pliki zostaną na dysku, możesz je potem włączyć albo odinstalować.",
                        "Switch off now? The files stay on disk; you can switch them on or uninstall them later.")),
                "Patchbay Launcher", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, choice, choice[0]);
            if (pick == 0) switchMods(pulled, false);
        }

        private boolean unavailable(Row row) {
            return verdict(row).state() == Compat.State.UNAVAILABLE;
        }

        /** Ticks every row checkable() gives; unticks them all when they are all ticked. */
        private void checkAll() {
            List<Row> tickable = checkable();
            boolean allTicked = !tickable.isEmpty() && tickable.stream().allMatch(r -> checked.contains(r.id()));
            checked.clear();
            if (!allTicked) for (Row r : tickable) checked.add(r.id());
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
            String name = withoutCategory(nameOf(row));
            String part = Categories.of(nameOf(row));
            JLabel heading = Theme.label(part, Theme.text(13f), Categories.colour(part));
            heading.setIcon(Categories.icon(Categories.find(part).icon(), Categories.colour(part), 16));
            heading.setIconTextGap(6);
            col.add(left(heading));
            col.add(Box.createVerticalStrut(4));
            col.add(left(Theme.wrapped(name, 190, Theme.TEXT, 20f)));
            ((JLabel) col.getComponent(col.getComponentCount() - 1)).setFont(Theme.bold(20f));
            col.add(Box.createVerticalStrut(12));
            if (row.installed() != null && row.available() == null) {
                col.add(Theme.outsideNote(166));
                col.add(Box.createVerticalStrut(12));
            } else if (row.installed() != null && gone(row)) {
                col.add(Theme.goneNote(withdrawn.containsKey(row.id()), withdrawn.get(row.id()), PatchbayLauncher.t("Ten mod", "This mod"), 166));
                col.add(Box.createVerticalStrut(12));
            }
            Compat.Verdict v = verdict(row);
            if (v.state() != Compat.State.UNKNOWN) {
                boolean ok = v.state() != Compat.State.UNAVAILABLE;
                JPanel box = new Theme.Card(new BorderLayout(0, 6), Theme.WELL);
                box.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(ok ? Theme.LINE : Theme.FAULT_TEXT, 1, true), new EmptyBorder(10, 10, 10, 10)));
                String verdictText = ok ? PatchbayLauncher.t("Zgodny z Twoim Bitwigiem", "Compatible with your Bitwig")
                    : PatchbayLauncher.t("Niezgodny z Twoim Bitwigiem", "Not compatible with your Bitwig");
                JLabel head = Theme.label(ok ? "<html><font color='" + Theme.hex(Theme.ACCENT) + "'>✓</font>&nbsp; " + verdictText + "</html>" : "×  " + verdictText,
                    Theme.bold(13f), ok ? Theme.TEXT : Theme.FAULT_TEXT);
                box.add(head, BorderLayout.NORTH);
                String why = v.state() == Compat.State.NATIVE ? PatchbayLauncher.t("Zbudowany dokładnie dla Twojego Bitwiga.", "Built for exactly your Bitwig.") : v.reason();
                box.add(Theme.wrapped(why, 166, Theme.SOFT, 13f), BorderLayout.CENTER);
                box.setAlignmentX(LEFT_ALIGNMENT);
                box.setMaximumSize(new Dimension(Integer.MAX_VALUE, box.getPreferredSize().height));
                col.add(box);
                col.add(Box.createVerticalStrut(12));
            }
            if (info != null && !info.description().isEmpty()) {
                col.add(left(Theme.wrapped(info.description(), 180, Theme.SOFT, 14f)));
                col.add(Box.createVerticalStrut(12));
            }
            List<String[]> facts = new ArrayList<>();
            if (info != null && !info.author().isEmpty()) facts.add(new String[] { PatchbayLauncher.t("Autor", "Author"), info.author() });
            if (info != null && info.builtFor() != null && !info.builtFor().isBlank())
                facts.add(new String[] { PatchbayLauncher.t("Zbudowany dla", "Built for"), "Bitwig " + info.builtFor()
                    + (info.builtPlatform() == null ? "" : " · " + Compat.platformName(info.builtPlatform())) });
            if (bitwig() != null) facts.add(new String[] { PatchbayLauncher.t("Twój Bitwig", "Your Bitwig"), bitwig() + " · " + Compat.platformName(Platform.suffix()) });
            if (row.installed() != null) facts.add(new String[] { PatchbayLauncher.t("Zainstalowana", "Installed"), row.installed(), "mono" });
            if (row.available() != null) {
                facts.add(new String[] { PatchbayLauncher.t("Dostępna", "Available"), row.available().version(), "mono" });
                if (row.available().published().length() >= 10)
                    facts.add(new String[] { PatchbayLauncher.t("Wydano", "Released"), row.available().published().substring(0, 10), "mono" });
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
            JPanel south = new JPanel(new GridLayout(0, 1, 0, 8));
            south.setOpaque(false);
            south.setBorder(new EmptyBorder(12, 0, 0, 0));
            if (v.state() == Compat.State.UNAVAILABLE && row.rank() != 1 && row.available() != null) {
                JButton anyway = Theme.button(PatchbayLauncher.t("Zainstaluj mimo to", "Install anyway"), "secondary");
                anyway.setEnabled(!busy && modsDir() != null);
                anyway.addActionListener(e -> {
                    int answer = JOptionPane.showConfirmDialog(this, PatchbayLauncher.t(
                            "Ten mod nie uruchomi się na Twoim Bitwigu, a Patchbay pokaże go jako niedostępny. Zainstalować mimo to?",
                            "This mod won't run on your Bitwig, and Patchbay will show it as not available. Install it anyway?"),
                        "Patchbay Launcher", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (answer == JOptionPane.OK_OPTION) installRows(List.of(row));
                });
                south.add(anyway);
            }
            if (row.installed() != null && modsDir() != null) {
                boolean on = switchedOn.contains(row.id());
                JButton toggle = Theme.button(on ? PatchbayLauncher.t("Wyłącz", "Switch off") : PatchbayLauncher.t("Włącz", "Switch on"), "secondary");
                toggle.setToolTipText(on ? PatchbayLauncher.t("Patchbay nie załaduje moda; pliki zostają na dysku.", "Patchbay won't load the mod; its files stay on disk.")
                    : PatchbayLauncher.t("Zaznacza mod w Patchbay, jak w jego oknie.", "Ticks the mod in Patchbay, as in its window."));
                toggle.setEnabled(!busy);
                toggle.addActionListener(e -> switchMods(List.of(row), !on));
                JButton remove = Theme.button(PatchbayLauncher.t("Odinstaluj", "Uninstall"), "danger");
                remove.setEnabled(!busy);
                remove.addActionListener(e -> uninstallMods(List.of(row)));
                south.add(toggle);
                south.add(remove);
            }
            if (row.available() != null && row.available().page() != null) {
                JButton page = Theme.button(PatchbayLauncher.t("Strona wydania", "Release page"), "secondary");
                String url = row.available().page();
                page.addActionListener(e -> Platform.open(url));
                south.add(page);
            }
            if (south.getComponentCount() > 0) details.add(south, BorderLayout.SOUTH);
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
                        refilterSoon.restart();
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
                    String version = Compat.norm(Platform.bitwigVersion());
                    Set<String> gone = new HashSet<>();
                    Map<String, String> pulled = new HashMap<>();
                    Map<String, Available> found = fetchCatalog(gone, pulled);
                    SwingUtilities.invokeLater(() -> { detectedBitwig = version; detecting = false; catalog = found; removed = gone; withdrawn = pulled; rebuild(); setBusy(false, ""); loadInfos(); reloadIfAsked(); SwingUtilities.invokeLater(this::askAboutGone); });
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
            switchedOn.clear();
            for (String id : have.keySet()) if (enabled(id)) switchedOn.add(id);
            all.clear();
            for (Available a : catalog.values()) {
                Installed i = have.get(a.id());
                boolean gone = removed.contains(a.id()) || withdrawn.containsKey(a.id());
                if (gone && i == null) continue;
                int rank = i == null ? 2 : !gone && PatchbayLauncher.compareVersions(a.version(), i.version()) > 0 ? 0 : 1;
                all.add(new Row(a.id(), i == null ? a.id() : i.name(), i == null ? null : i.version(), a, rank));
            }
            // installed mods no enabled source offers (added by hand in Patchbay, or their repository is gone): kept so they can be switched off or uninstalled
            if (!catalog.isEmpty())
                for (Installed i : have.values()) if (!catalog.containsKey(i.id())) all.add(new Row(i.id(), i.name(), i.version(), null, 1));
            String v = PatchbayLauncher.currentVersion();
            Path root = v == null ? null : PatchbayLauncher.folderOf(v);
            target = Compat.target(root, bitwig(), Platform.suffix());
            fillBitwigBox(root);
            refilter();
        }

        /** Shown only when the system doesn't say which Bitwig is installed: the versions Patchbay has maps for. */
        private void fillBitwigBox(Path root) {
            boolean ask = !detecting && detectedBitwig == null && root != null;
            filling = true;
            bitwigBox.removeAllItems();
            if (ask) {
                bitwigBox.addItem(PatchbayLauncher.t("nie wiem", "don't know"));
                for (String version : Compat.versions(root, Platform.suffix())) bitwigBox.addItem(version);
                String chosen = bitwig();
                bitwigBox.setSelectedIndex(0);
                for (int i = 1; chosen != null && i < bitwigBox.getItemCount(); i++) if (chosen.equals(bitwigBox.getItemAt(i))) bitwigBox.setSelectedIndex(i);
            }
            filling = false;
            bitwigLabel.setVisible(ask);
            bitwigBox.setVisible(ask);
        }

        /** The list's order for a Sort choice; mods that can't run on this Bitwig always come last. */
        private Comparator<Row> order(int sort) {
            Comparator<Row> usable = Comparator.comparing(this::unavailable);
            Comparator<Row> byName = Comparator.comparing(this::nameOf, String.CASE_INSENSITIVE_ORDER);
            return switch (sort) {
                // a mod without a category goes after the named ones
                case 1 -> usable.thenComparingInt((Row r) -> Categories.ALL.indexOf(Categories.find(Categories.of(nameOf(r))))).thenComparing(byName);
                case 2 -> usable.thenComparing((Row r) -> r.available() == null ? "" : r.available().published(), Comparator.reverseOrder()).thenComparing(byName);
                default -> usable.thenComparingInt(Row::rank).thenComparing(Row::id);
            };
        }

        /** The rows the Show filter lets through, mods that can run first, then updates, installed, new; and the counts. */
        private void refilter() {
            String keep = table.getSelectedRow() >= 0 && table.getSelectedRow() < rows.size() ? rows.get(table.getSelectedRow()).id() : null;
            int show = showBox.getSelectedIndex();
            rows.clear();
            List<Row> shown = new ArrayList<>();
            for (Row r : all) {
                boolean off = unavailable(r);
                boolean pass = switch (show) {
                    case 1 -> !off;
                    case 2 -> r.rank() != 2;
                    case 3 -> r.rank() == 0;
                    case 4 -> off;
                    case SHOW_GONE -> gone(r);
                    case SHOW_OUTSIDE -> r.available() == null;
                    default -> true;
                };
                if (pass && matches(searchField.getText(), nameOf(r), r.id(), authorOf(r))) shown.add(r);
            }
            showSide(shown);
            String part = side.selected();
            for (Row r : shown)
                if (part.equals(SidePanel.ALL) || part.equals(SidePanel.FAVOURITES) && favourites.contains(r.id())
                    || part.equals(SidePanel.categoryKey(Categories.of(nameOf(r))))) rows.add(r);
            rows.sort(order(sortBox.getSelectedIndex()));
            Set<String> canTick = new HashSet<>();
            for (Row r : rows) if (tickable(r)) canTick.add(r.id());
            checked.retainAll(canTick);
            model.fireTableDataChanged();

            int updates = (int) all.stream().filter(r -> r.rank() == 0 && !unavailable(r)).count();
            long installedCount = all.stream().filter(r -> r.rank() != 2).count();
            long off = all.stream().filter(this::unavailable).count();
            String mine = bitwig() == null ? null : "Bitwig " + bitwig() + " (" + Compat.platformName(Platform.suffix()) + ")";
            String head = catalog.isEmpty() ? " "
                : (mine != null ? mine + " · "
                    + PatchbayLauncher.t("zgodne: {n} z {all}", "compatible: {n} of {all}")
                        .replace("{n}", String.valueOf(all.size() - off)).replace("{all}", String.valueOf(all.size()))
                    : PatchbayLauncher.t("W katalogu: ", "In the catalogue: ") + all.size())
                + " · " + PatchbayLauncher.t("zainstalowane: ", "installed: ") + installedCount
                + " · " + PatchbayLauncher.t("aktualizacje: ", "updates: ") + updates;
            subtitle.setText(head);
            if (!busy && bitwig() != null && target == null && !catalog.isEmpty())
                status.setText(Compat.check(null, bitwig(), Platform.suffix(), null, null, null).reason());

            String select = System.getProperty("launcher.select"); // offline test: show this mod's details
            if (select == null) select = keep;
            int pick = rows.isEmpty() ? -1 : 0;
            for (int r = 0; select != null && r < rows.size(); r++) if (rows.get(r).id().equals(select)) pick = r;
            if (pick >= 0) { table.setRowSelectionInterval(pick, pick); table.scrollRectToVisible(table.getCellRect(pick, 0, true)); }
            updateAllButton.setText(PatchbayLauncher.t("Aktualizuj wszystkie (", "Update all (") + updates + ")");
            List<Row> waiting = all.stream().filter(r -> r.rank() == 0 && !unavailable(r)).toList();
            updateBar.show(updates, waiting.size() == 1
                ? nameOf(waiting.get(0)) + "  " + waiting.get(0).installed() + " → " + waiting.get(0).available().version()
                : String.join(", ", waiting.stream().map(this::nameOf).toList()));
            updatesFound.accept(updates);
            if (updates == 0 && !busy && !catalog.isEmpty() && showBox.getSelectedIndex() == SHOW_UPDATES) showBox.setSelectedIndex(beforeUpdates);
            if (updates > 0 && !notified) {
                notified = true;
                PatchbayLauncher.notifyUser(PatchbayLauncher.t("Aktualizacje modów: ", "Mod updates: ") + updates);
            }
            showSelected();
            refreshButtons();
        }

        /** The side panel's counts for the rows the Show filter and Search let through. */
        private void showSide(List<Row> shown) {
            Map<String, Integer> count = new HashMap<>();
            for (Row r : shown) count.merge(Categories.of(nameOf(r)), 1, Integer::sum);
            List<SidePanel.Entry> parts = new ArrayList<>();
            for (Categories.Category c : Categories.ALL)
                if (count.containsKey(c.name()))
                    parts.add(new SidePanel.Entry(SidePanel.categoryKey(c.name()), c.name(), Categories.icon(c.icon(), Categories.colour(c.name()), 20), count.get(c.name())));
            side.show(SidePanel.top(shown.size(), (int) shown.stream().filter(r -> favourites.contains(r.id())).count()), parts);
        }

        private void refreshButtons() {
            boolean ready = !busy && modsDir() != null;
            List<Row> tickable = checkable();
            List<Row> ticked = checkedRows();
            int toInstall = (int) ticked.stream().filter(this::installable).count();
            List<Row> mine = ticked.stream().filter(r -> r.installed() != null).toList();
            boolean allTicked = !tickable.isEmpty() && tickable.stream().allMatch(r -> checked.contains(r.id()));
            checkAllButton.setText(allTicked ? PatchbayLauncher.t("Odznacz wszystkie", "Untick all") : PatchbayLauncher.t("Zaznacz wszystkie", "Tick all"));
            checkAllButton.setEnabled(ready && !tickable.isEmpty());
            installButton.setText(PatchbayLauncher.t("Zainstaluj zaznaczone (", "Install ticked (") + toInstall + ")");
            installButton.setEnabled(ready && toInstall > 0);
            boolean allOff = !mine.isEmpty() && mine.stream().noneMatch(r -> switchedOn.contains(r.id()));
            switchButton.setText(allOff ? PatchbayLauncher.t("Włącz", "Switch on") : PatchbayLauncher.t("Wyłącz", "Switch off"));
            switchButton.setEnabled(ready && !mine.isEmpty());
            removeButton.setEnabled(ready && !mine.isEmpty());
            updateAllButton.setEnabled(ready && all.stream().anyMatch(r -> r.rank() == 0 && !unavailable(r)));
            refreshButton.setEnabled(!busy);
        }

        private void setBusy(boolean b, String text) {
            busy = b;
            status.setText(text == null || text.isEmpty() ? " " : text);
            bar.setIndeterminate(b);
            bar.setVisible(b);
            refreshButtons();
        }

        /** True when Bitwig and Patchbay are closed; says so otherwise. Patchbay rewrites its mod list and holds the files while it runs. */
        private boolean patchbayClosed() {
            if (!PatchbayLauncher.bitwigRunning() && !PatchbayLauncher.patchbayRunning()) return true;
            JOptionPane.showMessageDialog(this,
                PatchbayLauncher.t("Zamknij Bitwig Studio i okno Patchbay, a potem spróbuj ponownie.",
                    "Quit Bitwig Studio and the Patchbay window, then try again."),
                "Patchbay Launcher", JOptionPane.WARNING_MESSAGE);
            return false;
        }

        /** The warning for switching off or removing mods that other switched-on mods need; "" when none do. */
        private String neededWarning(List<Row> going) {
            Set<String> ids = new HashSet<>();
            for (Row r : going) ids.add(r.id());
            Set<String> users = new TreeSet<>();
            for (Row r : going) users.addAll(neededBy(r.id(), modsDir()));
            for (Row r : going) users.remove(onDisk.containsKey(r.id()) ? onDisk.get(r.id()).name() : r.name());
            return users.isEmpty() ? "" : "\n\n" + PatchbayLauncher.t("Potrzebują ich włączone mody, które przestaną działać: ",
                "Switched-on mods that need them and will stop working: ") + shortList(new ArrayList<>(users));
        }

        /** "Name" for one mod, "N mods: a, b, c" for several. */
        private String named(List<Row> chosen) {
            if (chosen.size() == 1) return nameOf(chosen.get(0));
            return chosen.size() + PatchbayLauncher.t(" modów: ", " mods: ") + shortList(chosen.stream().map(this::nameOf).toList());
        }

        /** The first eight names, then "and N more". */
        private static String shortList(List<String> names) {
            return String.join(", ", names.subList(0, Math.min(8, names.size())))
                + (names.size() > 8 ? PatchbayLauncher.t(" i ", " and ") + (names.size() - 8) + PatchbayLauncher.t(" innych", " more") : "");
        }

        private void switchMods(List<Row> chosen, boolean on) {
            if (chosen.isEmpty() || !patchbayClosed()) return;
            if (!on) {
                String warning = neededWarning(chosen);
                if (!warning.isEmpty() && JOptionPane.showConfirmDialog(this, Theme.message(PatchbayLauncher.t("Wyłączyć ", "Switch off ") + named(chosen) + "?" + warning),
                        "Patchbay Launcher", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
            }
            try {
                for (Row r : chosen) Mods.setEnabled(r.id(), on);
                for (Row r : chosen) checked.remove(r.id());
                rebuild();
                status.setText((on ? PatchbayLauncher.t("Włączono: ", "Switched on: ") : PatchbayLauncher.t("Wyłączono: ", "Switched off: ")) + named(chosen));
            } catch (IOException ex) {
                rebuild();
                JOptionPane.showMessageDialog(this, String.valueOf(ex.getMessage()), "Patchbay Launcher", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void uninstallMods(List<Row> chosen) {
            if (chosen.isEmpty() || !patchbayClosed()) return;
            String question = PatchbayLauncher.t("Odinstalować ", "Uninstall ") + named(chosen) + "?\n\n"
                + PatchbayLauncher.t("Pliki zostaną usunięte z dysku. Żeby przywrócić mod, trzeba go zainstalować ponownie. Żeby tylko go nie ładować, wybierz Wyłącz.",
                    "The files are deleted from disk. To bring a mod back, install it again. To only stop loading it, choose Switch off.")
                + neededWarning(chosen);
            String remove = PatchbayLauncher.t("Odinstaluj", "Uninstall"), cancel = PatchbayLauncher.t("Anuluj", "Cancel");
            if (JOptionPane.showOptionDialog(this, Theme.message(question), "Patchbay Launcher", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                    null, new String[] { remove, cancel }, cancel) != 0) return;
            try {
                for (Row r : chosen) uninstall(r.id(), modsDir());
                for (Row r : chosen) checked.remove(r.id());
                rebuild();
                status.setText(PatchbayLauncher.t("Odinstalowano: ", "Uninstalled: ") + named(chosen));
            } catch (IOException ex) {
                rebuild();
                JOptionPane.showMessageDialog(this, String.valueOf(ex.getMessage()), "Patchbay Launcher", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void installRows(List<Row> chosen) {
            List<Available> picks = chosen.stream().filter(r -> r.rank() != 1 && r.available() != null).map(Row::available).toList();
            Path mods = modsDir();
            if (picks.isEmpty() || mods == null) return;
            if (!patchbayClosed()) return;
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
