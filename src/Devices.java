import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The devices section: native Bitwig devices kept as files in GitHub repositories (Polarity's
 * github.com/polarity/bitwig-devices built in, others from Sources). A repository's files are
 * listed with GitHub's tree API, downloaded as raw files and checked against the git blob hash
 * from that list. They are kept in the launcher's folder, one subfolder per repository and build,
 * and those subfolders are put on Patchbay's Device Folders list.
 */
final class Devices {
    private Devices() { }

    static final Pattern DEVICE_FILE = Pattern.compile("(?:(.+)/)?([^/]+)\\.(bwdevice|bwmodulator|bwmodule)");
    static final Pattern BUILD = Pattern.compile("Bitwig-[^/]+");
    /** Patchbay's framework that loads the files, and the per-user file holding its folder list. */
    static final String FRAMEWORK_ID = "custom-device-registrar";

    /** build: the "Bitwig-…" folder the file is in, or "" for a file that serves every version. */
    record Item(String repo, String build, String name, String kind, String path, String sha, long size, String type, String description) {
        String fileName() { return name + "." + kind; }
        String previewPath() { return "previews/" + name.toLowerCase(Locale.ROOT).replace(' ', '-') + ".png"; }
    }

    // ---------- catalogue ----------

    /**
     * The device files in one repository's tree (tree API JSON). folder, when given, keeps only
     * files under it. A repository with Bitwig-<version> folders is read as builds: files outside
     * them are left out, so one device never shows twice.
     */
    static List<Item> parseTree(String json, Map<String, String[]> about, String repo, String folder) {
        String prefix = folder == null || folder.isBlank() ? "" : folder.strip().replaceAll("^/+|/+$", "") + "/";
        List<String[]> found = new ArrayList<>();
        boolean hasBuilds = false;
        Map<String, Object> root = Mods.Json.object(Mods.Json.parse(json));
        for (Object e : Mods.Json.array(root.get("tree"))) {
            Map<String, Object> entry = Mods.Json.object(e);
            String path = Mods.Json.text(entry, "path"), sha = Mods.Json.text(entry, "sha");
            if (path == null || sha == null || !"blob".equals(entry.get("type")) || !path.startsWith(prefix)) continue;
            Matcher m = DEVICE_FILE.matcher(path);
            if (!m.matches()) continue;
            String first = path.contains("/") ? path.substring(0, path.indexOf('/')) : "";
            String build = BUILD.matcher(first).matches() ? first : "";
            if (!build.isEmpty()) hasBuilds = true;
            long size = entry.get("size") instanceof Double d ? d.longValue() : 0;
            found.add(new String[] { build, m.group(2), m.group(3), path, sha, String.valueOf(size) });
        }
        List<Item> out = new ArrayList<>();
        for (String[] f : found) {
            if (hasBuilds && f[0].isEmpty()) continue;
            String[] info = about.get(f[1]);
            String kind = f[2];
            String type = info != null ? info[0] : kind.equals("bwmodulator") ? "Modulator" : kind.equals("bwmodule") ? "Grid module" : "Device";
            out.add(new Item(repo, f[0], f[1], kind, f[3], f[4], Long.parseLong(f[5]), type, info == null ? "" : info[1]));
        }
        out.sort(Comparator.comparing(Item::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    static final Pattern README_ROW = Pattern.compile("^\\|\\s*\\[([^\\]]+)\\]\\([^)]*\\)\\s*\\|\\s*([^|]+?)\\s*\\|\\s*([^|]+?)\\s*\\|.*$");

    /** Device name to {type, what it does}, from a table in the repository's README. */
    static Map<String, String[]> parseReadme(String markdown) {
        Map<String, String[]> out = new HashMap<>();
        for (String line : markdown.split("\n")) {
            Matcher m = README_ROW.matcher(line.strip());
            if (m.matches()) out.put(m.group(1), new String[] { m.group(2), m.group(3) });
        }
        return out;
    }

    static String rawUrl(String repo, String path) {
        StringBuilder b = new StringBuilder("https://raw.githubusercontent.com/" + repo + "/HEAD");
        for (String part : path.split("/")) b.append('/').append(URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20"));
        return b.toString();
    }

    private static Path fixtures() {
        String f = System.getProperty("launcher.fixtures"); // offline test
        return f == null ? null : Path.of(f);
    }

    /** One repository's device files; folder as in parseTree. */
    static List<Item> fetchRepo(String repo, String folder) throws IOException {
        String readme, tree;
        Path fx = fixtures();
        if (fx != null) {
            String stem = repo.replace('/', '_');
            Path t = fx.resolve(stem + ".tree.json"), r = fx.resolve(stem + ".readme.md");
            if (!Files.isRegularFile(t) && repo.equals("polarity/bitwig-devices")) { t = fx.resolve("devices-tree.json"); r = fx.resolve("devices-readme.md"); }
            tree = Files.readString(t);
            readme = Files.isRegularFile(r) ? Files.readString(r) : "";
        } else {
            tree = PatchbayLauncher.httpText("https://api.github.com/repos/" + repo + "/git/trees/HEAD?recursive=1");
            try {
                readme = PatchbayLauncher.httpText(rawUrl(repo, "README.md"));
            } catch (IOException noDescriptions) {
                readme = "";
            }
        }
        return parseTree(tree, parseReadme(readme), repo, folder);
    }

    /** Every enabled device source's files; a source that fails is skipped unless all fail. */
    static List<Item> fetch() throws IOException {
        List<Item> all = new ArrayList<>();
        IOException failure = null;
        for (Sources.Source s : Sources.enabled(Sources.DEVICES)) {
            try {
                all.addAll(fetchRepo(s.repo(), s.filter()));
                Sources.answered(Sources.DEVICES, s.repo(), null);
            } catch (IOException | RuntimeException ex) {
                failure = ex instanceof IOException io ? io : new IOException(s.repo() + ": " + ex.getMessage());
                Sources.answered(Sources.DEVICES, s.repo(), failure);
            }
        }
        if (all.isEmpty() && failure != null) throw failure;
        return all;
    }

    /**
     * Device files on disk, in the folders of the enabled sources, that their repository doesn't list any
     * more: kept as items (path "removed:…", no hash) so they can be switched off or uninstalled.
     */
    static List<Item> leftovers(List<Item> listed) {
        Set<String> known = new HashSet<>(), answered = new HashSet<>();
        for (Item i : listed) { known.add(i.repo() + " " + i.build() + " " + i.fileName()); answered.add(i.repo()); }
        answered.addAll(Sources.missingRepos(Sources.DEVICES)); // a repository that is gone holds none of them any more
        List<Item> out = new ArrayList<>();
        for (Sources.Source s : Sources.enabled(Sources.DEVICES)) {
            if (!answered.contains(s.repo())) continue; // its list couldn't be had (no network, rate limit): nothing can be called removed
            Path folder = root().resolve(s.repo().replace('/', '_'));
            if (!Files.isDirectory(folder)) continue;
            try (var builds = Files.list(folder)) {
                for (Path b : builds.filter(Files::isDirectory).toList()) {
                    String build = b.getFileName().toString().equals("files") ? "" : b.getFileName().toString();
                    try (var files = Files.list(b)) {
                        for (Path f : files.filter(Files::isRegularFile).toList()) {
                            String file = f.getFileName().toString();
                            if (file.endsWith(OFF)) file = file.substring(0, file.length() - OFF.length());
                            int dot = file.lastIndexOf('.');
                            if (dot <= 0) continue;
                            String kind = file.substring(dot + 1), name = file.substring(0, dot);
                            if (!kind.equals("bwdevice") && !kind.equals("bwmodulator") && !kind.equals("bwmodule")) continue;
                            if (!known.add(s.repo() + " " + build + " " + file)) continue;
                            String type = kind.equals("bwmodulator") ? "Modulator" : kind.equals("bwmodule") ? "Grid module" : "Device";
                            out.add(new Item(s.repo(), build, name, kind, "removed:" + build + "/" + file, "", Files.size(f), type, ""));
                        }
                    }
                }
            } catch (IOException unreadable) {
                // a folder that can't be read: nothing to show from it
            }
        }
        return out;
    }

    /** What the enabled sources' authors withdrew (withdrawn.json), "repo file" to the reason. */
    static Map<String, String> withdrawn() {
        Map<String, String> out = new HashMap<>();
        for (Sources.Source s : Sources.enabled(Sources.DEVICES))
            for (Map.Entry<String, String> e : Sources.withdrawn(s.repo(), "devices").entrySet()) out.put(s.repo() + " " + e.getKey(), e.getValue());
        return out;
    }

    static boolean isLeftover(Item i) { return i.path().startsWith("removed:"); }

    // ---------- which build ----------

    /** The first version in a build folder's name: "Bitwig-6.1.3-6.1.4" starts at 6.1.3. */
    static String firstVersion(String build) {
        Matcher m = Pattern.compile("\\d+(?:\\.\\d+)+").matcher(build);
        return m.find() ? m.group() : "0";
    }

    /** The build for a Bitwig version: the newest build that starts at or before it; the newest of all when the version is unknown. */
    static String pickBuild(Collection<String> builds, String bitwigVersion) {
        List<String> sorted = new ArrayList<>(builds);
        sorted.sort((a, b) -> PatchbayLauncher.compareVersions(firstVersion(a), firstVersion(b)));
        if (sorted.isEmpty()) return null;
        if (bitwigVersion == null) return sorted.get(sorted.size() - 1);
        String pick = sorted.get(0);
        for (String b : sorted) if (PatchbayLauncher.compareVersions(firstVersion(b), bitwigVersion) <= 0) pick = b;
        return pick;
    }

    /** Items for the chosen build: those in it and those that serve every version. */
    static List<Item> visible(List<Item> all, String build) {
        return all.stream().filter(i -> i.build().isEmpty() || i.build().equals(build)).toList();
    }

    /** The installed Bitwig Studio's version, or null when it can't be read. */
    static String bitwigVersion() { return Platform.bitwigVersion(); }

    // ---------- files ----------

    static Path root() { return PatchbayLauncher.data().resolve("devices"); }
    static Path folderOf(Item item) {
        return root().resolve(item.repo().replace('/', '_')).resolve(item.build().isEmpty() ? "files" : item.build());
    }

    /** The hash git gives a file's content (SHA-1 over "blob <length>\0" and the bytes), as the tree API lists it. */
    static String gitBlobSha(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest d = MessageDigest.getInstance("SHA-1");
            d.update(("blob " + Files.size(file) + "\0").getBytes(StandardCharsets.US_ASCII));
            byte[] buf = new byte[1 << 16];
            for (int n; (n = in.read(buf)) >= 0; ) d.update(buf, 0, n);
            return HexFormat.of().formatHex(d.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    /** A switched-off device keeps its file under this extra ending, so Bitwig doesn't take it for a device. */
    static final String OFF = ".off";

    /** 2 = not downloaded, 0 = downloaded but the repository has another version, 1 = up to date, 3 = switched off. */
    static int rank(Item item) {
        Path file = folderOf(item).resolve(item.fileName());
        if (!Files.isRegularFile(file)) return Files.isRegularFile(offFile(item)) ? 3 : 2;
        if (isLeftover(item)) return 1; // no longer in its repository: nothing to compare with
        try {
            return gitBlobSha(file).equals(item.sha()) ? 1 : 0;
        } catch (IOException e) {
            return 0;
        }
    }

    static Path offFile(Item item) { return folderOf(item).resolve(item.fileName() + OFF); }

    /** Switches a downloaded device off (its file renamed, so Bitwig skips it) or back on. */
    static void setOn(Item item, boolean on) throws IOException {
        Path file = folderOf(item).resolve(item.fileName()), off = offFile(item);
        if (on) Files.move(off, file, StandardCopyOption.REPLACE_EXISTING);
        else Files.move(file, off, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Deletes a device's file (switched on or off); an emptied folder goes too. */
    static void remove(Item item) throws IOException {
        Files.deleteIfExists(folderOf(item).resolve(item.fileName()));
        Files.deleteIfExists(offFile(item));
        try (var left = Files.list(folderOf(item))) {
            if (left.findAny().isEmpty()) Files.delete(folderOf(item));
        } catch (NoSuchFileException gone) {
            // nothing left to tidy
        }
    }

    interface Fetcher { void fetch(Item item, Path target) throws IOException; }

    /** Downloads one file into its folder; a file whose hash isn't the listed one is refused. */
    static void download(Item item, Fetcher fetcher) throws IOException {
        Path folder = folderOf(item);
        Files.createDirectories(folder);
        Path temp = folder.resolve("." + item.fileName() + ".download");
        try {
            fetcher.fetch(item, temp);
            if (!gitBlobSha(temp).equals(item.sha()))
                throw new IOException(item.name() + ": " + PatchbayLauncher.t(
                    "plik różni się od wersji z listy. Kliknij Odśwież i spróbuj ponownie.",
                    "the file differs from the listed version. Click Refresh and try again."));
            Files.move(temp, folder.resolve(item.fileName()), StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(offFile(item));
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** The launcher's folders that hold files for the items shown: the ones Patchbay should scan. */
    static List<Path> foldersFor(List<Item> shown) {
        LinkedHashSet<Path> out = new LinkedHashSet<>();
        for (Item i : shown) if (Files.isDirectory(folderOf(i))) out.add(folderOf(i));
        return new ArrayList<>(out);
    }

    // ---------- Patchbay's Device Folders list ----------

    /** Patchbay's per-user settings folder (macOS or Windows); the framework's folder list is a file in it. */
    static Path patchbaySettings() {
        String override = System.getProperty("launcher.bmlsettings");
        return override != null ? Path.of(override) : Platform.patchbaySettings();
    }
    static Path folderListFile() { return patchbaySettings().resolve("mods").resolve(FRAMEWORK_ID).resolve("config.json"); }

    /**
     * Makes the launcher's entries on Patchbay's Device Folders list exactly these folders (two
     * builds of a device share an ID, so only the chosen build may be listed). Folders the user
     * listed themselves and every other key in the file stay as they are. Returns true when the
     * file was changed.
     */
    static boolean register(List<Path> wanted) throws IOException {
        Path file = folderListFile();
        Map<String, Object> config;
        if (Files.isRegularFile(file)) {
            try {
                config = Mods.Json.object(Mods.Json.parse(Files.readString(file)));
            } catch (RuntimeException bad) {
                throw new IOException(PatchbayLauncher.t("Nie można odczytać listy folderów Patchbay: ", "Patchbay's folder list can't be read: ") + file);
            }
        } else {
            config = new LinkedHashMap<>();
            config.put("schema_version", 1.0);
        }
        Path ours = root().toAbsolutePath().normalize();
        List<Object> before = Mods.Json.array(config.get("folders")), after = new ArrayList<>();
        for (Object f : before) {
            if (f instanceof String s && Path.of(s).toAbsolutePath().normalize().startsWith(ours)) continue;
            after.add(f);
        }
        for (Path p : wanted) after.add(p.toAbsolutePath().normalize().toString());
        if (after.equals(before)) return false;
        config.put("folders", after);
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.writeString(temp, Mods.Json.write(config) + "\n");
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
        return true;
    }

    // ---------- the section ----------

    static final class Panel extends JPanel {
        private record Row(Item item, int rank) { }

        private final List<Row> rows = new ArrayList<>();
        private final Theme.TypeColours categories = new Theme.TypeColours();
        /** Keys of the devices marked with a star. */
        private final Set<String> favourites = SidePanel.favourites("favouriteDevices");
        private final SidePanel side = new SidePanel("devicesSide", key -> rebuild());
        private final Set<String> checked = new HashSet<>();
        private final AbstractTableModel model = new AbstractTableModel() {
            private final String[] columns = { "", "",
                PatchbayLauncher.t("Kategoria", "Category"), PatchbayLauncher.t("Urządzenie", "Device"), PatchbayLauncher.t("Stan", "Status") };
            @Override public int getRowCount() { return rows.size(); }
            @Override public int getColumnCount() { return columns.length; }
            @Override public String getColumnName(int c) { return columns[c]; }
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? Boolean.class : String.class; }
            @Override public boolean isCellEditable(int r, int c) { return c == 0 && !busy; }
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
                    case 1 -> favourites.contains(key(row.item()));
                    case 2 -> row.item().type();
                    case 3 -> row.item().name();
                    default -> pulledReason(row.item()) != null && row.rank() != 2 ? "withdrawn|" + PatchbayLauncher.t("wycofane", "withdrawn")
                        : isLeftover(row.item()) ? "removed|" + PatchbayLauncher.t("usunięte", "removed") : switch (row.rank()) {
                        case 0 -> "update|" + PatchbayLauncher.t("aktualizacja", "update");
                        case 1 -> "installed|" + PatchbayLauncher.t("pobrane", "downloaded");
                        case 3 -> "off|" + PatchbayLauncher.t("wyłączone", "off");
                        default -> "new|" + PatchbayLauncher.t("nowe", "new");
                    };
                };
            }
        };
        private final JTable table = new JTable(model);
        private final JComboBox<String> buildBox = new JComboBox<>();
        /** Sort: 0 by status (updates, new, downloaded), 1 by type then name, 2 by name. */
        private final JComboBox<String> sortBox = new JComboBox<>();
        private final Theme.HintField searchField = new Theme.HintField(PatchbayLauncher.t("Szukaj", "Search"));
        private final JLabel subtitle = Theme.label(" ", Theme.text(14f), Theme.MUTED);
        private final JPanel details = new Theme.Card(new BorderLayout(), Theme.FRAME);
        private final JButton checkAllButton = Theme.button(" ", "secondary");
        private final JButton installButton = Theme.button(" ", "primary");
        private final JButton switchButton = Theme.button(PatchbayLauncher.t("Wyłącz", "Switch off"), "secondary");
        private final JButton removeButton = Theme.button(PatchbayLauncher.t("Odinstaluj", "Uninstall"), "danger");
        private final JButton folderButton = Theme.button(PatchbayLauncher.t("Pokaż folder", "Show folder"), "secondary");
        private final JButton refreshButton = Theme.button(PatchbayLauncher.t("Odśwież", "Refresh"), "secondary");
        private final JLabel status = Theme.label(" ", Theme.text(14f), Theme.MUTED);
        private final JProgressBar bar = new JProgressBar();
        private final Map<String, BufferedImage> previews = new HashMap<>();
        private List<Item> items = List.of();
        /** Devices their author withdrew because of problems: "repo file" to the reason. */
        private Map<String, String> withdrawn = Map.of();
        private String bitwig;
        private boolean busy, loaded, filling;
        private String shownPreview;

        private static String key(Item i) { return i.repo() + "/" + i.path(); }

        Panel() {
            super(new BorderLayout());
            Theme.styleTable(table);
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.getColumnModel().getColumn(0).setMinWidth(40);
            table.getColumnModel().getColumn(0).setMaxWidth(40);
            table.getColumnModel().getColumn(1).setMinWidth(30);
            table.getColumnModel().getColumn(1).setMaxWidth(30);
            table.getColumnModel().getColumn(1).setCellRenderer(Theme.starCells());
            table.getColumnModel().getColumn(3).setPreferredWidth(260);
            table.getColumnModel().getColumn(4).setMinWidth(130);
            table.getColumnModel().getColumn(4).setMaxWidth(140);
            Theme.typeColumn(table.getColumnModel().getColumn(2), categories::colour);
            table.getColumnModel().getColumn(2).setMinWidth(124); // "Note generator" whole
            table.getColumnModel().getColumn(4).setCellRenderer(Theme.pillCells());
            SidePanel.starClicks(table, 1, r -> key(rows.get(r).item()), favourites, "favouriteDevices", this::rebuild);
            table.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) { showSelected(); refreshButtons(); } });

            details.setBorder(new EmptyBorder(18, 18, 18, 18));

            buildBox.setPreferredSize(new Dimension(160, 32));
            buildBox.setFont(Theme.mono(13f));
            buildBox.getAccessibleContext().setAccessibleName(PatchbayLauncher.t("Wersja plików", "Build"));
            buildBox.setToolTipText(PatchbayLauncher.t("Wersja plików", "Build"));
            sortBox.getAccessibleContext().setAccessibleName(PatchbayLauncher.t("Sortuj", "Sort"));
            sortBox.setToolTipText(PatchbayLauncher.t("Sortuj", "Sort"));
            searchField.setPreferredSize(new Dimension(140, 32));
            searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { if (loaded) rebuild(); }
                @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { if (loaded) rebuild(); }
                @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { }
            });
            JPanel tools = Theme.row(8, buildBox, sortBox, searchField);
            for (String o : new String[] { PatchbayLauncher.t("Stan", "Status"), PatchbayLauncher.t("Kategoria", "Category"), PatchbayLauncher.t("Nazwa", "Name") })
                sortBox.addItem(o);
            try { sortBox.setSelectedIndex(Integer.parseInt(String.valueOf(I18n.setting("devicesSort")))); } catch (RuntimeException none) { sortBox.setSelectedIndex(0); }
            sortBox.addActionListener(e -> { I18n.remember("devicesSort", String.valueOf(sortBox.getSelectedIndex())); if (loaded) rebuild(); });
            sortBox.setPreferredSize(new Dimension(140, 32));
            bar.setPreferredSize(new Dimension(10, 4));
            bar.setVisible(false);
            JPanel left = new JPanel(new BorderLayout(0, 6));
            left.setOpaque(false);
            left.add(status, BorderLayout.CENTER);
            left.add(bar, BorderLayout.SOUTH);
            JComponent body = Theme.split(tools, checkAllButton, SidePanel.beside(side, Theme.framed(table)), null, refreshButton, details,
                left, Theme.row(8, switchButton, removeButton), Theme.row(8, folderButton, installButton));
            add(Theme.listPage(PatchbayLauncher.t("Urządzenia", "Devices"), subtitle, null, body, null, null), BorderLayout.CENTER);

            buildBox.addActionListener(e -> { if (!filling) rebuild(); });
            checkAllButton.addActionListener(e -> checkAll());
            installButton.addActionListener(e -> installChecked());
            switchButton.addActionListener(e -> {
                List<Row> mine = actedRows().stream().filter(r -> r.rank() != 2).toList();
                switchDevices(mine.stream().map(Row::item).toList(), mine.stream().allMatch(r -> r.rank() == 3));
            });
            removeButton.addActionListener(e -> removeDevices(actedRows().stream().filter(r -> r.rank() != 2).map(Row::item).toList()));
            refreshButton.addActionListener(e -> reload());
            folderButton.addActionListener(e -> {
                try { Files.createDirectories(root()); } catch (Exception ignored) { }
                Platform.open(root().toString());
            });
            showSelected();
            refreshButtons();
        }

        private String build() { return (String) buildBox.getSelectedItem(); }

        /** Loads the list the first time the section is shown. */
        void shown() {
            if (!loaded) reload();
        }

        /** Loads again after the sources changed (only when it was loaded before). */
        void sourcesChanged() {
            if (loaded) reload();
        }

        void reload() {
            if (busy) return;
            loaded = true;
            setBusy(true, PatchbayLauncher.t("Pobieranie listy urządzeń…", "Loading the device list…"));
            new Thread(() -> {
                try {
                    List<Item> listed = fetch();
                    List<Item> found = new ArrayList<>(listed);
                    found.addAll(leftovers(listed));
                    Map<String, String> pulled = withdrawn();
                    String detected = bitwigVersion();
                    SwingUtilities.invokeLater(() -> {
                        items = found;
                        withdrawn = pulled;
                        bitwig = detected;
                        TreeSet<String> builds = new TreeSet<>();
                        for (Item i : found) if (!i.build().isEmpty()) builds.add(i.build());
                        String keep = build();
                        filling = true;
                        buildBox.removeAllItems();
                        for (String b : builds) buildBox.addItem(b);
                        if (!builds.isEmpty()) buildBox.setSelectedItem(keep != null && builds.contains(keep) ? keep : pickBuild(builds, detected));
                        buildBox.setVisible(!builds.isEmpty());
                        filling = false;
                        setBusy(false, "");
                        rebuild();
                    });
                } catch (IOException | RuntimeException ex) {
                    SwingUtilities.invokeLater(() -> setBusy(false, String.valueOf(ex.getMessage())));
                }
            }, "devices-list").start();
        }

        /** The list's order for a Sort choice. */
        private static Comparator<Row> order(int sort) {
            Comparator<Row> byName = Comparator.comparing(r -> r.item().name(), String.CASE_INSENSITIVE_ORDER);
            return switch (sort) {
                case 1 -> Comparator.comparing((Row r) -> r.item().type(), String.CASE_INSENSITIVE_ORDER).thenComparing(byName);
                case 2 -> byName;
                default -> Comparator.comparingInt(Row::rank).thenComparing(byName);
            };
        }

        /** Rebuilds the rows for the chosen build; hashing the downloaded files happens off the event thread. */
        private void rebuild() {
            String typed = searchField.getText();
            List<Item> found = visible(items, build()).stream().filter(i -> Mods.matches(typed, i.name(), i.type(), i.repo().substring(0, i.repo().indexOf('/')))).toList();
            categories.reset(items.stream().map(Item::type));
            showSide(found);
            String part = side.selected();
            List<Item> shown = found.stream().filter(i -> part.equals(SidePanel.ALL)
                || part.equals(SidePanel.FAVOURITES) && favourites.contains(key(i)) || part.equals(SidePanel.categoryKey(i.type()))).toList();
            int sort = sortBox.getSelectedIndex();
            new Thread(() -> {
                List<Row> fresh = new ArrayList<>();
                for (Item i : shown) {
                    int rank = rank(i);
                    boolean pulled = withdrawn.containsKey(i.repo() + " " + i.fileName());
                    if (pulled && rank == 2) continue; // withdrawn by its author: not offered
                    fresh.add(new Row(i, pulled && rank == 0 ? 1 : rank));
                }
                fresh.sort(order(sort));
                SwingUtilities.invokeLater(() -> {
                    int was = table.getSelectedRow();
                    String keep = was >= 0 && was < rows.size() ? key(rows.get(was).item()) : null;
                    rows.clear();
                    rows.addAll(fresh);
                    Set<String> canTick = new HashSet<>();
                    for (Row r : rows) canTick.add(key(r.item()));
                    checked.retainAll(canTick);
                    model.fireTableDataChanged();
                    long have = rows.stream().filter(r -> r.rank() != 2).count();
                    subtitle.setText(PatchbayLauncher.t("Na liście: ", "Listed: ") + rows.size()
                        + " · " + PatchbayLauncher.t("pobrane: ", "downloaded: ") + have
                        + " · " + (bitwig == null ? PatchbayLauncher.t("nie wykryto Bitwiga", "Bitwig not detected")
                            : PatchbayLauncher.t("wykryty Bitwig ", "detected Bitwig ") + bitwig));
                    refreshButtons();
                    SwingUtilities.invokeLater(this::askAboutGone);
                    String select = System.getProperty("launcher.select"); // offline test: show this device's details
                    int pick = rows.isEmpty() ? -1 : 0;
                    for (int r = 0; select != null && r < rows.size(); r++) if (rows.get(r).item().name().equals(select)) pick = r;
                    for (int r = 0; keep != null && r < rows.size(); r++) if (key(rows.get(r).item()).equals(keep)) pick = r; // the same device stays highlighted
                    if (pick >= 0 && (keep != null || table.getSelectedRow() < 0)) table.setRowSelectionInterval(pick, pick);
                    showSelected();
                });
            }, "devices-rows").start();
        }

        /** Rows Download takes and Tick all ticks: new ones and ones with another version (not up to date or switched off). */
        private static boolean downloadable(Row r) { return r.rank() == 0 || r.rank() == 2; }
        /** The side panel: the device types in the list (by name), with their icons and counts. */
        private void showSide(List<Item> found) {
            Map<String, Integer> count = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (Item i : found) count.merge(i.type(), 1, Integer::sum);
            List<SidePanel.Entry> parts = new ArrayList<>();
            for (Map.Entry<String, Integer> e : count.entrySet())
                parts.add(new SidePanel.Entry(SidePanel.categoryKey(e.getKey()), e.getKey(), Categories.icon(Categories.deviceIcon(e.getKey()), categories.colour(e.getKey()), 20), e.getValue()));
            side.show(SidePanel.top(found.size(), (int) found.stream().filter(i -> favourites.contains(key(i))).count()), parts);
        }

        /** Rows Tick all ticks: the ones to download; when the list shown has none, every downloaded one, to switch off or uninstall them together. */
        private List<Row> checkable() {
            List<Row> get = rows.stream().filter(Panel::downloadable).toList();
            return get.isEmpty() ? rows : get;
        }
        /** The rows Switch off and Uninstall under the list act on: the ticked ones, or the highlighted one when none is ticked. */
        private List<Row> actedRows() {
            List<Row> ticked = checkedRows();
            if (!ticked.isEmpty()) return ticked;
            int r = table.getSelectedRow();
            return r >= 0 && r < rows.size() ? List.of(rows.get(r)) : List.of();
        }

        private List<Row> checkedRows() { return rows.stream().filter(r -> checked.contains(key(r.item()))).toList(); }

        private void checkAll() {
            List<Row> all = checkable();
            boolean allTicked = !all.isEmpty() && all.stream().allMatch(r -> checked.contains(key(r.item())));
            checked.clear();
            if (!allTicked) for (Row r : all) checked.add(key(r.item()));
            model.fireTableDataChanged();
            refreshButtons();
        }

        private void refreshButtons() {
            List<Row> all = checkable();
            List<Row> ticked = checkedRows();
            int toGet = (int) ticked.stream().filter(Panel::downloadable).count();
            List<Row> mine = actedRows().stream().filter(r -> r.rank() != 2).toList();
            boolean allTicked = !all.isEmpty() && all.stream().allMatch(r -> checked.contains(key(r.item())));
            checkAllButton.setText(allTicked ? PatchbayLauncher.t("Odznacz wszystkie", "Untick all") : PatchbayLauncher.t("Zaznacz wszystkie", "Tick all"));
            checkAllButton.setEnabled(!busy && !all.isEmpty());
            installButton.setText(PatchbayLauncher.t("Pobierz zaznaczone (", "Download ticked (") + toGet + ")");
            installButton.setEnabled(!busy && toGet > 0);
            boolean allOff = !mine.isEmpty() && mine.stream().allMatch(r -> r.rank() == 3);
            switchButton.setText(allOff ? PatchbayLauncher.t("Włącz", "Switch on") : PatchbayLauncher.t("Wyłącz", "Switch off"));
            switchButton.setEnabled(!busy && !mine.isEmpty());
            removeButton.setEnabled(!busy && !mine.isEmpty());
            refreshButton.setEnabled(!busy);
            buildBox.setEnabled(!busy && buildBox.getItemCount() > 0);
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
                col.add(Theme.wrapped(PatchbayLauncher.t("Kliknij urządzenie na liście, żeby zobaczyć podgląd i opis.", "Click a device in the list to see its panel and description."), 226, Theme.MUTED, 13f));
                details.add(col, BorderLayout.NORTH);
                details.revalidate();
                details.repaint();
                return;
            }
            Item item = rows.get(r).item();
            Theme.Picture preview = new Theme.Picture(282, 280);
            preview.setAlignmentX(LEFT_ALIGNMENT);
            preview.getAccessibleContext().setAccessibleName(PatchbayLauncher.t("Podgląd panelu: ", "Panel preview: ") + item.name());
            col.add(preview);
            col.add(Box.createVerticalStrut(14));
            JLabel type = Theme.label(item.type(), Theme.text(13f), categories.colour(item.type()));
            type.setIcon(Categories.icon(Categories.deviceIcon(item.type()), categories.colour(item.type()), 16));
            type.setIconTextGap(6);
            type.setAlignmentX(LEFT_ALIGNMENT);
            col.add(type);
            col.add(Box.createVerticalStrut(4));
            JLabel name = Theme.label(item.name(), Theme.bold(20f), Theme.TEXT);
            name.setAlignmentX(LEFT_ALIGNMENT);
            col.add(name);
            col.add(Box.createVerticalStrut(10));
            String why = pulledReason(item);
            if (why != null || isLeftover(item)) {
                col.add(Theme.goneNote(why != null, why, PatchbayLauncher.t("To urządzenie", "This device"), 210));
                col.add(Box.createVerticalStrut(12));
            }
            if (!item.description().isEmpty()) {
                JLabel d = Theme.wrapped(item.description(), 226, Theme.SOFT, 14f);
                d.setAlignmentX(LEFT_ALIGNMENT);
                col.add(d);
                col.add(Box.createVerticalStrut(12));
            }
            JPanel facts = Theme.facts(new String[][] {
                { PatchbayLauncher.t("Autor", "Author"), item.repo().substring(0, item.repo().indexOf('/')) },
                { PatchbayLauncher.t("Rozmiar", "Size"), Math.max(1, item.size() / 1024) + " KB" },
                { PatchbayLauncher.t("Wersja plików", "Build"), item.build().isEmpty() ? PatchbayLauncher.t("wszystkie", "all") : item.build(), "mono" },
                { PatchbayLauncher.t("Źródło", "Source"), item.repo() } });
            facts.setAlignmentX(LEFT_ALIGNMENT);
            facts.setMaximumSize(new Dimension(Integer.MAX_VALUE, facts.getPreferredSize().height));
            col.add(facts);
            details.add(Theme.scrolling(col), BorderLayout.CENTER); // a tall preview or a short window: the details scroll, the buttons stay
            JButton page = Theme.button(PatchbayLauncher.t("Strona projektu", "Project page"), "secondary");
            page.addActionListener(e -> {
                Platform.open("https://github.com/" + item.repo());
            });
            JPanel south = new JPanel(new GridLayout(0, 1, 0, 8));
            south.setOpaque(false);
            south.setBorder(new EmptyBorder(12, 0, 0, 0));
            int rank = rows.get(r).rank();
            if (rank != 2) {
                boolean on = rank != 3;
                JButton toggle = Theme.button(on ? PatchbayLauncher.t("Wyłącz", "Switch off") : PatchbayLauncher.t("Włącz", "Switch on"), "secondary");
                toggle.setToolTipText(on ? PatchbayLauncher.t("Bitwig nie pokaże urządzenia; plik zostaje na dysku.", "Bitwig won't show the device; its file stays on disk.")
                    : PatchbayLauncher.t("Bitwig znów pokaże urządzenie.", "Bitwig shows the device again."));
                toggle.setEnabled(!busy);
                toggle.addActionListener(e -> switchDevices(List.of(item), !on));
                JButton remove = Theme.button(PatchbayLauncher.t("Odinstaluj", "Uninstall"), "danger");
                remove.setEnabled(!busy);
                remove.addActionListener(e -> removeDevices(List.of(item)));
                south.add(toggle);
                south.add(remove);
            }
            south.add(page);
            details.add(south, BorderLayout.SOUTH);
            details.revalidate();
            details.repaint();

            String key = item.repo() + "/" + item.previewPath();
            shownPreview = key;
            BufferedImage have = previews.get(key);
            if (have != null) { preview.setImage(have); return; }
            new Thread(() -> {
                BufferedImage icon = loadPreview(item);
                SwingUtilities.invokeLater(() -> {
                    if (icon != null) previews.put(key, icon);
                    if (key.equals(shownPreview)) { preview.setImage(icon); details.revalidate(); }
                });
            }, "devices-preview").start();
        }

        private BufferedImage loadPreview(Item item) {
            try {
                BufferedImage img;
                if (fixtures() != null) {
                    Path f = fixtures().resolve(item.previewPath());
                    if (!Files.isRegularFile(f)) return null;
                    img = ImageIO.read(f.toFile());
                } else {
                    Path temp = Files.createTempFile("patchbay-preview", ".png");
                    try {
                        PatchbayLauncher.download(rawUrl(item.repo(), item.previewPath()), temp, null);
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

        /** True when Bitwig is closed; says so otherwise, since Bitwig holds the device files while it runs. */
        private boolean bitwigClosed() {
            if (!PatchbayLauncher.bitwigRunning()) return true;
            JOptionPane.showMessageDialog(this,
                PatchbayLauncher.t("Zamknij Bitwig Studio, a potem spróbuj ponownie.", "Quit Bitwig Studio, then try again."),
                "Patchbay Launcher", JOptionPane.WARNING_MESSAGE);
            return false;
        }

        /** "Name" for one device, "N devices: a, b, c" for several. */
        private static String named(List<Item> chosen) {
            if (chosen.size() == 1) return chosen.get(0).name();
            List<String> names = chosen.stream().map(Item::name).limit(8).toList();
            return chosen.size() + PatchbayLauncher.t(" urządzeń: ", " devices: ") + String.join(", ", names) + (chosen.size() > 8 ? ", …" : "");
        }

        /** Switches devices on or off; one already in that state is left as it is. */
        /** The reason its author withdrew the device, or null when it isn't withdrawn. */
        private String pulledReason(Item i) { return withdrawn.get(i.repo() + " " + i.fileName()); }

        /**
         * Once per device: downloaded, switched-on devices their author withdrew are offered to be switched
         * off (with the reason); devices removed from their repository get one notice.
         */
        private void askAboutGone() {
            Set<String> asked = new HashSet<>(List.of(String.valueOf(I18n.setting("devicesGoneSeen")).split(",")));
            List<Item> pulled = rows.stream().filter(r -> (r.rank() == 0 || r.rank() == 1) && pulledReason(r.item()) != null && !asked.contains(key(r.item()))).map(Row::item).toList();
            List<Item> fresh = rows.stream().filter(r -> isLeftover(r.item()) && pulledReason(r.item()) == null && !asked.contains(key(r.item()))).map(Row::item).toList();
            if (pulled.isEmpty() && fresh.isEmpty()) return;
            for (Item i : pulled) asked.add(key(i));
            for (Item i : fresh) asked.add(key(i));
            asked.remove("null");
            I18n.remember("devicesGoneSeen", String.join(",", asked));
            if (!fresh.isEmpty()) {
                String text = PatchbayLauncher.t("Usunięte z repozytorium (działają dalej, bez aktualizacji): ", "Removed from their repository (still work, no updates): ") + named(fresh);
                status.setText(text);
                PatchbayLauncher.notifyUser(text);
            }
            if (pulled.isEmpty()) return;
            StringBuilder why = new StringBuilder();
            for (Item i : pulled) why.append("\n• ").append(i.name()).append(pulledReason(i).isEmpty() ? "" : ": " + pulledReason(i));
            Object[] choice = { PatchbayLauncher.t("Wyłącz", "Switch off"), PatchbayLauncher.t("Zostaw", "Keep") };
            int pick = JOptionPane.showOptionDialog(this, Theme.message(PatchbayLauncher.t("Autor wycofał z powodu problemów:", "The author withdrew because of problems:") + why
                    + "\n\n" + PatchbayLauncher.t("Wyłączyć teraz? Pliki zostaną na dysku, możesz je potem włączyć albo odinstalować.",
                        "Switch off now? The files stay on disk; you can switch them on or uninstall them later.")),
                "Patchbay Launcher", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, choice, choice[0]);
            if (pick == 0) switchDevices(pulled, false);
        }

        private void switchDevices(List<Item> chosen, boolean on) {
            if (chosen.isEmpty() || !bitwigClosed()) return;
            try {
                for (Item item : chosen) if ((rank(item) == 3) == on) setOn(item, on);
                for (Item item : chosen) checked.remove(key(item));
                status.setText((on ? PatchbayLauncher.t("Włączono: ", "Switched on: ") : PatchbayLauncher.t("Wyłączono: ", "Switched off: ")) + named(chosen));
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, String.valueOf(ex.getMessage()), "Patchbay Launcher", JOptionPane.ERROR_MESSAGE);
            }
            rebuild();
        }

        private void removeDevices(List<Item> chosen) {
            if (chosen.isEmpty() || !bitwigClosed()) return;
            String question = PatchbayLauncher.t("Odinstalować ", "Uninstall ") + named(chosen) + "?\n\n"
                + PatchbayLauncher.t("Pliki zostaną usunięte z dysku. Żeby przywrócić urządzenie, trzeba je pobrać ponownie. Żeby tylko go nie pokazywać, wybierz Wyłącz.",
                    "The files are deleted from disk. To bring a device back, download it again. To only hide it, choose Switch off.");
            String remove = PatchbayLauncher.t("Odinstaluj", "Uninstall"), cancel = PatchbayLauncher.t("Anuluj", "Cancel");
            if (JOptionPane.showOptionDialog(this, Theme.message(question), "Patchbay Launcher", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                    null, new String[] { remove, cancel }, cancel) != 0) return;
            try {
                for (Item item : chosen) Devices.remove(item);
                for (Item item : chosen) checked.remove(key(item));
                register(foldersFor(visible(items, build())));
                status.setText(PatchbayLauncher.t("Odinstalowano: ", "Uninstalled: ") + named(chosen));
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, String.valueOf(ex.getMessage()), "Patchbay Launcher", JOptionPane.ERROR_MESSAGE);
            }
            rebuild();
        }

        private void installChecked() {
            List<Item> picks = checkedRows().stream().filter(Panel::downloadable).map(Row::item).toList();
            if (picks.isEmpty()) return;
            if (!bitwigClosed()) return;
            List<Item> shown = visible(items, build());
            setBusy(true, "");
            new Thread(() -> {
                try {
                    for (Item item : picks) {
                        SwingUtilities.invokeLater(() -> status.setText(PatchbayLauncher.t("Pobieranie: ", "Downloading: ") + item.name()));
                        download(item, (i, target) -> PatchbayLauncher.download(rawUrl(i.repo(), i.path()), target, null));
                    }
                    register(foldersFor(shown));
                    SwingUtilities.invokeLater(() -> {
                        setBusy(false, PatchbayLauncher.t(
                            "Gotowe. W Patchbay włącz „Framework: Nitro Devices” i kliknij Load.",
                            "Done. In Patchbay tick \"Framework: Nitro Devices\" and click Load."));
                        rebuild();
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        setBusy(false, "");
                        rebuild();
                        JOptionPane.showMessageDialog(this, String.valueOf(ex.getMessage()), "Patchbay Launcher", JOptionPane.ERROR_MESSAGE);
                    });
                }
            }, "devices-install").start();
        }
    }
}
