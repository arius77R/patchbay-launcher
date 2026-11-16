import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;

/**
 * Bitwig themes for Patchbay's "Framework: Themes" (bte-theme-loader): .bte colour files from
 * GitHub repositories, put into the components/ folder of the active Patchbay, as its README says.
 * A folder holding one .bte file is one theme (with its overlays/ and mod.json, if any) named after
 * the folder; a .bte file elsewhere is a theme on its own, named after the file. The theme chosen in
 * Patchbay lives in selected-components.properties in Patchbay's settings folder.
 */
final class Themes {
    private Themes() { }

    static final String FRAMEWORK_ID = "bte-theme-loader";
    static final String SELECTION_KEY = "component." + FRAMEWORK_ID + ".group.themes.ids";

    /** One file of a theme: its path in the repository, its git blob hash and size. */
    record File(String path, String sha, long size) { }

    /**
     * A theme in a repository: folder "" for a lone file; files holds what gets installed. bte is the theme file,
     * which may be an older Theme Editor .json that is turned into a .bte when it is installed.
     */
    record Item(String repo, String name, String folder, File bte, List<File> files, String preview) {
        boolean isFolder() { return !folder.isEmpty(); }
        boolean isJson() { return bte.path().toLowerCase(Locale.ROOT).endsWith(".json"); }
        long size() { return files.stream().mapToLong(File::size).sum(); }
    }

    private static boolean image(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        return p.endsWith(".png") || p.endsWith(".jpg") || p.endsWith(".jpeg") || p.endsWith(".gif");
    }

    private static String parent(String path) { return path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : ""; }
    private static String leaf(String path) { return path.substring(path.lastIndexOf('/') + 1); }
    private static String stem(String file) { return file.contains(".") ? file.substring(0, file.lastIndexOf('.')) : file; }

    /** The themes in one repository's tree (tree API JSON); folder, when given, keeps only what is under it. */
    static List<Item> parseTree(String json, String repo, String folder) {
        String prefix = folder == null || folder.isBlank() ? "" : folder.strip().replaceAll("^/+|/+$", "") + "/";
        List<File> blobs = new ArrayList<>();
        Map<String, Object> root = Mods.Json.object(Mods.Json.parse(json));
        for (Object e : Mods.Json.array(root.get("tree"))) {
            Map<String, Object> entry = Mods.Json.object(e);
            String path = Mods.Json.text(entry, "path"), sha = Mods.Json.text(entry, "sha");
            if (path == null || sha == null || !"blob".equals(entry.get("type")) || !path.startsWith(prefix)) continue;
            long size = entry.get("size") instanceof Double d ? d.longValue() : 0;
            blobs.add(new File(path, sha, size));
        }
        Map<String, List<File>> btesByDir = new LinkedHashMap<>();
        for (File f : blobs) if (f.path().toLowerCase(Locale.ROOT).endsWith(".bte")) btesByDir.computeIfAbsent(parent(f.path()), k -> new ArrayList<>()).add(f);
        // older themes from the Theme Editor are .json; one with a .bte of the same name beside it is that theme's older copy
        List<File> jsons = new ArrayList<>();
        for (File f : blobs) {
            if (!jsonTheme(f.path())) continue;
            String name = stem(leaf(f.path()));
            boolean hasBte = btesByDir.getOrDefault(parent(f.path()), List.of()).stream().anyMatch(b -> stem(leaf(b.path())).equalsIgnoreCase(name));
            if (!hasBte) jsons.add(f);
        }

        List<Item> out = new ArrayList<>();
        for (Map.Entry<String, List<File>> dir : btesByDir.entrySet()) {
            String d = dir.getKey();
            if (!d.isEmpty() && dir.getValue().size() == 1) {
                // a theme folder: the .bte, overlays/, mod.json, resources/ and README go into components/<folder>/
                File bte = dir.getValue().get(0);
                List<File> files = new ArrayList<>();
                String preview = null;
                for (File f : blobs) {
                    if (!f.path().startsWith(d + "/")) continue;
                    String rel = f.path().substring(d.length() + 1);
                    boolean topImage = !rel.contains("/") && image(rel);
                    if (topImage) {
                        if (preview == null || rel.toLowerCase(Locale.ROOT).matches(".*(screenshot|preview).*")) preview = f.path();
                        continue; // screenshots stay in the repository
                    }
                    files.add(f);
                }
                out.add(new Item(repo, leaf(d), d, bte, List.copyOf(files), preview));
            } else {
                // lone .bte files: each one a theme named after the file, with a picture of the same name if there is one
                for (File bte : dir.getValue()) {
                    String name = stem(leaf(bte.path()));
                    String preview = null;
                    for (File f : blobs)
                        if (parent(f.path()).equals(d) && image(f.path()) && stem(leaf(f.path())).equalsIgnoreCase(name)) preview = f.path();
                    out.add(new Item(repo, name, "", bte, List.of(bte), preview));
                }
            }
        }
        for (File jf : jsons) {
            String d = parent(jf.path()), name = stem(leaf(jf.path()));
            String preview = null;
            for (File f : blobs)
                if (parent(f.path()).equals(d) && image(f.path()) && stem(leaf(f.path())).equalsIgnoreCase(name)) preview = f.path();
            out.add(new Item(repo, name, "", jf, List.of(jf), preview));
        }
        out.sort(Comparator.comparing(Item::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private static final Set<String> NOT_THEMES = Set.of("package.json", "package-lock.json", "catalog.json", "mod.json", "manifest.json",
        "tsconfig.json", "composer.json", "renovate.json", "settings.json", "launch.json", "tasks.json", "extensions.json");

    /** A .json that may be a Theme Editor theme (what is in it is checked when it is installed). */
    static boolean jsonTheme(String path) {
        String p = path.toLowerCase(Locale.ROOT), name = leaf(p);
        if (!p.endsWith(".json") || NOT_THEMES.contains(name) || name.startsWith(".")) return false;
        for (String part : p.split("/")) if (part.startsWith(".") || part.equals("node_modules")) return false;
        return true;
    }

    /** Colours the Theme Editor no longer writes; Bitwig 6 themes leave them out. */
    private static final Set<String> DEFUNCT = Set.of("Irrelevant Timeline Overlay", "Transparent", "Image Source", "Same as background", "Inherited");

    /**
     * A Theme Editor .json theme ({"window": {"Name": "#rrggbb", …}, "arranger": {…}, "advanced": {…}}) as a .bte file:
     * one "Name: #colour" line per colour, as the Theme Editor itself does when it imports one. null when it isn't such a theme.
     */
    static String jsonToBte(String json, String source) {
        Object parsed;
        try { parsed = Mods.Json.parse(json); } catch (RuntimeException e) { return null; }
        // the Theme Editor's sections; other .json files with colours in them (palettes, catalogues) are not themes
        if (!(parsed instanceof Map<?, ?> root) || !(root.get("window") instanceof Map || root.get("arranger") instanceof Map)) return null;
        StringBuilder out = new StringBuilder("// Converted from the Theme Editor .json " + source + " by Patchbay Launcher\n");
        Set<String> seen = new HashSet<>();
        int colours = 0;
        for (Map.Entry<?, ?> section : root.entrySet()) {
            if (!(section.getValue() instanceof Map<?, ?> entries)) continue;
            for (Map.Entry<?, ?> e : entries.entrySet()) {
                String key = String.valueOf(e.getKey()).strip();
                if (!(e.getValue() instanceof String value)) continue;
                value = value.strip();
                if (key.equals("Gradient") && (value.equals("true") || value.equals("false"))) { out.append(key).append(": ").append(value).append('\n'); continue; }
                if (!value.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?") || DEFUNCT.contains(key) || !seen.add(key)) continue;
                if (key.equals("Piano Roll White Key") && value.length() == 9) value = "#b3b3b3"; // as the Theme Editor does
                out.append(key).append(": ").append(value).append('\n');
                colours++;
            }
        }
        return colours >= 10 ? out.toString() : null;
    }

    private static Path fixtures() {
        String f = System.getProperty("launcher.fixtures"); // offline test
        return f == null ? null : Path.of(f);
    }

    static List<Item> fetchRepo(String repo, String folder) throws IOException {
        return fetchRepo(repo, folder, true);
    }

    /** At most this many repositories are followed from a list repository's README. */
    static final int LIST_LINKS = 40;

    /**
     * The themes in a repository. A repository with no themes of its own whose README links to other
     * repositories (a list like awesome-bitwig-themes) brings in the themes of the repositories it links to.
     */
    static List<Item> fetchRepo(String repo, String folder, boolean followList) throws IOException {
        Path fx = fixtures();
        String tree = fx != null
            ? Files.readString(fx.resolve(repo.replace('/', '_') + ".tree.json"))
            : PatchbayLauncher.httpText("https://api.github.com/repos/" + repo + "/git/trees/HEAD?recursive=1");
        List<Item> items = keepRealThemes(parseTree(tree, repo, folder));
        if (!items.isEmpty() || !followList || (folder != null && !folder.isBlank())) return items;
        String readme = readmePath(tree);
        if (readme == null) return items;
        String text = fx != null ? Files.readString(fx.resolve(repo.replace('/', '_')).resolve(readme))
            : PatchbayLauncher.httpText(Devices.rawUrl(repo, readme));
        List<Item> all = new ArrayList<>();
        for (String linked : linkedRepos(text, repo)) {
            try { all.addAll(fetchRepo(linked, "", false)); } catch (IOException | RuntimeException skip) { /* a moved or private repository */ }
        }
        all.sort(Comparator.comparing(Item::name, String.CASE_INSENSITIVE_ORDER));
        return all;
    }

    private static String readmePath(String tree) {
        for (Object e : Mods.Json.array(Mods.Json.object(Mods.Json.parse(tree)).get("tree"))) {
            String path = Mods.Json.text(Mods.Json.object(e), "path");
            if (path != null && !path.contains("/") && path.toLowerCase(Locale.ROOT).matches("readme(\\.md|\\.markdown|\\.txt)?")) return path;
        }
        return null;
    }

    /** The GitHub repositories a README links to, each once, in order, without the list itself. */
    static List<String> linkedRepos(String readme, String self) {
        Set<String> out = new LinkedHashSet<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("github\\.com/([A-Za-z0-9-]{1,39})/([A-Za-z0-9._-]{1,100})").matcher(readme);
        while (m.find() && out.size() < LIST_LINKS) {
            String name = m.group(2).replaceAll("\\.git$", "").replaceAll("[.]+$", "");
            String r = m.group(1) + "/" + name;
            if (!r.equalsIgnoreCase(self) && !name.isEmpty()) out.add(r);
        }
        return List.copyOf(out);
    }

    /** At most this many .json files per repository are opened to see whether they are themes. */
    static final int JSON_CHECKS = 60;

    /** Leaves out .json files that aren't Theme Editor themes (they are read from GitHub's raw files, which don't count against the API limit). */
    static List<Item> keepRealThemes(List<Item> items) {
        List<Item> out = new ArrayList<>();
        int checked = 0;
        for (Item i : items) {
            if (!i.isJson()) { out.add(i); continue; }
            if (checked++ >= JSON_CHECKS) continue;
            try {
                Path fx = fixtures();
                String text = fx != null ? Files.readString(fx.resolve(i.repo().replace('/', '_')).resolve(i.bte().path()))
                    : PatchbayLauncher.httpText(Devices.rawUrl(i.repo(), i.bte().path()));
                if (jsonToBte(text, "") != null) out.add(i);
            } catch (IOException | RuntimeException skip) {
                // unreadable: not listed
            }
        }
        return out;
    }

    /** Every enabled theme source's themes; a source that fails is skipped unless all fail. */
    static List<Item> fetch() throws IOException {
        List<Item> all = new ArrayList<>();
        IOException failure = null;
        for (Sources.Source s : Sources.enabled(Sources.THEMES)) {
            try {
                all.addAll(fetchRepo(s.repo(), s.filter()));
            } catch (IOException | RuntimeException ex) {
                failure = ex instanceof IOException io ? io : new IOException(s.repo() + ": " + ex.getMessage());
            }
        }
        if (all.isEmpty() && failure != null) throw failure;
        return all;
    }

    // ---------- where they go ----------

    /** components/ of the active Patchbay, or null before Patchbay is installed. */
    static Path components() {
        String v = PatchbayLauncher.currentVersion();
        return v == null ? null : PatchbayLauncher.folderOf(v).resolve("components");
    }

    /** Where a theme sits in components/: its folder, or its .bte file for a lone file. */
    static Path target(Path components, Item item) {
        return item.isFolder() ? components.resolve(item.name()) : components.resolve(stem(leaf(item.bte().path())) + ".bte");
    }

    private static Path installedBte(Path components, Item item) {
        Path t = target(components, item);
        return item.isFolder() ? t.resolve(leaf(item.bte().path())) : t;
    }

    /** 2 = not installed, 0 = installed but the repository has another version, 1 = up to date. */
    static int rank(Path components, Item item) {
        if (components == null) return 2;
        Path bte = installedBte(components, item);
        if (!Files.isRegularFile(bte)) return 2;
        try {
            if (item.isJson()) {
                // a converted theme remembers the hash of the .json it came from in its first line
                String first = Files.readAllLines(bte, StandardCharsets.UTF_8).stream().findFirst().orElse("");
                return first.contains("(" + item.bte().sha() + ")") ? 1 : 0;
            }
            return Devices.gitBlobSha(bte).equals(item.bte().sha()) ? 1 : 0;
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }

    interface Fetcher { void fetch(String repo, String path, Path target) throws IOException; }

    /** Installs one theme: every file is checked against its listed hash before the old copy is replaced. */
    static void install(Path components, Item item, Fetcher fetcher) throws IOException {
        Files.createDirectories(components);
        Path staging = Files.createTempDirectory(components, ".theme-");
        try {
            for (File f : item.files()) {
                String rel = item.isFolder() ? f.path().substring(item.folder().length() + 1) : leaf(f.path());
                Path out = staging.resolve(rel).normalize();
                if (!out.startsWith(staging)) throw new IOException(item.name() + ": " + rel);
                Files.createDirectories(out.getParent());
                fetcher.fetch(item.repo(), f.path(), out);
                if (!Devices.gitBlobSha(out).equals(f.sha()))
                    throw new IOException(item.name() + ": " + PatchbayLauncher.t(
                        "plik różni się od wersji z listy. Kliknij Odśwież i spróbuj ponownie.",
                        "the file differs from the listed version. Click Refresh and try again."));
            }
            if (item.isJson()) {
                Path json = staging.resolve(leaf(item.bte().path()));
                String bte = jsonToBte(Files.readString(json, StandardCharsets.UTF_8), item.repo() + "/" + item.bte().path() + " (" + item.bte().sha() + ")");
                if (bte == null) throw new IOException(item.name() + ": " + PatchbayLauncher.t(
                    "to nie jest motyw (plik .json bez kolorów Theme Editora).", "this isn't a theme (a .json without Theme Editor colours)."));
                Files.delete(json);
                Files.writeString(staging.resolve(stem(leaf(item.bte().path())) + ".bte"), bte, StandardCharsets.UTF_8);
            }
            Path target = target(components, item);
            remove(target);
            Path made = item.isFolder() ? staging : staging.resolve(stem(leaf(item.bte().path())) + ".bte");
            Files.move(made, target, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            remove(staging);
        }
    }

    static void remove(Path p) throws IOException {
        if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) return;
        try (var walk = Files.walk(p)) {
            for (Path q : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(q);
        }
    }

    // ---------- which theme Patchbay uses ----------

    /** Patchbay's ID for a theme named only by its folder or file (its ComponentCatalog.derivedId). */
    static String derivedId(String name) {
        String plain = Normalizer.normalize(name, Normalizer.Form.NFKD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        String id = plain.replaceAll("[^a-z0-9_.-]+", "-").replaceAll("-{2,}", "-").replaceAll("^[-_.]+|[-_.]+$", "");
        if (id.isEmpty()) {
            try {
                id = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(name.getBytes(StandardCharsets.UTF_8))).substring(0, 8);
            } catch (java.security.NoSuchAlgorithmException never) {
                id = "theme";
            }
        }
        return Character.isLetter(id.charAt(0)) ? id : "c-" + id;
    }

    /** The ID Patchbay gives an installed theme: its mod.json's component.id, else the derived one. */
    static String idOf(Path components, Item item) {
        if (components != null && item.isFolder()) {
            Path manifest = target(components, item).resolve("mod.json");
            try {
                if (Files.isRegularFile(manifest)) {
                    Object c = Mods.Json.object(Mods.Json.parse(Files.readString(manifest))).get("component");
                    String id = c instanceof Map ? Mods.Json.text(Mods.Json.object(c), "id") : null;
                    if (id != null && !id.isBlank()) return id;
                }
            } catch (IOException | RuntimeException ignored) { }
        }
        return derivedId(item.isFolder() ? item.name() : stem(leaf(item.bte().path())));
    }

    private static Path selectionFile() { return Devices.patchbaySettings().resolve("selected-components.properties"); }
    private static Path enabledFile() { return Devices.patchbaySettings().resolve("enabled-mods.properties"); }

    private static Properties load(Path file) {
        Properties p = new Properties();
        if (Files.isRegularFile(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { p.load(r); } catch (IOException ignored) { }
        }
        return p;
    }

    private static void store(Path file, Properties p, String comment) throws IOException {
        Files.createDirectories(file.getParent());
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) { p.store(w, comment); }
    }

    /** The theme ID chosen in Patchbay, when Framework: Themes is ticked; else null. */
    static String activeId() {
        if (!"true".equals(load(enabledFile()).getProperty("mod." + FRAMEWORK_ID))) return null;
        String ids = load(selectionFile()).getProperty(SELECTION_KEY, "").trim();
        return ids.isEmpty() ? null : ids.split(",")[0];
    }

    /** Chooses a theme in Patchbay's settings and ticks Framework: Themes; id null unticks it. Patchbay must be closed. */
    static void choose(String id) throws IOException {
        Properties enabled = load(enabledFile());
        enabled.setProperty("mod." + FRAMEWORK_ID, Boolean.toString(id != null));
        if (id != null) {
            Properties selection = load(selectionFile());
            selection.setProperty(SELECTION_KEY, id);
            store(selectionFile(), selection, "BML v3 requested component selections; never auto-substitute unavailable IDs.");
        }
        store(enabledFile(), enabled, "BML enabled mods");
    }

    // ---------- the section ----------

    static final class Panel extends JPanel {
        private record Row(Item item, int rank, boolean active) { }

        private final List<Row> rows = new ArrayList<>();
        private final Set<String> checked = new HashSet<>();
        private final AbstractTableModel model = new AbstractTableModel() {
            private final String[] columns = { "", PatchbayLauncher.t("Motyw", "Theme"), PatchbayLauncher.t("Źródło", "Source"), PatchbayLauncher.t("Stan", "Status") };
            @Override public int getRowCount() { return rows.size(); }
            @Override public int getColumnCount() { return columns.length; }
            @Override public String getColumnName(int c) { return columns[c]; }
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? Boolean.class : String.class; }
            @Override public boolean isCellEditable(int r, int c) { return c == 0 && !busy && rows.get(r).rank() != 1; }
            @Override public void setValueAt(Object v, int r, int c) {
                if (c != 0) return;
                if (Boolean.TRUE.equals(v)) checked.add(key(rows.get(r).item())); else checked.remove(key(rows.get(r).item()));
                fireTableCellUpdated(r, c);
                refreshButtons();
            }
            @Override public Object getValueAt(int r, int c) {
                Row row = rows.get(r);
                return switch (c) {
                    case 0 -> checked.contains(key(row.item()));
                    case 1 -> row.item().name();
                    case 2 -> row.item().repo();
                    default -> row.active() ? "active|" + PatchbayLauncher.t("aktywny", "active") : switch (row.rank()) {
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
        private final JButton checkAllButton = Theme.button(" ", "secondary");
        private final JButton installButton = Theme.button(" ", "primary");
        private final JButton folderButton = Theme.button(PatchbayLauncher.t("Pokaż folder", "Show folder"), "secondary");
        private final JButton refreshButton = Theme.button(PatchbayLauncher.t("Odśwież", "Refresh"), "secondary");
        private final JLabel status = Theme.label(" ", Theme.text(14f), Theme.MUTED);
        private final JProgressBar bar = new JProgressBar();
        private final Map<String, BufferedImage> previews = new HashMap<>();
        private List<Item> items = List.of();
        private boolean busy, loaded;
        private String shownPreview;

        private static String key(Item i) { return i.repo() + "/" + i.bte().path(); }

        Panel() {
            super(new BorderLayout());
            Theme.styleTable(table);
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.getColumnModel().getColumn(0).setMinWidth(40);
            table.getColumnModel().getColumn(0).setMaxWidth(40);
            table.getColumnModel().getColumn(1).setPreferredWidth(220);
            table.getColumnModel().getColumn(2).setPreferredWidth(180);
            table.getColumnModel().getColumn(3).setMinWidth(140);
            table.getColumnModel().getColumn(3).setMaxWidth(150);
            table.getColumnModel().getColumn(3).setCellRenderer(Theme.pillCells());
            table.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) showSelected(); });

            details.setBorder(new EmptyBorder(18, 18, 18, 18));
            JComponent body = Theme.split(Theme.framed(table), details);

            bar.setPreferredSize(new Dimension(10, 4));
            bar.setVisible(false);
            JPanel left = new JPanel(new BorderLayout(0, 6));
            left.setOpaque(false);
            left.add(status, BorderLayout.CENTER);
            left.add(bar, BorderLayout.SOUTH);
            add(Theme.page(PatchbayLauncher.t("Motywy", "Themes"), subtitle, Theme.row(8, checkAllButton, refreshButton), body,
                left, Theme.row(8, folderButton, installButton)), BorderLayout.CENTER);

            checkAllButton.addActionListener(e -> checkAll());
            installButton.addActionListener(e -> installChecked());
            refreshButton.addActionListener(e -> reload());
            folderButton.addActionListener(e -> {
                Path c = components();
                if (c == null) return;
                try { Files.createDirectories(c); } catch (IOException ignored) { }
                Platform.open(c.toString());
            });
            showSelected();
            refreshButtons();
        }

        void shown() { if (!loaded) reload(); }
        void sourcesChanged() { if (loaded) reload(); }
        /** Patchbay was installed or updated: its components/ folder changed. */
        void patchbayChanged() { if (loaded) rebuild(); }

        void reload() {
            if (busy) return;
            loaded = true;
            setBusy(true, PatchbayLauncher.t("Pobieranie listy motywów…", "Loading the theme list…"));
            new Thread(() -> {
                try {
                    List<Item> found = fetch();
                    SwingUtilities.invokeLater(() -> { items = found; setBusy(false, ""); rebuild(); });
                } catch (IOException | RuntimeException ex) {
                    SwingUtilities.invokeLater(() -> setBusy(false, String.valueOf(ex.getMessage())));
                }
            }, "themes-list").start();
        }

        /** Installed state and the active theme are read off the event thread (hashing files). */
        private void rebuild() {
            List<Item> shown = items;
            Path components = components();
            new Thread(() -> {
                String active = activeId();
                List<Row> fresh = new ArrayList<>();
                for (Item i : shown) {
                    int rank = rank(components, i);
                    fresh.add(new Row(i, rank, rank != 2 && active != null && active.equals(idOf(components, i))));
                }
                fresh.sort(Comparator.comparing((Row r) -> !r.active()).thenComparingInt(Row::rank)
                    .thenComparing(r -> r.item().name(), String.CASE_INSENSITIVE_ORDER));
                SwingUtilities.invokeLater(() -> {
                    String keep = table.getSelectedRow() >= 0 && table.getSelectedRow() < rows.size() ? key(rows.get(table.getSelectedRow()).item()) : null;
                    rows.clear();
                    rows.addAll(fresh);
                    Set<String> canTick = new HashSet<>();
                    for (Row r : rows) if (r.rank() != 1) canTick.add(key(r.item()));
                    checked.retainAll(canTick);
                    model.fireTableDataChanged();
                    long have = rows.stream().filter(r -> r.rank() != 2).count();
                    String activeName = rows.stream().filter(Row::active).map(r -> r.item().name()).findFirst().orElse(null);
                    subtitle.setText(components == null ? PatchbayLauncher.t("Najpierw zainstaluj Patchbay.", "Install Patchbay first.")
                        : PatchbayLauncher.t("Na liście: ", "Listed: ") + rows.size()
                            + " · " + PatchbayLauncher.t("zainstalowane: ", "installed: ") + have
                            + " · " + (activeName == null ? PatchbayLauncher.t("brak aktywnego motywu", "no active theme")
                                : PatchbayLauncher.t("aktywny: ", "active: ") + activeName));
                    String select = System.getProperty("launcher.select"); // offline test: show this theme's details
                    if (select == null) select = keep;
                    int pick = rows.isEmpty() ? -1 : 0;
                    for (int r = 0; select != null && r < rows.size(); r++)
                        if (key(rows.get(r).item()).equals(select) || rows.get(r).item().name().equals(select)) pick = r;
                    if (pick >= 0) table.setRowSelectionInterval(pick, pick);
                    refreshButtons();
                    showSelected();
                });
            }, "themes-rows").start();
        }

        private List<Row> checkable() { return rows.stream().filter(r -> r.rank() != 1).toList(); }
        private List<Row> checkedRows() { return rows.stream().filter(r -> checked.contains(key(r.item()))).toList(); }

        private void checkAll() {
            List<Row> tickable = checkable();
            boolean allTicked = !tickable.isEmpty() && tickable.stream().allMatch(r -> checked.contains(key(r.item())));
            checked.clear();
            if (!allTicked) for (Row r : tickable) checked.add(key(r.item()));
            model.fireTableDataChanged();
            refreshButtons();
        }

        private void refreshButtons() {
            boolean ready = !busy && components() != null;
            List<Row> tickable = checkable();
            int ticked = checkedRows().size();
            boolean allTicked = !tickable.isEmpty() && ticked == tickable.size();
            checkAllButton.setText(allTicked ? PatchbayLauncher.t("Odznacz wszystkie", "Untick all") : PatchbayLauncher.t("Zaznacz wszystkie", "Tick all"));
            checkAllButton.setEnabled(ready && !tickable.isEmpty());
            installButton.setText(PatchbayLauncher.t("Zainstaluj zaznaczone (", "Install ticked (") + ticked + ")");
            installButton.setEnabled(ready && ticked > 0);
            folderButton.setEnabled(components() != null);
            refreshButton.setEnabled(!busy);
        }

        private void setBusy(boolean b, String text) {
            busy = b;
            status.setText(text == null || text.isEmpty() ? " " : text);
            bar.setIndeterminate(b);
            bar.setVisible(b);
            refreshButtons();
        }

        private void showSelected() {
            details.removeAll();
            int r = table.getSelectedRow();
            JPanel col = new JPanel();
            col.setOpaque(false);
            col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
            if (r < 0 || r >= rows.size()) {
                col.add(Theme.wrapped(PatchbayLauncher.t(
                    "Motyw zmienia kolory Bitwiga. Kliknij motyw na liście, żeby zobaczyć podgląd.",
                    "A theme changes Bitwig's colours. Click a theme in the list to see a preview."), 226, Theme.MUTED, 13f));
                details.add(col, BorderLayout.NORTH);
                details.revalidate();
                details.repaint();
                return;
            }
            Row row = rows.get(r);
            Item item = row.item();
            Theme.Picture preview = new Theme.Picture(282, 280);
            preview.setAlignmentX(LEFT_ALIGNMENT);
            preview.getAccessibleContext().setAccessibleName(PatchbayLauncher.t("Podgląd motywu: ", "Theme preview: ") + item.name());
            col.add(preview);
            col.add(Box.createVerticalStrut(14));
            JLabel name = Theme.label(item.name(), Theme.bold(20f), Theme.TEXT);
            name.setAlignmentX(LEFT_ALIGNMENT);
            col.add(name);
            col.add(Box.createVerticalStrut(10));
            JPanel facts = Theme.facts(new String[][] {
                { PatchbayLauncher.t("Stan", "Status"), row.active() ? PatchbayLauncher.t("aktywny w Patchbay", "active in Patchbay")
                    : row.rank() == 2 ? PatchbayLauncher.t("nie zainstalowany", "not installed")
                    : row.rank() == 0 ? PatchbayLauncher.t("jest nowsza wersja", "a newer version is out") : PatchbayLauncher.t("zainstalowany", "installed") },
                { PatchbayLauncher.t("Rozmiar", "Size"), Math.max(1, item.size() / 1024) + " KB" },
                { PatchbayLauncher.t("Format", "Format"), item.isJson()
                    ? PatchbayLauncher.t(".json z Theme Editora, zamieniany na .bte (starsze motywy mogą nie zmienić wszystkich kolorów Bitwiga 6)",
                        ".json from the Theme Editor, turned into .bte (older themes may not change every Bitwig 6 colour)")
                    : ".bte" },
                { PatchbayLauncher.t("Źródło", "Source"), item.repo() } });
            facts.setAlignmentX(LEFT_ALIGNMENT);
            facts.setMaximumSize(new Dimension(Integer.MAX_VALUE, facts.getPreferredSize().height));
            col.add(facts);
            col.add(Box.createVerticalStrut(10));
            JLabel how = Theme.wrapped(PatchbayLauncher.t(
                "Po zmianie motywu uruchom Bitwiga ponownie z Patchbay (Load).",
                "After changing the theme, start Bitwig again from Patchbay (Load)."), 226, Theme.MUTED, 12f);
            how.setAlignmentX(LEFT_ALIGNMENT);
            col.add(how);
            details.add(Theme.scrolling(col), BorderLayout.CENTER); // a tall preview or a short window: the details scroll, the buttons stay

            JPanel south = new JPanel(new GridLayout(0, 1, 0, 8));
            south.setOpaque(false);
            south.setBorder(new EmptyBorder(12, 0, 0, 0));
            Path components = components();
            if (row.rank() != 2 && components != null) {
                if (!row.active()) {
                    JButton use = Theme.button(PatchbayLauncher.t("Ustaw jako motyw", "Use this theme"), "primary");
                    use.setEnabled(!busy);
                    use.addActionListener(e -> choose(item, components));
                    south.add(use);
                } else {
                    JButton off = Theme.button(PatchbayLauncher.t("Wyłącz motywy", "Turn themes off"), "secondary");
                    off.setEnabled(!busy);
                    off.addActionListener(e -> turnOff());
                    south.add(off);
                }
                JButton delete = Theme.button(PatchbayLauncher.t("Usuń", "Remove"), "danger");
                delete.setEnabled(!busy);
                delete.addActionListener(e -> removeTheme(item, components, row.active()));
                south.add(delete);
            }
            JButton page = Theme.button(PatchbayLauncher.t("Strona projektu", "Project page"), "secondary");
            page.addActionListener(e -> Platform.open("https://github.com/" + item.repo()
                + (item.isFolder() ? "/tree/HEAD/" + item.folder().replace(" ", "%20") : "")));
            south.add(page);
            details.add(south, BorderLayout.SOUTH);
            details.revalidate();
            details.repaint();

            if (item.preview() == null) return;
            String k = item.repo() + "/" + item.preview();
            shownPreview = k;
            BufferedImage have = previews.get(k);
            if (have != null) { preview.setImage(have); return; }
            new Thread(() -> {
                BufferedImage icon = loadPreview(item);
                SwingUtilities.invokeLater(() -> {
                    if (icon != null) previews.put(k, icon);
                    if (k.equals(shownPreview)) { preview.setImage(icon); details.revalidate(); }
                });
            }, "themes-preview").start();
        }

        private BufferedImage loadPreview(Item item) {
            try {
                BufferedImage img;
                if (fixtures() != null) {
                    Path f = fixtures().resolve(item.repo().replace('/', '_')).resolve(item.preview());
                    if (!Files.isRegularFile(f)) return null;
                    img = ImageIO.read(f.toFile());
                } else {
                    Path temp = Files.createTempFile("patchbay-theme", ".img");
                    try {
                        PatchbayLauncher.download(Devices.rawUrl(item.repo(), item.preview()), temp, null);
                        img = ImageIO.read(temp.toFile());
                    } finally {
                        Files.deleteIfExists(temp);
                    }
                }
                if (img == null) return null;
                return Theme.shrink(img, 640);
            } catch (IOException | RuntimeException e) {
                return null;
            }
        }

        /** Patchbay reads its settings when it starts and writes them when it closes, so it must be closed here. */
        private boolean patchbayClosed() {
            if (!Platform.patchbayRunning()) return true;
            JOptionPane.showMessageDialog(this, PatchbayLauncher.t(
                    "Zamknij okno Patchbay, a potem spróbuj ponownie.", "Close the Patchbay window, then try again."),
                "Patchbay Launcher", JOptionPane.WARNING_MESSAGE);
            return false;
        }

        private void choose(Item item, Path components) {
            if (!patchbayClosed()) return;
            try {
                Themes.choose(idOf(components, item));
                status.setText(PatchbayLauncher.t("Ustawiono motyw „", "Theme set: \"") + item.name()
                    + PatchbayLauncher.t("”. Uruchom Patchbay i kliknij Load.", "\". Start Patchbay and click Load."));
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, String.valueOf(ex.getMessage()), "Patchbay Launcher", JOptionPane.ERROR_MESSAGE);
            }
            rebuild();
        }

        private void turnOff() {
            if (!patchbayClosed()) return;
            try {
                Themes.choose(null);
                status.setText(PatchbayLauncher.t("Motywy wyłączone. Bitwig wróci do swoich kolorów po ponownym starcie.",
                    "Themes are off. Bitwig gets its own colours back at the next start."));
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, String.valueOf(ex.getMessage()), "Patchbay Launcher", JOptionPane.ERROR_MESSAGE);
            }
            rebuild();
        }

        private void removeTheme(Item item, Path components, boolean active) {
            int answer = JOptionPane.showConfirmDialog(this,
                PatchbayLauncher.t("Usunąć motyw „", "Remove the theme \"") + item.name() + PatchbayLauncher.t("” z Patchbay?", "\" from Patchbay?"),
                "Patchbay Launcher", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (answer != JOptionPane.OK_OPTION) return;
            if (active && !patchbayClosed()) return;
            try {
                Themes.remove(target(components, item));
                if (active) Themes.choose(null);
                status.setText(PatchbayLauncher.t("Usunięto: ", "Removed: ") + item.name());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, String.valueOf(ex.getMessage()), "Patchbay Launcher", JOptionPane.ERROR_MESSAGE);
            }
            rebuild();
        }

        private void installChecked() {
            List<Item> picks = checkedRows().stream().map(Row::item).toList();
            Path components = components();
            if (picks.isEmpty() || components == null) return;
            setBusy(true, "");
            new Thread(() -> {
                try {
                    for (Item item : picks) {
                        SwingUtilities.invokeLater(() -> status.setText(PatchbayLauncher.t("Instalowanie: ", "Installing: ") + item.name()));
                        install(components, item, (repo, path, target) -> {
                            Path fx = fixtures();
                            if (fx != null) Files.copy(fx.resolve(repo.replace('/', '_')).resolve(path), target, StandardCopyOption.REPLACE_EXISTING);
                            else PatchbayLauncher.download(Devices.rawUrl(repo, path), target, null);
                        });
                    }
                    SwingUtilities.invokeLater(() -> {
                        checked.clear();
                        setBusy(false, picks.size() == 1
                            ? PatchbayLauncher.t("Zainstalowano. Kliknij „Ustaw jako motyw”, żeby go włączyć.", "Installed. Click \"Use this theme\" to turn it on.")
                            : PatchbayLauncher.t("Zainstalowano. Wybierz motyw i kliknij „Ustaw jako motyw”.", "Installed. Pick a theme and click \"Use this theme\"."));
                        rebuild();
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        setBusy(false, "");
                        rebuild();
                        JOptionPane.showMessageDialog(this, String.valueOf(ex.getMessage()), "Patchbay Launcher", JOptionPane.ERROR_MESSAGE);
                    });
                }
            }, "themes-install").start();
        }
    }
}
