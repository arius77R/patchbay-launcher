import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

/**
 * Patchbay Launcher: installs, updates and starts Patchbay Mod Loader (BML) on macOS without a
 * Terminal window. Everything it manages lives in ~/Library/Application Support/PatchbayLauncher.
 * Runs on the Java inside Bitwig Studio.app (java.base + java.desktop only).
 * Not affiliated with Patchbay's author or with Bitwig GmbH.
 */
public final class PatchbayLauncher {
    static final String VERSION = "0.9.2";
    /** The release list of the Patchbay source in use (the built-in one unless the user picked another). */
    static String api() { return "https://api.github.com/repos/" + Sources.patchbay().repo() + "/releases?per_page=100"; }
    static String t(String pl, String en) { return I18n.t(pl, en); }

    record Release(String tag, String version, String url) { }

    // ---------- paths ----------

    static Path data() {
        String override = System.getProperty("launcher.data");
        return override != null ? Path.of(override) : Platform.data();
    }
    static Path installs() { return data().resolve("installs"); }
    static Path folderOf(String version) { return installs().resolve("BML-" + version + "-" + Platform.suffix()); }
    static Path currentFile() { return data().resolve("current.txt"); }
    static Path logFile() { return data().resolve("logs").resolve("patchbay.log"); }

    static final Pattern INSTALL_DIR = Pattern.compile("BML-(.+)-" + Platform.suffix());

    static List<String> installedVersions() {
        List<String> out = new ArrayList<>();
        if (!Files.isDirectory(installs())) return out;
        try (Stream<Path> s = Files.list(installs())) {
            for (Path p : s.toList()) {
                Matcher m = INSTALL_DIR.matcher(p.getFileName().toString());
                if (m.matches() && Files.isRegularFile(p.resolve(Platform.startScript()))) out.add(m.group(1));
            }
        } catch (IOException ignored) { }
        out.sort((a, b) -> compareVersions(b, a));
        return out;
    }

    /** The version Patchbay starts with: the one named in current.txt, else the newest installed. */
    static String currentVersion() {
        List<String> installed = installedVersions();
        try {
            if (Files.isRegularFile(currentFile())) {
                String v = Files.readString(currentFile()).strip();
                if (installed.contains(v)) return v;
            }
        } catch (IOException ignored) { }
        return installed.isEmpty() ? null : installed.get(0);
    }

    static void setCurrent(String version) throws IOException {
        Files.createDirectories(data());
        Files.writeString(currentFile(), version + "\n");
    }

    // ---------- versions ----------

    /** Orders versions such as 0.36.0-dev.9, 0.36.0-dev.14, 1.0.0; build metadata (+...) is ignored. */
    static int compareVersions(String a, String b) {
        String[] x = split(a), y = split(b);
        int c = compareNumbers(x[0], y[0]);
        if (c != 0) return c;
        boolean xp = !x[1].isEmpty(), yp = !y[1].isEmpty();
        if (xp != yp) return xp ? -1 : 1; // a pre-release is older than its release
        return compareNumbers(x[1], y[1]);
    }
    private static String[] split(String v) {
        int plus = v.indexOf('+');
        if (plus >= 0) v = v.substring(0, plus);
        int dash = v.indexOf('-');
        return dash < 0 ? new String[] { v, "" } : new String[] { v.substring(0, dash), v.substring(dash + 1) };
    }
    private static int compareNumbers(String a, String b) {
        List<Long> x = numbers(a), y = numbers(b);
        for (int i = 0; i < Math.max(x.size(), y.size()); i++) {
            long p = i < x.size() ? x.get(i) : 0, q = i < y.size() ? y.get(i) : 0;
            if (p != q) return p < q ? -1 : 1;
        }
        return 0;
    }
    private static List<Long> numbers(String s) {
        List<Long> out = new ArrayList<>();
        Matcher m = Pattern.compile("\\d+").matcher(s);
        while (m.find()) out.add(Long.parseLong(m.group()));
        return out;
    }

    // ---------- releases ----------

    /** The macOS downloads in GitHub's release list, newest version first. */
    static List<Release> parseReleases(String json) {
        return parseReleases(json, Sources.glob(Platform.releasePattern()));
    }

    /** Assets whose name matches the pattern; the pattern's first * is the version (the tag when it has none). */
    static List<Release> parseReleases(String json, Pattern file) {
        List<Release> out = new ArrayList<>();
        if (file == null) return out;
        for (Object r : Mods.Json.array(Mods.Json.parse(json))) {
            Map<String, Object> release = Mods.Json.object(r);
            if (Boolean.TRUE.equals(release.get("draft"))) continue;
            String tag = Mods.Json.text(release, "tag_name");
            for (Object a : Mods.Json.array(release.get("assets"))) {
                Map<String, Object> asset = Mods.Json.object(a);
                String url = Mods.Json.text(asset, "browser_download_url");
                if (url == null || !url.startsWith("https://")) continue;
                String name = Mods.Json.text(asset, "name");
                if (name == null) name = java.net.URLDecoder.decode(url.substring(url.lastIndexOf('/') + 1), StandardCharsets.UTF_8);
                Matcher m = file.matcher(name);
                if (!m.matches()) continue;
                String version = m.groupCount() > 0 ? m.group(1) : tag;
                if (version == null) continue;
                out.add(new Release(tag, version, url));
            }
        }
        out.sort((a, b) -> compareVersions(b.version(), a.version()));
        return out;
    }

    static List<Release> fetchReleases() throws IOException {
        Sources.Source src = Sources.patchbay();
        List<Release> list = parseReleases(httpText(api()), Sources.glob(src.filter()));
        if (list.isEmpty()) throw new IOException(t("Na liście wydań nie ma pliku dla macOS.", "No macOS download in the release list."));
        return list;
    }

    /** GitHub said no because of its request limit; retrying now will not help. */
    static final class RateLimited extends IOException {
        RateLimited() {
            super(t("GitHub chwilowo ogranicza zapytania z tego adresu. Spróbuj za godzinę.",
                "GitHub is rate-limiting this address. Try again in an hour."));
        }
    }

    /**
     * The body of a GitHub answer as text. Each answer is kept in the cache with its ETag: the next
     * request asks only whether it changed (a "not modified" answer does not count against GitHub's
     * hourly limit), and when GitHub can't be reached or is rate-limiting, the last good answer is used.
     * A failed request is tried once more before that, since a first failure often clears.
     */
    static String httpText(String url) throws IOException {
        Path dir = data().resolve("cache").resolve("http");
        String key = cacheKey(url);
        Path body = dir.resolve(key + ".txt"), tag = dir.resolve(key + ".etag");
        IOException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return fetchText(url, body, tag);
            } catch (RateLimited limited) {
                last = limited;
                break;
            } catch (IOException ex) {
                last = ex;
                try { Thread.sleep(1500); } catch (InterruptedException stop) { Thread.currentThread().interrupt(); break; }
            }
        }
        if (Files.isRegularFile(body)) return Files.readString(body);
        throw last;
    }

    private static String cacheKey(String url) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(d, 0, 12);
        } catch (java.security.NoSuchAlgorithmException never) {
            return Integer.toHexString(url.hashCode());
        }
    }

    private static String fetchText(String url, Path body, Path tag) throws IOException {
        HttpURLConnection c = open(url);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        if (Files.isRegularFile(body) && Files.isRegularFile(tag)) c.setRequestProperty("If-None-Match", Files.readString(tag).trim());
        try {
            int status = c.getResponseCode();
            if (status == 304) return Files.readString(body);
            if (status == 403 || status == 429) throw new RateLimited();
            if (status != 200) throw new IOException("GitHub: HTTP " + status + " (" + url + ")");
            String text;
            try (InputStream in = c.getInputStream()) {
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            try {
                Files.createDirectories(body.getParent());
                Files.writeString(body, text);
                String etag = c.getHeaderField("ETag");
                if (etag != null) Files.writeString(tag, etag);
                else Files.deleteIfExists(tag);
            } catch (IOException cacheFull) { /* the answer is still good without the cache */ }
            return text;
        } finally { c.disconnect(); }
    }

    private static HttpURLConnection open(String url) throws IOException {
        if (!url.startsWith("https://")) throw new IOException("only https: " + url);
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(20_000);
        c.setReadTimeout(60_000);
        c.setRequestProperty("User-Agent", "PatchbayLauncher/" + VERSION);
        return c;
    }

    /** Downloads url to target through target.part; progress gets (bytes so far, total or -1). */
    static void download(String url, Path target, BiConsumer<Long, Long> progress) throws IOException {
        Files.createDirectories(target.getParent());
        Path part = target.resolveSibling(target.getFileName() + ".part");
        HttpURLConnection c = open(url);
        try {
            int status = c.getResponseCode();
            if (status != 200) throw new IOException(t("Pobieranie nie powiodło się: HTTP ", "Download failed: HTTP ") + status);
            long total = c.getContentLengthLong(), done = 0;
            try (InputStream in = c.getInputStream(); OutputStream out = Files.newOutputStream(part)) {
                byte[] buf = new byte[1 << 16];
                for (int n; (n = in.read(buf)) >= 0; ) {
                    out.write(buf, 0, n);
                    done += n;
                    if (progress != null) progress.accept(done, total);
                }
            }
            if (total > 0 && done != total)
                throw new IOException(t("Pobrany plik jest niepełny.", "The download is incomplete."));
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            c.disconnect();
            Files.deleteIfExists(part);
        }
    }

    // ---------- install ----------

    /**
     * Unpacks a Patchbay ZIP into installs/BML-<version>-<system>, copies the user's mods and themes
     * from the version in use, and makes the new one current. The old folder is kept.
     */
    static void installZip(Path zip, String version, Consumer<String> status) throws IOException, InterruptedException {
        Path target = folderOf(version);
        String previous = currentVersion();
        Files.createDirectories(installs());
        if (!Files.isRegularFile(target.resolve(Platform.startScript()))) {
            status.accept(t("Rozpakowywanie…", "Unpacking…"));
            Path tmp = Files.createTempDirectory(installs(), ".unpack-");
            try {
                Platform.unzip(zip, tmp);
                Path unpacked = null;
                try (Stream<Path> s = Files.list(tmp)) {
                    for (Path p : s.toList()) if (Files.isRegularFile(p.resolve(Platform.startScript()))) unpacked = p;
                }
                if (unpacked == null) throw new IOException(t("W archiwum nie ma Patchbay dla tego systemu (brak ", "No Patchbay for this system in the archive (")
                    + Platform.startScript() + t(").", " missing)."));
                if (Files.exists(target)) deleteTree(target);
                Files.move(unpacked, target);
            } finally {
                deleteTree(tmp);
            }
        }
        if (previous != null && !previous.equals(version)) {
            status.accept(t("Przenoszenie modów i motywów…", "Carrying over mods and themes…"));
            carryOver(folderOf(previous), target);
        }
        setCurrent(version);
    }

    /** Copies mods and themes the new install doesn't have yet; nothing in the new install is replaced. */
    static int carryOver(Path from, Path to) throws IOException {
        int copied = 0;
        for (String folder : new String[] { "mods", "components" }) {
            Path src = from.resolve(folder);
            if (!Files.isDirectory(src)) continue;
            Files.createDirectories(to.resolve(folder));
            try (Stream<Path> s = Files.list(src)) {
                for (Path item : s.toList()) {
                    String name = item.getFileName().toString();
                    if (name.startsWith(".")) continue;
                    Path dest = to.resolve(folder).resolve(name);
                    if (Files.exists(dest)) continue;
                    copyTree(item, dest);
                    copied++;
                }
            }
        }
        return copied;
    }

    static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> s = Files.walk(from)) {
            for (Path p : s.toList()) {
                Path d = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(d);
                else Files.copy(p, d, StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
    }

    static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        if (!root.toAbsolutePath().normalize().startsWith(data().toAbsolutePath().normalize()))
            throw new IOException("refusing to delete outside the launcher's folder: " + root);
        try (Stream<Path> s = Files.walk(root)) {
            for (Path p : s.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
        }
    }

    // ---------- system (details in Platform) ----------

    static boolean bitwigRunning() { return Platform.bitwigRunning(); }
    static boolean patchbayRunning() { return Platform.patchbayRunning(); }
    static void notifyUser(String text) { Platform.notifyUser(text); }

    static void startPatchbay() throws IOException {
        String v = currentVersion();
        if (v == null) throw new IOException(t("Patchbay nie jest zainstalowany.", "Patchbay isn't installed."));
        Files.createDirectories(logFile().getParent());
        Platform.startPatchbay(folderOf(v), logFile());
    }

    // ---------- window ----------

    private final JFrame frame = new JFrame("Patchbay Launcher");
    private final JLabel installedLabel = Theme.label(" ", Theme.text(14f), Theme.MUTED);
    private final JLabel latestLabel = Theme.label(" ", Theme.bold(16f), Theme.TEXT);
    private final JLabel sourceLabel = Theme.label(" ", Theme.text(13f), Theme.MUTED);
    private final JLabel activeLabel = Theme.label("–", Theme.mono(13f), Theme.TEXT);
    private final JComboBox<String> versionBox = new JComboBox<>();
    private final JButton installButton = Theme.button(" ", "primary");
    private final JButton startButton = Theme.button(t("Uruchom Patchbay", "Start Patchbay"), "primary");
    private final JProgressBar bar = new JProgressBar(0, 1000);
    private final JLabel statusLabel = Theme.label(" ", Theme.text(13f), Theme.MUTED);
    private final JComboBox<String> languageBox = new JComboBox<>();
    private final JButton dockButton = Theme.button(Platform.pinLabel(), "secondary");
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final Map<String, JButton> nav = new LinkedHashMap<>();
    private final Mods.Panel modsPanel = new Mods.Panel(this::modUpdatesFound);
    private final Devices.Panel devicesPanel = new Devices.Panel();
    private final Sources.Panel sourcesPanel = new Sources.Panel(this::sourcesChanged);
    private List<Release> releases = List.of();
    private boolean busy;

    private void modUpdatesFound(int updates) {
        JButton b = nav.get("mods");
        if (b == null) return;
        b.putClientProperty("badge", updates);
        b.revalidate();
        b.repaint();
    }

    /** After the user changed the sources: every section loads again from the new set. */
    private void sourcesChanged() {
        checkReleases();
        modsPanel.reload();
        devicesPanel.sourcesChanged();
    }

    private void select(String key) {
        cards.show(content, key);
        for (Map.Entry<String, JButton> e : nav.entrySet()) {
            e.getValue().putClientProperty("current", e.getKey().equals(key));
            e.getValue().repaint();
        }
        if (key.equals("devices")) devicesPanel.shown();
    }

    /** The mark beside the name: a socket with two jacks and a cable, drawn in the accent. */
    private static final class Logo extends JComponent {
        Logo() { setPreferredSize(new Dimension(28, 28)); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Theme.ACCENT);
            g2.setStroke(new BasicStroke(2f));
            g2.drawRoundRect(2, 2, 24, 24, 12, 12);
            g2.fillOval(8, 8, 5, 5);
            g2.fillOval(16, 16, 5, 5);
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            java.awt.geom.Path2D cable = new java.awt.geom.Path2D.Float();
            cable.moveTo(10.5, 13);
            cable.curveTo(10.5, 19, 18.5, 12, 18.5, 18);
            g2.draw(cable);
            g2.dispose();
        }
    }

    /** The app's picture (the Patchbay character) at the given size; the drawn mark when the picture is missing. */
    static JComponent appIcon(int size) {
        java.net.URL url = PatchbayLauncher.class.getResource("/icon-128.png");
        if (url == null) return new Logo();
        ImageIcon raw = new ImageIcon(url);
        JLabel l = new JLabel(new ImageIcon(raw.getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH)));
        l.setPreferredSize(new Dimension(size, size));
        l.getAccessibleContext().setAccessibleName("Patchbay Launcher");
        return l;
    }

    private JComponent sidebar() {
        JPanel side = new JPanel();
        side.setBackground(Theme.TITLE);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.HAIR), new EmptyBorder(22, 14, 16, 14)));
        side.setPreferredSize(new Dimension(214, 10));
        JPanel brand = new JPanel(new BorderLayout(10, 0));
        brand.setOpaque(false);
        brand.setBorder(new EmptyBorder(0, 4, 20, 0));
        brand.add(appIcon(34), BorderLayout.WEST);
        JPanel names = new JPanel();
        names.setOpaque(false);
        names.setLayout(new BoxLayout(names, BoxLayout.Y_AXIS));
        names.add(Theme.label("Patchbay", Theme.bold(16f), Theme.TEXT));
        names.add(Theme.label("Launcher " + VERSION, Theme.text(12f), Theme.MUTED));
        brand.add(names, BorderLayout.CENTER);
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        brand.setMaximumSize(new Dimension(Integer.MAX_VALUE, brand.getPreferredSize().height));
        side.add(brand);
        String[][] items = { { "patchbay", "Patchbay" }, { "mods", t("Mody", "Mods") }, { "devices", t("Urządzenia", "Devices") }, { "sources", t("Źródła", "Sources") } };
        for (String[] it : items) {
            JButton b = Theme.button(it[1], "nav");
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
            b.addActionListener(e -> select(it[0]));
            nav.put(it[0], b);
            side.add(b);
            side.add(Box.createVerticalStrut(4));
        }
        side.add(Box.createVerticalGlue());
        JPanel active = new Theme.Card(new BorderLayout(0, 4), Theme.WELL);
        active.setBorder(new EmptyBorder(12, 12, 12, 12));
        active.add(Theme.label(t("Aktywny Patchbay", "Active Patchbay"), Theme.text(12f), Theme.MUTED), BorderLayout.NORTH);
        active.add(activeLabel, BorderLayout.CENTER);
        active.setAlignmentX(Component.LEFT_ALIGNMENT);
        active.setMaximumSize(new Dimension(Integer.MAX_VALUE, active.getPreferredSize().height));
        side.add(active);
        side.add(Box.createVerticalStrut(10));
        startButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        startButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        side.add(startButton);
        return side;
    }

    private JComponent patchbayPage() {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));

        JPanel versions = new Theme.Card(new BorderLayout(), Theme.FRAME);
        versions.setBorder(new EmptyBorder(18, 18, 18, 18));
        JPanel v = new JPanel();
        v.setOpaque(false);
        v.setLayout(new BoxLayout(v, BoxLayout.Y_AXIS));
        for (JComponent c : new JComponent[] { latestLabel, sourceLabel }) { c.setAlignmentX(Component.LEFT_ALIGNMENT); v.add(c); v.add(Box.createVerticalStrut(4)); }
        v.add(Box.createVerticalStrut(10));
        versionBox.setPreferredSize(new Dimension(240, 34));
        versionBox.setFont(Theme.mono(13f));
        versionBox.getAccessibleContext().setAccessibleName(t("Wersja Patchbay", "Patchbay version"));
        JPanel pick = Theme.row(10, versionBox, installButton);
        pick.setAlignmentX(Component.LEFT_ALIGNMENT);
        v.add(pick);
        v.add(Box.createVerticalStrut(14));
        bar.setPreferredSize(new Dimension(10, 6));
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        bar.setVisible(false);
        v.add(bar);
        v.add(Box.createVerticalStrut(6));
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        v.add(statusLabel);
        versions.add(v, BorderLayout.CENTER);
        versions.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(versions);
        col.add(Box.createVerticalStrut(16));

        JPanel settings = new Theme.Card(new BorderLayout(), Theme.FRAME);
        settings.setBorder(new EmptyBorder(18, 18, 18, 18));
        JPanel s = new JPanel();
        s.setOpaque(false);
        s.setLayout(new BoxLayout(s, BoxLayout.Y_AXIS));
        JLabel head = Theme.label(t("Ustawienia", "Settings"), Theme.bold(16f), Theme.TEXT);
        head.setAlignmentX(Component.LEFT_ALIGNMENT);
        s.add(head);
        s.add(Box.createVerticalStrut(14));
        languageBox.addItem(t("Język systemu", "System language"));
        for (String name : new String[] { "Polski", "English", "Deutsch", "Français" }) languageBox.addItem(name);
        languageBox.setSelectedIndex(I18n.CODES.indexOf(I18n.choice()) + 1);
        languageBox.addActionListener(e -> languageChosen()); // only after the list is filled: filling it fires events
        languageBox.setPreferredSize(new Dimension(200, 34));
        languageBox.getAccessibleContext().setAccessibleName(t("Język", "Language"));
        JButton folder = Theme.button(t("Pokaż folder z danymi", "Show the data folder"), "secondary");
        folder.addActionListener(e -> {
            try { Files.createDirectories(data()); Desktop.getDesktop().open(data().toFile()); } catch (Exception ignored) { }
        });
        JPanel lang = Theme.row(10, Theme.label(t("Język:", "Language:"), Theme.text(14f), Theme.MUTED), languageBox);
        lang.setAlignmentX(Component.LEFT_ALIGNMENT);
        s.add(lang);
        s.add(Box.createVerticalStrut(12));
        JPanel more = Theme.row(10, folder, dockButton);
        more.setAlignmentX(Component.LEFT_ALIGNMENT);
        s.add(more);
        settings.add(s, BorderLayout.CENTER);
        settings.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(settings);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(col, BorderLayout.NORTH);
        return Theme.page("Patchbay", installedLabel, null, top, null, null);
    }

    private void show() {
        dockButton.addActionListener(e -> addToDockClicked());
        dockButton.setVisible(false);
        new Thread(() -> {
            Platform.refreshIcon(VERSION);
            boolean offer = (Platform.pinBlocked() == null || (!Platform.WINDOWS && Platform.appBundle() != null)) && !Platform.pinned();
            SwingUtilities.invokeLater(() -> dockButton.setVisible(offer));
        }, "dock-check").start();
        installButton.addActionListener(e -> installSelected());
        startButton.addActionListener(e -> start());
        versionBox.addActionListener(e -> refreshButtons());

        content.setBackground(Theme.WELL);
        content.add(patchbayPage(), "patchbay");
        content.add(modsPanel, "mods");
        content.add(devicesPanel, "devices");
        content.add(sourcesPanel, "sources");
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.WELL);
        root.add(sidebar(), BorderLayout.WEST);
        root.add(content, BorderLayout.CENTER);
        frame.setContentPane(root);
        java.net.URL iconUrl = PatchbayLauncher.class.getResource("/icon-128.png");
        if (iconUrl != null) frame.setIconImage(new ImageIcon(iconUrl).getImage());
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.getRootPane().setDefaultButton(startButton);
        frame.setMinimumSize(new Dimension(980, 640));
        frame.setSize(1200, 760);
        frame.setLocationRelativeTo(null);
        refreshLocal();
        String tab = System.getProperty("launcher.tab", currentVersion() == null ? "patchbay" : "mods");
        select(nav.containsKey(tab) ? tab : "patchbay");
        frame.setVisible(true);
        checkReleases();
        modsPanel.reload();
    }

    private void addToDockClicked() {
        String blocked = Platform.pinBlocked();
        if (blocked != null) {
            if (!blocked.isEmpty())
                JOptionPane.showMessageDialog(frame, blocked, "Patchbay Launcher", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            if (!Platform.pinned()) Platform.pin();
            dockButton.setVisible(false);
            statusLabel.setText(Platform.pinnedMessage());
        } catch (IOException | InterruptedException ex) {
            JOptionPane.showMessageDialog(frame, Platform.pinFailedMessage(), "Patchbay Launcher", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** After the first install: asks once whether the launcher should go in the Dock (macOS) or the Start menu (Windows). */
    private void offerDock() {
        if (Platform.pinBlocked() != null || "true".equals(I18n.setting("dockAsked")) || Platform.pinned()) return;
        I18n.remember("dockAsked", "true");
        int answer = JOptionPane.showConfirmDialog(frame, Platform.pinQuestion(),
            "Patchbay Launcher", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer == JOptionPane.YES_OPTION) addToDockClicked();
    }

    /** Saves the pick and rebuilds the window in the new language. */
    private void languageChosen() {
        int i = languageBox.getSelectedIndex();
        String code = i <= 0 ? I18n.AUTO : I18n.CODES.get(i - 1);
        if (busy || code.equals(I18n.choice())) return;
        try {
            I18n.choose(code);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, String.valueOf(ex.getMessage()), "Patchbay Launcher", JOptionPane.ERROR_MESSAGE);
            return;
        }
        frame.dispose();
        new PatchbayLauncher().show();
    }

    private void refreshLocal() {
        String cur = currentVersion();
        installedLabel.setText(cur == null
            ? t("Patchbay nie jest jeszcze zainstalowany.", "Patchbay isn't installed yet.")
            : t("Zainstalowana wersja: ", "Installed version: ") + cur);
        activeLabel.setText(cur == null ? "–" : cur);
        sourceLabel.setText(t("Źródło: ", "Source: ") + Sources.patchbay().repo());
        refreshButtons();
    }

    private void refreshButtons() {
        String cur = currentVersion();
        String sel = (String) versionBox.getSelectedItem();
        startButton.setEnabled(!busy && cur != null);
        installButton.setEnabled(!busy && sel != null && !sel.equals(cur));
        versionBox.setEnabled(!busy && versionBox.getItemCount() > 0);
        languageBox.setEnabled(!busy);
        if (sel == null || cur == null) installButton.setText(t("Zainstaluj", "Install"));
        else if (sel.equals(cur)) installButton.setText(t("Zainstalowana", "Installed"));
        else if (compareVersions(sel, cur) > 0) installButton.setText(t("Aktualizuj", "Update"));
        else installButton.setText(t("Przełącz na tę wersję", "Switch to this version"));
    }

    private void setBusy(boolean b, String status) {
        busy = b;
        statusLabel.setText(status == null || status.isEmpty() ? " " : status);
        bar.setIndeterminate(false);
        bar.setVisible(b);
        if (!b) bar.setValue(0);
        refreshButtons();
    }

    private void checkReleases() {
        setBusy(true, t("Sprawdzanie aktualizacji…", "Checking for updates…"));
        bar.setIndeterminate(true);
        new Thread(() -> {
            try {
                List<Release> list = fetchReleases();
                SwingUtilities.invokeLater(() -> releasesLoaded(list));
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> {
                    latestLabel.setText(t("Nie udało się sprawdzić aktualizacji.", "Couldn't check for updates."));
                    versionBox.removeAllItems();
                    for (String v : installedVersions()) versionBox.addItem(v);
                    setBusy(false, ex.getMessage());
                });
            }
        }, "releases").start();
    }

    private void releasesLoaded(List<Release> list) {
        releases = list;
        refreshLocal();
        versionBox.removeAllItems();
        for (Release r : list) versionBox.addItem(r.version());
        String newest = list.get(0).version(), cur = currentVersion();
        versionBox.setSelectedItem(newest);
        if (cur == null) {
            latestLabel.setText(t("Najnowsza wersja: ", "Latest version: ") + newest);
        } else if (compareVersions(newest, cur) > 0) {
            latestLabel.setText(t("Dostępna aktualizacja: ", "Update available: ") + newest);
            notifyUser(t("Dostępna jest nowa wersja Patchbay: ", "A new Patchbay version is available: ") + newest);
        } else {
            latestLabel.setText(t("Masz najnowszą wersję.", "You have the latest version."));
        }
        setBusy(false, "");
    }

    private void installSelected() {
        String sel = (String) versionBox.getSelectedItem();
        if (sel == null) return;
        if (bitwigRunning() || patchbayRunning()) {
            JOptionPane.showMessageDialog(frame,
                t("Zamknij Bitwig Studio i okno Patchbay, a potem spróbuj ponownie.",
                  "Quit Bitwig Studio and the Patchbay window, then try again."),
                "Patchbay Launcher", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Release release = releases.stream().filter(r -> r.version().equals(sel)).findFirst().orElse(null);
        boolean alreadyUnpacked = Files.isRegularFile(folderOf(sel).resolve(Platform.startScript()));
        if (release == null && !alreadyUnpacked) return;
        setBusy(true, "");
        new Thread(() -> {
            try {
                Path zip = data().resolve("downloads").resolve("BML-" + sel + "-" + Platform.suffix() + ".zip");
                if (!alreadyUnpacked) {
                    download(release.url(), zip, (done, total) -> SwingUtilities.invokeLater(() -> {
                        if (total > 0) bar.setValue((int) (done * 1000 / total));
                        else bar.setIndeterminate(true);
                        statusLabel.setText(t("Pobieranie ", "Downloading ") + sel + ": " + done / 1_000_000 + " MB"
                            + (total > 0 ? " / " + total / 1_000_000 + " MB" : ""));
                    }));
                }
                SwingUtilities.invokeLater(() -> bar.setIndeterminate(true));
                installZip(zip, sel, s -> SwingUtilities.invokeLater(() -> statusLabel.setText(s)));
                Files.deleteIfExists(zip);
                SwingUtilities.invokeLater(() -> {
                    setBusy(false, t("Gotowe. Aktywna wersja: ", "Done. Active version: ") + sel);
                    refreshLocal();
                    releasesLoadedKeepStatus();
                    modsPanel.patchbayChanged();
                    offerDock();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    setBusy(false, "");
                    refreshLocal();
                    JOptionPane.showMessageDialog(frame, String.valueOf(ex.getMessage()), "Patchbay Launcher", JOptionPane.ERROR_MESSAGE);
                });
            }
        }, "install").start();
    }

    private void releasesLoadedKeepStatus() {
        if (releases.isEmpty()) return;
        String newest = releases.get(0).version(), cur = currentVersion();
        latestLabel.setText(cur != null && compareVersions(newest, cur) > 0
            ? t("Dostępna aktualizacja: ", "Update available: ") + newest
            : t("Masz najnowszą wersję.", "You have the latest version."));
    }

    private void start() {
        try {
            if (patchbayRunning()) {
                JOptionPane.showMessageDialog(frame, t("Patchbay jest już uruchomiony.", "Patchbay is already running."),
                    "Patchbay Launcher", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            startPatchbay();
            setBusy(true, t("Uruchamianie Patchbay…", "Starting Patchbay…"));
            javax.swing.Timer quit = new javax.swing.Timer(2500, e -> System.exit(0));
            quit.setRepeats(false);
            quit.start();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, String.valueOf(ex.getMessage()), "Patchbay Launcher", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--selftest")) { SelfTest.run(args); return; }
        if (!Platform.WINDOWS) {
            System.setProperty("apple.awt.application.name", "Patchbay Launcher");
            System.setProperty("apple.awt.application.appearance", "NSAppearanceNameDarkAqua");
        }
        String cur = currentVersion();
        if (cur != null) Theme.loadFonts(folderOf(cur).resolve("loader/app/bml-control.jar"));
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            new PatchbayLauncher().show();
        });
    }

    /** Offline checks (the 4th argument is a folder of fixtures: two release lists and two mod ZIPs): java -Dlauncher.data=<empty temp folder> -jar launcher.jar --selftest <BML zip> <its version>. */
    static final class SelfTest {
        static void check(boolean ok, String what) {
            System.out.println((ok ? "ok    " : "FAIL  ") + what);
            if (!ok) System.exit(1);
        }
        static void run(String[] args) throws Exception {
            check(compareVersions("0.36.0-dev.14", "0.36.0-dev.9") > 0, "dev.14 > dev.9");
            check(compareVersions("0.36.0-dev.9+bitwig-6.1.7", "0.36.0-dev.13") < 0, "dev.9+meta < dev.13");
            check(compareVersions("1.0.0", "1.0.0-dev.3") > 0, "1.0.0 > 1.0.0-dev.3");
            check(compareVersions("0.37.0-dev.1", "0.36.0-dev.16") > 0, "0.37.0-dev.1 > 0.36.0-dev.16");
            check(compareVersions("0.36.0-dev.14", "0.36.0-dev.14") == 0, "equal versions");
            String json = "[{\"tag_name\": \"0.36.0-dev.9+bitwig-6.1.7\", \"assets\": [{\"browser_download_url\": "
                + "\"https://github.com/o/r/releases/download/0.36.0-dev.9%2Bbitwig-6.1.7/BML-0.36.0-dev.9-linux.zip\"},"
                + "{\"browser_download_url\": \"https://github.com/o/r/releases/download/0.36.0-dev.9%2Bbitwig-6.1.7/BML-0.36.0-dev.9-macos.zip\"}]},"
                + "{\"tag_name\":\"0.36.0-dev.16\",\"assets\":[{\"browser_download_url\":\"https://github.com/o/r/releases/download/0.36.0-dev.16/BML-0.36.0-dev.16-macos.zip\"},"
                + "{\"browser_download_url\":\"https://github.com/o/r/releases/download/0.36.0-dev.16/BML-0.36.0-dev.16-windows.zip\"}]},"
                + "{\"tag_name\":\"no-mac\",\"assets\":[]}]";
            List<Release> list = parseReleases(json, Sources.glob("BML-*-macos.zip"));
            check(list.size() == 2, "two macOS releases parsed");
            check(list.get(0).version().equals("0.36.0-dev.16") && list.get(0).url().endsWith("BML-0.36.0-dev.16-macos.zip"), "newest first, right URL");
            check(list.get(1).version().equals("0.36.0-dev.9"), "version comes from the file name");
            Object parsed = Mods.Json.parse("{\"a\":[1,2,{\"b\":\"x\\n\\u0041\\\"\"}],\"t\":true,\"n\":null,\"e\":{},\"l\":[]}");
            check("x\nA\"".equals(Mods.Json.text(Mods.Json.object(Mods.Json.array(Mods.Json.object(parsed).get("a")).get(2)), "b")), "JSON parser reads nesting and escapes");
            check(Mods.ASSET.matcher("pre-5-style-side-browser-0.2.0-dev.1.zip").matches(), "asset pattern: id with a digit");
            check(!Mods.ASSET.matcher("all-mods-2026-11-15.zip").matches() && !Mods.ASSET.matcher("SHA256SUMS.txt").matches(), "asset pattern: bundles and sums skipped");
            check(Platform.dockEntry(Path.of("/Applications/Patchbay Launcher.app")).contains("<string>/Applications/Patchbay Launcher.app</string>"), "Dock entry holds the app's path");
            check(Platform.inApplications(Path.of("/Applications/Patchbay Launcher.app")) && !Platform.inApplications(Path.of("/Volumes/External/x/Patchbay Launcher.app")), "Dock offered only from Applications");
            check("6.1.4".equals(Platform.cleanVersion("6.1.4.0")) && "6.1.7".equals(Platform.cleanVersion(" 6.1.7 (Build 123)")) && Platform.cleanVersion("n/a") == null, "Bitwig version read from Windows and macOS forms");
            check(Platform.releasePattern().equals(Platform.WINDOWS ? "BML-*-windows.zip" : "BML-*-macos.zip"), "release file for this system: " + Platform.releasePattern());
            check(I18n.CODES.size() == 4 && I18n.needs("A", "b", "1.0", null).contains("b"), "four languages, requirement note built");
            if (args.length < 3) { System.out.println("SELFTEST_OK (logic only)"); return; }

            Path zip = Path.of(args[1]);
            String version = args[2];
            check(System.getProperty("launcher.data") != null, "launcher.data is set for the test");
            check(currentVersion() == null, "nothing installed at first");
            installZip(zip, version, s -> { });
            check(version.equals(currentVersion()), "installed version is current");
            Path ui = folderOf(version).resolve(Platform.startScript());
            check(Files.isRegularFile(ui) && (Platform.WINDOWS || Files.isExecutable(ui)), Platform.startScript() + " in place" + (Platform.WINDOWS ? "" : " and runnable"));
            check(Files.isRegularFile(folderOf(version).resolve("loader/app/bml-control.jar")), "control jar present");

            // a fake older install with a user mod and a theme
            String old = "0.0.1-dev.1";
            Path oldDir = folderOf(old);
            Files.createDirectories(oldDir.resolve("mods/my-mod"));
            Files.writeString(oldDir.resolve("mods/my-mod/mod.json"), "{}");
            Files.createDirectories(oldDir.resolve("mods/browser-framework"));
            Files.writeString(oldDir.resolve("mods/browser-framework/OLD"), "old");
            Files.createDirectories(oldDir.resolve("components"));
            Files.writeString(oldDir.resolve("components/theme.bte"), "t");
            Files.writeString(oldDir.resolve(Platform.startScript()), "#!/bin/sh\n");
            setCurrent(old);
            check(old.equals(currentVersion()), "switched to the older install");
            installZip(zip, version, s -> { });
            check(version.equals(currentVersion()), "update makes the new version current");
            check(Files.isRegularFile(folderOf(version).resolve("mods/my-mod/mod.json")), "user mod carried over");
            check(Files.isRegularFile(folderOf(version).resolve("components/theme.bte")), "theme carried over");
            check(!Files.exists(folderOf(version).resolve("mods/browser-framework/OLD")), "shipped framework not replaced by the old copy");
            check(Files.isDirectory(oldDir), "old install kept");
            check(installedVersions().get(0).equals(version), "newest listed first");
            if (args.length >= 4) modsTest(Path.of(args[3]), folderOf(version).resolve("mods"));
            if (args.length >= 4) devicesTest(Path.of(args[3]));
            sourcesTest();
            System.out.println("SELFTEST_OK");
        }

        static void devicesTest(Path fixtures) throws Exception {
            Map<String, String[]> about = Devices.parseReadme(Files.readString(fixtures.resolve("devices-readme.md")));
            check(about.size() == 12 && about.get("OTT")[0].equals("Audio FX") && about.get("FILAR")[1].startsWith("An additive"), "README table: 12 devices with type and text");
            String repo = "polarity/bitwig-devices";
            List<Devices.Item> all = Devices.parseTree(Files.readString(fixtures.resolve("devices-tree.json")), about, repo, "");
            Set<String> builds = new TreeSet<>();
            for (Devices.Item i : all) builds.add(i.build());
            check(builds.equals(Set.of("Bitwig-6.1.3-6.1.4", "Bitwig-6.1.7")), "two builds found, files outside them left out: " + builds);
            check(Devices.visible(all, "Bitwig-6.1.7").size() == 13 && Devices.visible(all, "Bitwig-6.1.3-6.1.4").size() == 12, "13 files for 6.1.7, 12 for 6.1.3-6.1.4");
            check(Devices.parseTree(Files.readString(fixtures.resolve("devices-tree.json")), about, repo, "Bitwig-6.1.7").size() == 13, "folder filter keeps one build");
            check(Devices.pickBuild(builds, "6.1.4").equals("Bitwig-6.1.3-6.1.4"), "Bitwig 6.1.4 gets the 6.1.3-6.1.4 build");
            check(Devices.pickBuild(builds, "6.1.5").equals("Bitwig-6.1.3-6.1.4"), "Bitwig 6.1.5 gets the 6.1.3-6.1.4 build");
            check(Devices.pickBuild(builds, "6.1.7").equals("Bitwig-6.1.7"), "Bitwig 6.1.7 gets the 6.1.7 build");
            check(Devices.pickBuild(builds, "6.2").equals("Bitwig-6.1.7"), "Bitwig 6.2 gets the 6.1.7 build");
            check(Devices.pickBuild(builds, null).equals("Bitwig-6.1.7"), "unknown Bitwig gets the newest build");
            check(Devices.rawUrl(repo, "Bitwig-6.1.7/Multiband FX-4.bwdevice").endsWith("/bitwig-devices/HEAD/Bitwig-6.1.7/Multiband%20FX-4.bwdevice"), "raw URL encodes spaces");
            String flat = "{\"tree\":[{\"path\":\"Cool Comp.bwdevice\",\"type\":\"blob\",\"sha\":\"aa\",\"size\":10},{\"path\":\"mods/x/Wobble.bwmodulator\",\"type\":\"blob\",\"sha\":\"bb\",\"size\":10},{\"path\":\"README.md\",\"type\":\"blob\",\"sha\":\"cc\"}]}";
            List<Devices.Item> other = Devices.parseTree(flat, Map.of(), "someone/devs", "");
            check(other.size() == 2 && other.stream().allMatch(i -> i.build().isEmpty()), "a repository without builds: every device file, for every version");
            check(Devices.visible(other, "Bitwig-6.1.7").size() == 2 && Devices.folderOf(other.get(0)).endsWith(Path.of("someone_devs", "files")), "build-less files show for any build, in their own folder");

            Devices.Item normalizer = Devices.visible(all, "Bitwig-6.1.7").stream().filter(i -> i.name().equals("Normalizer")).findFirst().orElseThrow();
            check(normalizer.previewPath().equals("previews/normalizer.png") && normalizer.type().equals("Audio FX"), "item carries type and preview path");
            Path sample = fixtures.resolve("Normalizer-617.bwdevice");
            check(Devices.gitBlobSha(sample).equals(normalizer.sha()), "git blob hash of the real file matches the list");
            check(Devices.folderOf(normalizer).endsWith(Path.of("polarity_bitwig-devices", "Bitwig-6.1.7")), "files kept per repository and build");

            check(Devices.rank(normalizer) == 2, "not downloaded yet");
            Devices.download(normalizer, (i, target) -> Files.copy(sample, target));
            check(Devices.rank(normalizer) == 1, "downloaded and up to date");
            Files.writeString(Devices.folderOf(normalizer).resolve("Normalizer.bwdevice"), "older content");
            check(Devices.rank(normalizer) == 0, "changed file shows as update");
            boolean refused = false;
            try { Devices.download(normalizer, (i, target) -> Files.writeString(target, "tampered")); } catch (IOException e) { refused = true; }
            check(refused && Files.readString(Devices.folderOf(normalizer).resolve("Normalizer.bwdevice")).equals("older content"), "wrong hash refused, existing file kept");

            check(System.getProperty("launcher.bmlsettings") != null, "launcher.bmlsettings is set for the test");
            Path list = Devices.folderListFile();
            Files.createDirectories(list.getParent());
            Path oldBuild = Devices.root().resolve("polarity_bitwig-devices").resolve("Bitwig-6.1.3-6.1.4");
            Files.writeString(list, "{\n  \"schema_version\": 1,\n  \"folders\": [\"/Users/x/My devices\", \"" + oldBuild + "\"],\n  \"registrations\": [{\"uuid\": \"u\", \"keywords\": [\"a\"]}]\n}\n");
            List<Path> wanted = Devices.foldersFor(Devices.visible(all, "Bitwig-6.1.7"));
            check(wanted.equals(List.of(Devices.folderOf(normalizer))), "Patchbay gets the folder that holds files: " + wanted);
            check(Devices.register(wanted), "folder list changed");
            Map<String, Object> written = Mods.Json.object(Mods.Json.parse(Files.readString(list)));
            List<Object> folders = Mods.Json.array(written.get("folders"));
            check(folders.equals(List.of("/Users/x/My devices", Devices.folderOf(normalizer).toAbsolutePath().normalize().toString())), "user's folder kept, other build replaced by the chosen one");
            check(written.containsKey("registrations") && Files.readString(list).contains("\"schema_version\": 1,"), "other keys kept, whole numbers stay whole");
            check(!Devices.register(wanted), "registering again changes nothing");
        }

        static void sourcesTest() throws Exception {
            check(Sources.all().size() == 4 && Sources.patchbay().repo().equals("shaboogen-dev/Patchbay"), "four built-in sources, Patchbay from shaboogen-dev");
            check(Sources.patchbay().filter().equals(Platform.releasePattern()), "built-in Patchbay source takes this system's file");
            check("o/r".equals(Sources.normalizeRepo(" https://github.com/o/r.git ")) && "o/r".equals(Sources.normalizeRepo("o/r/tree/main")), "repository from a pasted link");
            check(Sources.normalizeRepo("not a repo") == null && Sources.normalizeRepo("../x") == null, "bad repository refused");
            Matcher m = Sources.glob("BML-*-macos.zip").matcher("BML-0.36.0-dev.16-macos.zip");
            check(m.matches() && m.group(1).equals("0.36.0-dev.16"), "pattern takes the version from the file name");
            check(!Sources.glob("BML-*-macos.zip").matcher("BML-0.36.0-dev.16-windows.zip").matches(), "pattern skips other platforms");
            check(Sources.glob("*.zip").matcher("scale-fold-0.1.0-dev.4.zip").matches() && !Sources.glob("*.zip").matcher("SHA256SUMS.txt").matches(), "*.zip keeps only ZIPs");
            List<Sources.Source> next = new ArrayList<>(Sources.all());
            next.set(2, next.get(2).withEnabled(false));
            next.add(new Sources.Source("own-1", Sources.MODS, "me/my-mods", "*.zip", false, true));
            next.add(new Sources.Source("own-2", Sources.PATCHBAY, "me/patchbay-fork", "BML-*-macos.zip", false, false));
            Sources.save(next);
            List<Sources.Source> back = Sources.all();
            check(back.size() == 6 && !back.get(2).enabled() && back.get(4).repo().equals("me/my-mods"), "sources saved and read back");
            check(Sources.enabled(Sources.MODS).stream().map(Sources.Source::repo).toList().equals(List.of("shaboogen-dev/Mods", "me/my-mods")), "a switched-off source is left out");
            check(Sources.patchbay().repo().equals("shaboogen-dev/Patchbay"), "Patchbay keeps the source that's on");
            String json = "[{\"tag_name\":\"v2\",\"assets\":[{\"name\":\"BML-2.0.0-macos.zip\",\"browser_download_url\":\"https://x/BML-2.0.0-macos.zip\"},{\"name\":\"BML-2.0.0-linux.zip\",\"browser_download_url\":\"https://x/BML-2.0.0-linux.zip\"}]}]";
            List<Release> r = parseReleases(json, Sources.glob("BML-*-macos.zip"));
            check(r.size() == 1 && r.get(0).version().equals("2.0.0"), "Patchbay releases read with the source's pattern");
            Sources.save(Sources.BUILTIN);
            check(Sources.all().equals(Sources.BUILTIN), "back to the built-in sources");
        }

        static void modsTest(Path fixtures, Path mods) throws Exception {
            Map<String, Mods.Available> a = Mods.parseCatalog(Files.readString(fixtures.resolve("mods-shaboogen.json")), "shaboogen-dev/Mods");
            Map<String, Mods.Available> b = Mods.parseCatalog(Files.readString(fixtures.resolve("mods-polarity.json")), "polarity/bitwig-mods");
            check(a.size() >= 50 && a.containsKey("hibernate-tracks") && a.containsKey("pre-5-style-side-browser"), "shaboogen catalogue: " + a.size() + " mods");
            check(!a.containsKey("all-mods") && !a.containsKey("SHA256SUMS"), "no bundle rows in the catalogue");
            check(b.size() == 2 && b.get("scale-fold").version().equals("0.1.0-dev.4"), "polarity catalogue: 2 mods");
            check("Track: Hibernate Tracks".equals(Mods.NAMES.get("hibernate-tracks")) && Mods.NAMES.size() >= 50, "display names read from the release notes: " + Mods.NAMES.size());
            Mods.Info info = Mods.infoOf(Mods.readManifest(mods.resolve("piano-roll-framework/mod.json")));
            check(info != null && info.name().startsWith("Framework") && !info.description().isEmpty(), "manifest details read: " + info.name());
            Mods.Info built = Mods.infoOf(Mods.Json.object(Mods.Json.parse("{\"mod\":{\"id\":\"x\",\"name\":\"X\"},\"built_against\":{\"bitwig_version\":\"6.1.3\",\"platform\":\"windows\"}}")));
            check(built != null && "6.1.3".equals(built.builtFor()), "built_against read: Bitwig " + (built == null ? null : built.builtFor()));
            check(a.get("show-chord-name").sha256() != null && a.get("show-chord-name").sha256().length() == 64, "catalogue carries SHA-256");
            Map<String, Mods.Available> catalog = new TreeMap<>(a);
            catalog.putAll(b);

            Map<String, Mods.Installed> before = Mods.installed(mods);
            check(before.containsKey("piano-roll-framework") && !before.containsKey("scale-fold"), "frameworks installed, scale-fold not yet");
            Mods.Fetcher local = (av, target) -> Files.copy(fixtures.resolve(av.id() + "-" + av.version() + ".zip"), target, StandardCopyOption.REPLACE_EXISTING);
            Path downloads = data().resolve("downloads");
            List<String> notes = Mods.installAll(List.of(catalog.get("scale-fold"), catalog.get("show-chord-name")), catalog, mods, downloads, local, s -> { });
            Map<String, Mods.Installed> after = Mods.installed(mods);
            check(after.containsKey("scale-fold") && after.get("scale-fold").version().equals("0.1.0-dev.4"), "scale-fold installed with its version");
            check(after.containsKey("show-chord-name"), "show-chord-name installed");
            check(Files.isRegularFile(mods.resolve("scale-fold/helper.jar")), "helper.jar unpacked");
            System.out.println("      notes: " + notes);
            try (Stream<Path> s = Files.list(mods)) {
                check(s.noneMatch(p -> p.getFileName().toString().startsWith(Mods.STAGING_PREFIX)), "no staging folder left");
            }
            try (Stream<Path> s = Files.list(downloads)) { check(s.findAny().isEmpty(), "downloaded ZIPs removed"); }

            Files.writeString(mods.resolve("scale-fold/MARK"), "old copy");
            Mods.installAll(List.of(catalog.get("scale-fold")), catalog, mods, downloads, local, s -> { });
            check(!Files.exists(mods.resolve("scale-fold/MARK")), "reinstall replaces the old copy");

            Mods.Available wrong = new Mods.Available("scale-fold", "0.1.0-dev.4", "https://x", "0".repeat(64), "t", null);
            boolean refused = false;
            try { Mods.installAll(List.of(wrong), catalog, mods, downloads, local, s -> { }); } catch (IOException e) { refused = true; }
            check(refused && Files.isRegularFile(mods.resolve("scale-fold/mod.json")), "wrong SHA-256 refused, installed copy untouched");

            Path bad = data().resolve("bad.zip");
            try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(Files.newOutputStream(bad))) {
                z.putNextEntry(new ZipEntry("evil/../../escape.txt")); z.write(1); z.closeEntry();
            }
            boolean unsafe = false;
            try { Mods.check(bad, mods); } catch (IOException e) { unsafe = true; }
            check(unsafe, "ZIP with .. in a path refused");
        }
    }
}
