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
            } catch (IOException | RuntimeException ex) {
                failure = ex instanceof IOException io ? io : new IOException(s.repo() + ": " + ex.getMessage());
            }
        }
        if (all.isEmpty() && failure != null) throw failure;
        return all;
    }

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

    /** 2 = not downloaded, 0 = downloaded but the repository has another version, 1 = up to date. */
    static int rank(Item item) {
        Path file = folderOf(item).resolve(item.fileName());
        if (!Files.isRegularFile(file)) return 2;
        try {
            return gitBlobSha(file).equals(item.sha()) ? 1 : 0;
        } catch (IOException e) {
            return 0;
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
        private final Set<String> checked = new HashSet<>();
        private final AbstractTableModel model = new AbstractTableModel() {
            private final String[] columns = { "",
                PatchbayLauncher.t("Urządzenie", "Device"), PatchbayLauncher.t("Typ", "Type"), PatchbayLauncher.t("Stan", "Status") };
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
                    case 2 -> row.item().type();
                    default -> switch (row.rank()) {
                        case 0 -> "update|" + PatchbayLauncher.t("aktualizacja", "update");
                        case 1 -> "installed|" + PatchbayLauncher.t("pobrane", "downloaded");
                        default -> "new|" + PatchbayLauncher.t("nowe", "new");
                    };
                };
            }
        };
        private final JTable table = new JTable(model);
        private final JComboBox<String> buildBox = new JComboBox<>();
        /** Sort: 0 by status (updates, new, downloaded), 1 by type then name, 2 by name. */
        private final JComboBox<String> sortBox = new JComboBox<>();
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
            table.getColumnModel().getColumn(1).setPreferredWidth(260);
            table.getColumnModel().getColumn(2).setPreferredWidth(140);
            table.getColumnModel().getColumn(3).setMinWidth(130);
            table.getColumnModel().getColumn(3).setMaxWidth(140);
            table.getColumnModel().getColumn(3).setCellRenderer(Theme.pillCells());
            table.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) showSelected(); });

            details.setBorder(new EmptyBorder(18, 18, 18, 18));
            JComponent body = Theme.split(Theme.framed(table), details);

            buildBox.setPreferredSize(new Dimension(170, 32));
            buildBox.setFont(Theme.mono(13f));
            buildBox.getAccessibleContext().setAccessibleName(PatchbayLauncher.t("Wersja plików", "Build"));
            JPanel actions = Theme.row(8, Theme.label(PatchbayLauncher.t("Wersja plików", "Build"), Theme.text(13f), Theme.MUTED), buildBox,
                Box.createHorizontalStrut(6), Theme.label(PatchbayLauncher.t("Sortuj", "Sort"), Theme.text(13f), Theme.MUTED), sortBox, Box.createHorizontalStrut(6), checkAllButton);
            for (String o : new String[] { PatchbayLauncher.t("Stan", "Status"), PatchbayLauncher.t("Typ", "Type"), PatchbayLauncher.t("Nazwa", "Name") })
                sortBox.addItem(o);
            try { sortBox.setSelectedIndex(Integer.parseInt(String.valueOf(I18n.setting("devicesSort")))); } catch (RuntimeException none) { sortBox.setSelectedIndex(0); }
            sortBox.addActionListener(e -> { I18n.remember("devicesSort", String.valueOf(sortBox.getSelectedIndex())); if (loaded) rebuild(); });
            sortBox.getAccessibleContext().setAccessibleName(PatchbayLauncher.t("Sortuj", "Sort"));
            sortBox.setPreferredSize(new Dimension(150, 32));
            bar.setPreferredSize(new Dimension(10, 4));
            bar.setVisible(false);
            JPanel left = new JPanel(new BorderLayout(0, 6));
            left.setOpaque(false);
            left.add(status, BorderLayout.CENTER);
            left.add(bar, BorderLayout.SOUTH);
            add(Theme.listPage(PatchbayLauncher.t("Urządzenia", "Devices"), subtitle, actions, refreshButton, body, left, Theme.row(8, folderButton, installButton)), BorderLayout.CENTER);

            buildBox.addActionListener(e -> { if (!filling) rebuild(); });
            checkAllButton.addActionListener(e -> checkAll());
            installButton.addActionListener(e -> installChecked());
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
                    List<Item> found = fetch();
                    String detected = bitwigVersion();
                    SwingUtilities.invokeLater(() -> {
                        items = found;
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
            List<Item> shown = visible(items, build());
            int sort = sortBox.getSelectedIndex();
            new Thread(() -> {
                List<Row> fresh = new ArrayList<>();
                for (Item i : shown) fresh.add(new Row(i, rank(i)));
                fresh.sort(order(sort));
                SwingUtilities.invokeLater(() -> {
                    rows.clear();
                    rows.addAll(fresh);
                    Set<String> canTick = new HashSet<>();
                    for (Row r : rows) if (r.rank() != 1) canTick.add(key(r.item()));
                    checked.retainAll(canTick);
                    model.fireTableDataChanged();
                    long have = rows.stream().filter(r -> r.rank() != 2).count();
                    subtitle.setText(PatchbayLauncher.t("Na liście: ", "Listed: ") + rows.size()
                        + " · " + PatchbayLauncher.t("pobrane: ", "downloaded: ") + have
                        + " · " + (bitwig == null ? PatchbayLauncher.t("nie wykryto Bitwiga", "Bitwig not detected")
                            : PatchbayLauncher.t("wykryty Bitwig ", "detected Bitwig ") + bitwig));
                    refreshButtons();
                    String select = System.getProperty("launcher.select"); // offline test: show this device's details
                    int pick = rows.isEmpty() ? -1 : 0;
                    for (int r = 0; select != null && r < rows.size(); r++) if (rows.get(r).item().name().equals(select)) pick = r;
                    if (pick >= 0 && table.getSelectedRow() < 0) table.setRowSelectionInterval(pick, pick);
                    showSelected();
                });
            }, "devices-rows").start();
        }

        private List<Row> checkable() { return rows.stream().filter(r -> r.rank() != 1).toList(); }
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
            int ticked = checkedRows().size();
            boolean allTicked = !all.isEmpty() && ticked == all.size();
            checkAllButton.setText(allTicked ? PatchbayLauncher.t("Odznacz wszystkie", "Untick all") : PatchbayLauncher.t("Zaznacz wszystkie", "Tick all"));
            checkAllButton.setEnabled(!busy && !all.isEmpty());
            installButton.setText(PatchbayLauncher.t("Pobierz zaznaczone (", "Download ticked (") + ticked + ")");
            installButton.setEnabled(!busy && ticked > 0);
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
            JLabel type = Theme.label(item.type(), Theme.text(13f), Theme.MUTED);
            type.setAlignmentX(LEFT_ALIGNMENT);
            col.add(type);
            col.add(Box.createVerticalStrut(4));
            JLabel name = Theme.label(item.name(), Theme.bold(20f), Theme.TEXT);
            name.setAlignmentX(LEFT_ALIGNMENT);
            col.add(name);
            col.add(Box.createVerticalStrut(10));
            if (!item.description().isEmpty()) {
                JLabel d = Theme.wrapped(item.description(), 226, Theme.SOFT, 14f);
                d.setAlignmentX(LEFT_ALIGNMENT);
                col.add(d);
                col.add(Box.createVerticalStrut(12));
            }
            JPanel facts = Theme.facts(new String[][] {
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
            details.add(page, BorderLayout.SOUTH);
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

        private void installChecked() {
            List<Item> picks = checkedRows().stream().map(Row::item).toList();
            if (picks.isEmpty()) return;
            if (PatchbayLauncher.bitwigRunning()) {
                JOptionPane.showMessageDialog(this,
                    PatchbayLauncher.t("Zamknij Bitwig Studio, a potem spróbuj ponownie.", "Quit Bitwig Studio, then try again."),
                    "Patchbay Launcher", JOptionPane.WARNING_MESSAGE);
                return;
            }
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
