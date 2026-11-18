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
    static final String VERSION = "0.9.5";
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

    /** GitHub says the file or repository isn't there (HTTP 404): a kept copy must not stand in for it. */
    static final class Gone extends IOException {
        Gone(String url) { super("GitHub: HTTP 404 (" + url + ")"); }
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
            } catch (Gone gone) {
                // GitHub says it's not there (any more): the kept copy is out of date, never shown again
                try { Files.deleteIfExists(body); Files.deleteIfExists(tag); } catch (IOException ignored) { }
                throw gone;
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
            if (status == 404) throw new Gone(url);
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
    private final JLabel activeLabel = Theme.label(" ", Theme.mono(12f), Theme.MUTED);
    private final JComboBox<String> versionBox = new JComboBox<>();
    private final JButton installButton = Theme.button(" ", "primary");
    private final JButton startButton = Theme.button(t("Uruchom Patchbay", "Start Patchbay"), "start");
    private final JProgressBar bar = new JProgressBar(0, 1000);
    private final JLabel statusLabel = Theme.label(" ", Theme.text(13f), Theme.MUTED);
    private final JComboBox<String> languageBox = new JComboBox<>();
    private final JButton dockButton = Theme.button(Platform.pinLabel(), "secondary");
    /** Opens the launcher's settings; it sits in the bottom bar, beside the dark / light switch. */
    private final JButton settingsButton = Theme.button(t("Ustawienia", "Settings"), "secondary");
    private final JButton guideButton = Theme.button(t("Instrukcja", "Guide"), "secondary");
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final Map<String, JButton> nav = new LinkedHashMap<>();
    private final Mods.Panel modsPanel = new Mods.Panel(this::modUpdatesFound);
    private final Devices.Panel devicesPanel = new Devices.Panel();
    private final Themes.Panel themesPanel = new Themes.Panel();
    private final Sources.Panel sourcesPanel = new Sources.Panel(this::sourcesChanged, this::newSourcesFound);
    private List<Release> releases = List.of();
    private boolean busy;

    private void modUpdatesFound(int updates) {
        JButton b = nav.get("mods");
        if (b == null) return;
        b.putClientProperty("badge", updates);
        b.getAccessibleContext().setAccessibleDescription(updates > 0 ? t("Aktualizacje: ", "Updates: ") + updates : null);
        b.revalidate();
        b.repaint();
    }

    /** The red count on the Sources tab: recommended sources the user hasn't looked at in Polecane. */
    private void newSourcesFound(int count) {
        JButton b = nav.get("sources");
        if (b == null) return;
        b.putClientProperty("badge", count);
        b.getAccessibleContext().setAccessibleDescription(count > 0 ? t("Nowe polecane źródła: ", "New recommended sources: ") + count : null);
        b.revalidate();
        b.repaint();
    }

    /** After the user changed the sources: every section loads again from the new set. */
    private void sourcesChanged() {
        checkReleases();
        modsPanel.reload();
        devicesPanel.sourcesChanged();
        themesPanel.sourcesChanged();
    }

    private void select(String key) {
        shownTab = key;
        cards.show(content, key);
        for (Map.Entry<String, JButton> e : nav.entrySet()) {
            e.getValue().putClientProperty("current", e.getKey().equals(key));
            e.getValue().repaint();
        }
        settingsButton.putClientProperty("current", key.equals("settings"));
        guideButton.putClientProperty("current", key.equals("guide"));
        guideButton.repaint();
        settingsButton.repaint();
        if (key.equals("devices")) devicesPanel.shown();
        if (key.equals("themes")) themesPanel.shown();
        if (key.equals("sources")) sourcesPanel.shown();
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

    /** The picture at size x size, halved step by step so it stays smooth (the source is already loaded by ImageIcon). */
    private static java.awt.image.BufferedImage scaled(Image raw, int size) {
        Image cur = raw;
        int w = raw.getWidth(null);
        do {
            w = Math.max(size, w / 2);
            java.awt.image.BufferedImage next = new java.awt.image.BufferedImage(w, w, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = next.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.drawImage(cur, 0, 0, w, w, null);
            g2.dispose();
            cur = next;
        } while (w > size);
        return (java.awt.image.BufferedImage) cur;
    }

    /** The app's picture (the Patchbay character) at the given size; the drawn mark when the picture is missing. */
    static JComponent appIcon(int size) {
        java.net.URL url = PatchbayLauncher.class.getResource("/icon-128.png");
        if (url == null) return new Logo();
        Image raw = new ImageIcon(url).getImage();
        // a sharp picture on Retina screens too: the 2x version is picked there
        Image sharp = new java.awt.image.BaseMultiResolutionImage(scaled(raw, size), scaled(raw, size * 2));
        JLabel l = new JLabel(new ImageIcon(sharp));
        l.setPreferredSize(new Dimension(size, size));
        l.getAccessibleContext().setAccessibleName("Patchbay Launcher");
        return l;
    }

    /** The bar across the top: the name, the sections as tabs, the active Patchbay and the button that starts it. */
    private JComponent topBar() {
        JPanel bar = new JPanel(new BorderLayout(16, 0));
        bar.setBackground(Theme.TITLE);
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.HAIR), new EmptyBorder(11, 18, 11, 18)));

        JLabel name = Theme.label("Patchbay Launcher", Theme.bold(14f), Theme.TEXT);
        JLabel version = Theme.label(VERSION, Theme.mono(11f), Theme.MUTED);
        JPanel brand = new JPanel(new GridBagLayout());
        brand.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.gridheight = 2;
        g.insets = new Insets(0, 0, 0, 10);
        brand.add(appIcon(36), g);
        g.gridheight = 1;
        g.gridx = 1;
        g.anchor = GridBagConstraints.WEST;
        g.insets = new Insets(0, 0, 0, 0);
        brand.add(name, g);
        g.gridy = 1;
        g.insets = new Insets(1, 0, 0, 0);
        brand.add(Theme.row(8, version, launcherLink), g);
        brand.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        brand.setToolTipText(t("Wersja, co nowego i informacje o systemie", "Version, what's new and system information"));
        brand.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { showLauncherPage(); }
            @Override public void mouseEntered(java.awt.event.MouseEvent e) { name.setForeground(Theme.ACCENT); }
            @Override public void mouseExited(java.awt.event.MouseEvent e) { name.setForeground(Theme.TEXT); }
        });
        bar.add(brand, BorderLayout.WEST);

        JPanel tabs = Theme.tabStrip(t("Sekcje", "Sections"));
        String[][] items = { { "patchbay", "Patchbay" }, { "mods", t("Mody", "Mods") }, { "devices", t("Urządzenia", "Devices") }, { "themes", t("Motywy", "Themes") }, { "sources", t("Źródła", "Sources") } };
        for (String[] it : items) {
            JButton b = Theme.button(it[1], "nav");
            b.addActionListener(e -> select(it[0]));
            nav.put(it[0], b);
            tabs.add(b);
        }
        JPanel middle = new JPanel(new GridBagLayout());
        middle.setOpaque(false);
        middle.add(tabs);
        bar.add(middle, BorderLayout.CENTER);

        activeLabel.setToolTipText(t("Aktywny Patchbay", "Active Patchbay"));
        bar.add(Theme.row(12, activeLabel, startButton), BorderLayout.EAST);
        ((JPanel) bar.getComponent(bar.getComponentCount() - 1)).setLayout(new GridBagLayout());
        // narrow windows (and longer words in German or French) drop the extras first
        bar.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                int w = bar.getWidth();
                activeLabel.setVisible(w >= 1120);
                name.setVisible(w >= 1040);
                version.setVisible(w >= 1040);
            }
        });
        return bar;
    }

    /** The thin bar along the bottom: on the left, the launcher's Discord channel and the dark / light switch. */
    private JComponent bottomBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.TITLE);
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.HAIR), new EmptyBorder(5, 14, 5, 14)));
        JButton discord = Theme.button("Discord", "secondary");
        discord.putClientProperty("small", true);
        discord.setFont(Theme.text(12f));
        discord.setIcon(discordIcon());
        discord.setIconTextGap(6);
        discord.setToolTipText(t("Kanał Launchera na Discordzie Bitwiga", "The launcher's channel on the Bitwig Discord"));
        discord.getAccessibleContext().setAccessibleName(t("Otwórz kanał Launchera na Discordzie", "Open the launcher's Discord channel"));
        discord.addActionListener(e -> browse(DISCORD));
        Theme.ModeSwitch mode = new Theme.ModeSwitch(Theme.light(), t("Jasny wygląd", "Light appearance"),
            t("Ciemny wygląd", "Dark appearance"), this::appearanceChosen);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(mode);
        settingsButton.putClientProperty("small", true);
        settingsButton.setFont(Theme.text(12f));
        settingsButton.setIcon(new GearIcon());
        settingsButton.setIconTextGap(6);
        settingsButton.addActionListener(e -> select("settings"));
        left.add(settingsButton);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        guideButton.putClientProperty("small", true);
        guideButton.setFont(Theme.text(12f));
        guideButton.setIcon(new BookIcon());
        guideButton.setIconTextGap(6);
        guideButton.setToolTipText(t("Jak korzystać z Launchera", "How to use the launcher"));
        guideButton.addActionListener(e -> select("guide"));
        right.add(guideButton);
        for (JComponent b : aboutButtons()) right.add(b);
        right.add(discord);
        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    /** The tab on screen, so a rebuilt window opens on the same one. */
    private String shownTab = "patchbay";
    /** The first size of the window: most of the screen's free space (without the menu bar and Dock), 1200×760 at least and 1600×1000 at most, never beyond the screen. */
    static Dimension startSize() {
        Rectangle free = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int w = Math.max(1200, Math.min(1600, free.width * 8 / 10));
        int h = Math.max(760, Math.min(1000, free.height * 85 / 100));
        return new Dimension(Math.min(w, free.width), Math.min(h, free.height));
    }

    /** The window's place and size from the last time, if its top still lies on a screen; else null (the start size). */
    static Rectangle savedWindow() {
        String kept = I18n.setting("window");
        if (kept == null) return null;
        try {
            String[] n = kept.split(",");
            Rectangle r = new Rectangle(Integer.parseInt(n[0].strip()), Integer.parseInt(n[1].strip()), Integer.parseInt(n[2].strip()), Integer.parseInt(n[3].strip()));
            for (GraphicsDevice screen : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
                Rectangle on = screen.getDefaultConfiguration().getBounds();
                if (on.contains(r.x + Math.min(r.width, 200) / 2, r.y + 10)) {
                    r.width = Math.max(980, Math.min(r.width, on.width));
                    r.height = Math.max(640, Math.min(r.height, on.height));
                    return r;
                }
            }
        } catch (RuntimeException unreadable) { }
        return null;
    }

    /** The window's last place and size while not maximized. */
    private Rectangle normalBounds;

    /** Keeps the window's place and size (as it is when not maximized) for the next start. */
    private void saveWindow() {
        boolean max = (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
        Rectangle r = max && normalBounds != null ? normalBounds : frame.getBounds();
        I18n.remember("window", r.x + "," + r.y + "," + r.width + "," + r.height);
        I18n.remember("windowState", max ? "max" : "normal");
    }

    /** Where the previous window was, for a window rebuilt after a language or appearance change; null on start. */
    private Rectangle keptBounds;
    private int keptState = Frame.NORMAL;
    private String keptTab;

    /** Builds the window again (new language or colours) at the same place, size and tab as the old one. */
    private void rebuild() {
        PatchbayLauncher next = new PatchbayLauncher();
        int state = frame.getExtendedState();
        frame.setExtendedState(Frame.NORMAL); // the size before maximizing, so un-maximizing still works
        next.keptBounds = frame.getBounds();
        next.keptState = state & ~Frame.ICONIFIED;
        next.keptTab = shownTab;
        frame.dispose();
        next.show();
    }

    /** Dark or light: kept in the settings; the window is built again in the new colours, like after a language change. */
    private void appearanceChosen(boolean light) {
        if (busy) {
            JOptionPane.showMessageDialog(frame, t("Poczekaj, aż Launcher skończy bieżące zadanie.", "Wait until the launcher finishes what it is doing."),
                "Patchbay Launcher", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        I18n.remember("appearance", light ? "light" : "dark");
        Theme.palette(light);
        Theme.install();
        rebuild();
    }

    /** A speech bubble, for the Discord link (drawn, not Discord's own logo). */
    /**
     * The Discord button's picture: res/discord.png when that file is in the build (an image Mario supplies),
     * otherwise the drawn speech bubble.
     */
    private static Icon discordIcon() {
        return suppliedIcon("discord.png", new ChatIcon());
    }

    /** An image from the build's resources (res/<name>, supplied by Mario) at 18 px high; the drawn icon when it isn't there. */
    private static Icon suppliedIcon(String name, Icon drawn) {
        java.net.URL url = PatchbayLauncher.class.getResource("/" + name);
        if (url == null) return drawn;
        Image raw = new ImageIcon(url).getImage();
        int h = 18, w = Math.max(1, Math.round(raw.getWidth(null) * h / (float) raw.getHeight(null)));
        return new ImageIcon(new java.awt.image.BaseMultiResolutionImage(fit(raw, w, h), fit(raw, w * 2, h * 2)));
    }

    private static java.awt.image.BufferedImage fit(Image raw, int w, int h) {
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.drawImage(raw, 0, 0, w, h, null);
        g2.dispose();
        return out;
    }

    /** An arrow into a tray, for "New version" in the top bar. */
    private static final class DownloadIcon implements Icon {
        @Override public int getIconWidth() { return 12; }
        @Override public int getIconHeight() { return 12; }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            g2.setColor(Theme.ACCENT);
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(6, 1, 6, 8);
            g2.drawPolyline(new int[] { 3, 6, 9 }, new int[] { 5, 8, 5 }, 3);
            g2.drawPolyline(new int[] { 1, 1, 11, 11 }, new int[] { 8, 11, 11, 8 }, 4);
            g2.dispose();
        }
    }

    /** "</>" for the project's page (drawn; res/github.png replaces it when supplied). */
    private static final class CodeIcon implements Icon {
        @Override public int getIconWidth() { return 16; }
        @Override public int getIconHeight() { return 16; }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.translate(x, y);
            g2.setColor(Theme.ACCENT);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            java.awt.geom.Path2D left = new java.awt.geom.Path2D.Float(), right = new java.awt.geom.Path2D.Float();
            left.moveTo(5, 4.5); left.lineTo(1.5, 8); left.lineTo(5, 11.5);
            right.moveTo(11, 4.5); right.lineTo(14.5, 8); right.lineTo(11, 11.5);
            g2.draw(left);
            g2.draw(right);
            g2.draw(new java.awt.geom.Line2D.Float(9.2f, 3.5f, 6.8f, 12.5f));
            g2.dispose();
        }
    }

    /** A cog, for Settings (drawn). */
    private static final class GearIcon implements Icon {
        @Override public int getIconWidth() { return 16; }
        @Override public int getIconHeight() { return 16; }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.translate(x + 8, y + 8);
            g2.setColor(Theme.ACCENT);
            java.awt.geom.Area cog = new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Float(-5.2f, -5.2f, 10.4f, 10.4f));
            for (int i = 0; i < 8; i++) {
                java.awt.geom.AffineTransform turn = java.awt.geom.AffineTransform.getRotateInstance(Math.PI / 4 * i);
                cog.add(new java.awt.geom.Area(turn.createTransformedShape(new java.awt.geom.RoundRectangle2D.Float(-1.5f, -7.6f, 3f, 3.4f, 1f, 1f))));
            }
            cog.subtract(new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Float(-2.2f, -2.2f, 4.4f, 4.4f)));
            g2.fill(cog);
            g2.dispose();
        }
    }

    /** An open book, for the guide (drawn). */
    private static final class BookIcon implements Icon {
        @Override public int getIconWidth() { return 16; }
        @Override public int getIconHeight() { return 16; }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.translate(x, y);
            g2.setColor(Theme.ACCENT);
            g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            java.awt.geom.Path2D.Float left = new java.awt.geom.Path2D.Float(), right = new java.awt.geom.Path2D.Float();
            left.moveTo(8f, 4.2f); left.curveTo(6.2f, 2.8f, 3.6f, 2.8f, 1.6f, 3.4f); left.lineTo(1.6f, 13f); left.curveTo(3.6f, 12.4f, 6.2f, 12.4f, 8f, 13.8f);
            right.moveTo(8f, 4.2f); right.curveTo(9.8f, 2.8f, 12.4f, 2.8f, 14.4f, 3.4f); right.lineTo(14.4f, 13f); right.curveTo(12.4f, 12.4f, 9.8f, 12.4f, 8f, 13.8f);
            g2.draw(left);
            g2.draw(right);
            g2.draw(new java.awt.geom.Line2D.Float(8f, 4.2f, 8f, 13.8f));
            g2.dispose();
        }
    }

    /** A small beetle, for "Report a problem" (drawn). */
    private static final class BugIcon implements Icon {
        @Override public int getIconWidth() { return 16; }
        @Override public int getIconHeight() { return 16; }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.translate(x, y);
            g2.setColor(Theme.ACCENT);
            g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            // legs: three on each side
            for (float ly : new float[] { 7.5f, 10f, 12.5f }) {
                float reach = ly == 10f ? 3.6f : 3f, tilt = ly == 7.5f ? -1.2f : ly == 12.5f ? 1.2f : 0f;
                g2.draw(new java.awt.geom.Line2D.Float(5f, ly, 5f - reach, ly + tilt));
                g2.draw(new java.awt.geom.Line2D.Float(11f, ly, 11f + reach, ly + tilt));
            }
            // antennae
            g2.draw(new java.awt.geom.Line2D.Float(6.6f, 3.6f, 5f, 1.2f));
            g2.draw(new java.awt.geom.Line2D.Float(9.4f, 3.6f, 11f, 1.2f));
            // head and body
            g2.fill(new java.awt.geom.Ellipse2D.Float(5.6f, 2.8f, 4.8f, 3.6f));
            g2.fill(new java.awt.geom.Ellipse2D.Float(4.4f, 5.4f, 7.2f, 9.2f));
            // the line down the back
            g2.setColor(Theme.TITLE);
            g2.setStroke(new BasicStroke(1f));
            g2.draw(new java.awt.geom.Line2D.Float(8f, 7f, 8f, 13.6f));
            g2.dispose();
        }
    }

    private static final class ChatIcon implements Icon {
        @Override public int getIconWidth() { return 16; }
        @Override public int getIconHeight() { return 16; }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y + 1);
            x = 0; y = 0;
            g2.setColor(Theme.ACCENT);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawRoundRect(x + 1, y + 1, 14, 9, 5, 5);
            g2.drawPolyline(new int[] { x + 4, x + 4, x + 8 }, new int[] { y + 10, y + 13, y + 10 }, 3);
            g2.fillOval(x + 4, y + 5, 2, 2);
            g2.fillOval(x + 7, y + 5, 2, 2);
            g2.fillOval(x + 10, y + 5, 2, 2);
            g2.dispose();
        }
    }

    /** A framed part of the Patchbay page: an optional header line (title left, extra right) over the body. */
    private static JPanel section(JComponent title, JComponent right, JComponent body) {
        JPanel card = new Theme.Card(new BorderLayout(), Theme.FRAME);
        if (title != null) {
            JPanel head = new JPanel(new BorderLayout(12, 0));
            head.setOpaque(false);
            head.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.EDGE), new EmptyBorder(10, 14, 10, 14)));
            head.add(title, BorderLayout.CENTER);
            if (right != null) head.add(right, BorderLayout.EAST);
            card.add(head, BorderLayout.NORTH);
        }
        body.setBorder(new EmptyBorder(12, 14, 12, 14));
        card.add(body, BorderLayout.CENTER);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        return card;
    }

    /** Saving what the user set up into one file, and bringing it back. */
    private JPanel backupSection() {
        JButton save = Theme.button(t("Zapisz kopię…", "Save a backup…"), "secondary");
        save.addActionListener(e -> saveBackup());
        JButton restore = Theme.button(t("Przywróć z kopii…", "Restore from a backup…"), "secondary");
        restore.addActionListener(e -> restoreBackup());
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.add(Theme.wrapped(t("Kopia ustawień: włączone mody, wybrany motyw z plikami motywów i źródła w jednym pliku, np. do przeniesienia na inny komputer.",
            "A backup of your settings: the mods switched on, the chosen theme with the theme files, and the sources in one file, for example to move to another computer."),
            420, Theme.MUTED, 13f), BorderLayout.CENTER);
        row.add(Theme.row(8, save, restore), BorderLayout.EAST);
        return section(null, null, row);
    }

    private void saveBackup() {
        FileDialog pick = new FileDialog(frame, t("Zapisz kopię ustawień", "Save the settings backup"), FileDialog.SAVE);
        pick.setFile(Backup.defaultName());
        pick.setVisible(true);
        if (pick.getFile() == null) return;
        String name = pick.getFile().toLowerCase(java.util.Locale.ROOT).endsWith(".zip") ? pick.getFile() : pick.getFile() + ".zip";
        try {
            int files = Backup.save(Path.of(pick.getDirectory(), name), Devices.patchbaySettings(), Themes.components(), Sources.file());
            statusLabel.setText(files == 0 ? t("Nie ma jeszcze czego zapisać: Patchbay nie był uruchamiany i nie ma własnych źródeł.",
                "There is nothing to save yet: Patchbay hasn't been started and there are no sources of your own.")
                : t("Kopia zapisana: ", "Backup saved: ") + name);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, t("Nie udało się zapisać kopii: ", "Couldn't save the backup: ") + ex.getMessage(), "Patchbay Launcher", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void restoreBackup() {
        if (bitwigRunning() || patchbayRunning()) {
            JOptionPane.showMessageDialog(frame, t("Zamknij Patchbay i Bitwiga, a potem przywróć kopię.", "Close Patchbay and Bitwig, then restore the backup."),
                "Patchbay Launcher", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        FileDialog pick = new FileDialog(frame, t("Wybierz kopię ustawień", "Choose a settings backup"), FileDialog.LOAD);
        pick.setFilenameFilter((dir, n) -> n.toLowerCase(java.util.Locale.ROOT).endsWith(".zip"));
        if (Platform.WINDOWS) pick.setFile("*.zip");
        pick.setVisible(true);
        if (pick.getFile() == null) return;
        int answer = JOptionPane.showConfirmDialog(frame,
            t("Przywrócenie zastąpi obecną listę włączonych modów, wybrany motyw i źródła. Kontynuować?",
                "Restoring replaces the current list of mods switched on, the chosen theme and the sources. Continue?"),
            "Patchbay Launcher", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) return;
        try {
            Backup.restore(Path.of(pick.getDirectory(), pick.getFile()), Devices.patchbaySettings(), Themes.components(), Sources.file());
            rebuild(); // the lists and sources are read again
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, t("Nie udało się przywrócić kopii: ", "Couldn't restore the backup: ") + ex.getMessage(), "Patchbay Launcher", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** A red count, as macOS marks updates. */
    private static final class Count extends JLabel {
        int value;
        Count() { setPreferredSize(new Dimension(24, 20)); setVisible(false); }
        void set(int n) {
            value = n;
            setVisible(n > 0);
            getAccessibleContext().setAccessibleName(n > 0 ? t("Aktualizacje: ", "Updates: ") + n : null);
            repaint();
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            String n = String.valueOf(value);
            g2.setFont(Theme.bold(12f));
            FontMetrics fm = g2.getFontMetrics();
            int w = Math.max(20, fm.stringWidth(n) + 12), x = getWidth() - w, y = (getHeight() - 20) / 2;
            g2.setColor(Theme.BADGE);
            g2.fillRoundRect(x, y, w, 20, 20, 20);
            g2.setColor(Color.WHITE);
            g2.drawString(n, x + (w - fm.stringWidth(n)) / 2, y + (20 + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }

    private final Count patchbayCount = new Count();
    private final JLabel carryLabel = Theme.label(" ", Theme.text(13f), Theme.MUTED);

    private JComponent patchbayPage() {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));

        // 1. versions: "Update available · 0.36.0-dev.14 → 0.36.0-dev.16" with a red count, then the picker
        JPanel v = new JPanel();
        v.setOpaque(false);
        v.setLayout(new BoxLayout(v, BoxLayout.Y_AXIS));
        versionBox.setPreferredSize(new Dimension(240, 32));
        versionBox.setFont(Theme.mono(12f));
        versionBox.getAccessibleContext().setAccessibleName(t("Wersja Patchbay", "Patchbay version"));
        JPanel pick = Theme.row(10, Theme.label(t("Wersja", "Version"), Theme.text(13f), Theme.MUTED), versionBox, installButton, carryLabel);
        pick.setAlignmentX(Component.LEFT_ALIGNMENT);
        v.add(pick);
        bar.setPreferredSize(new Dimension(10, 4));
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 4));
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        bar.setVisible(false);
        v.add(Box.createVerticalStrut(10));
        v.add(bar);
        v.add(Box.createVerticalStrut(4));
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        // the line under the picker takes room only while it says something
        statusLabel.setVisible(false);
        statusLabel.addPropertyChangeListener("text", e -> statusLabel.setVisible(!statusLabel.getText().isBlank()));
        v.add(statusLabel);
        latestLabel.setFont(Theme.text(13f));
        col.add(section(latestLabel, patchbayCount, v));

        // the page follows the window's width, so the buttons on the right never fall off a narrow window
        JPanel top = new Theme.Column();
        col.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(col);
        JScrollPane scroll = new JScrollPane(top, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return Theme.page("Patchbay", installedLabel, null, scroll, null, null);
    }

    /** The launcher's own settings, opened from the bottom bar: language, the settings backup, the data folder, and whose project this is. */
    private JComponent settingsPage() {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));

        languageBox.addItem(t("Język systemu", "System language"));
        for (String name : new String[] { "Polski", "English", "Deutsch", "Français" }) languageBox.addItem(name);
        languageBox.setSelectedIndex(I18n.CODES.indexOf(I18n.choice()) + 1);
        languageBox.addActionListener(e -> languageChosen()); // only after the list is filled: filling it fires events
        languageBox.setPreferredSize(new Dimension(190, 32));
        languageBox.getAccessibleContext().setAccessibleName(t("Język", "Language"));
        JPanel language = Theme.row(10, Theme.label(t("Język", "Language"), Theme.text(13f), Theme.MUTED), languageBox);
        col.add(section(null, null, language));
        col.add(Box.createVerticalStrut(12));

        col.add(backupSection());
        col.add(Box.createVerticalStrut(12));

        JButton folder = Theme.button(t("Pokaż folder z danymi", "Show the data folder"), "secondary");
        folder.addActionListener(e -> {
            try { Files.createDirectories(data()); } catch (Exception ignored) { }
            Platform.open(data().toString());
        });
        JPanel files = new JPanel(new BorderLayout(12, 0));
        files.setOpaque(false);
        files.add(Theme.wrapped(t("Folder z danymi Launchera: zainstalowane wersje Patchbay, ustawienia i logi.",
            "The launcher's data folder: the installed Patchbay versions, settings and logs."), 420, Theme.MUTED, 13f), BorderLayout.CENTER);
        files.add(Theme.row(8, dockButton, folder), BorderLayout.EAST);
        col.add(section(null, null, files));
        col.add(Box.createVerticalStrut(12));

        JPanel about = new JPanel(new BorderLayout());
        about.setOpaque(false);
        about.add(Theme.wrapped(aboutText(), 620, Theme.MUTED, 13f), BorderLayout.CENTER);
        col.add(section(null, null, about));

        JPanel top = new Theme.Column();
        col.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(col);
        JScrollPane scroll = new JScrollPane(top, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return Theme.page(t("Ustawienia", "Settings"), Theme.label("Patchbay Launcher " + VERSION, Theme.mono(12f), Theme.MUTED), null, scroll, null, null);
    }

    /** The launcher's own GitHub page, where its problems are reported (not Patchbay's). */
    static final String PROJECT = "https://github.com/arius77R/patchbay-launcher";
    /** The launcher's channel on the Bitwig Discord server. */
    static final String DISCORD = "https://discord.com/channels/121395249003233280/1571878415781466153";

    /** Who makes the launcher and where to report problems with it, so they don't go to Patchbay's authors. */
    /** Who makes the launcher and where to report its problems: shown on hovering the two buttons in the bottom bar. */
    static String aboutText() {
        return t("Patchbay Launcher to nieoficjalny projekt, który tworzy i wspiera Marius R. Nie jest częścią Patchbay: problemy z Launcherem zgłaszaj na jego stronie na GitHubie, a nie autorom Patchbay.",
            "Patchbay Launcher is an unofficial project made and supported by Marius R. It is not part of Patchbay: please report problems with the launcher on its GitHub page, not to the authors of Patchbay.");
    }

    /** "Report a problem" and "Project page", small, for the bottom bar; hovering either explains whose project this is. */
    private JComponent[] aboutButtons() {
        String tip = "<html><div style='width:300px'>" + aboutText() + "</div></html>";
        JButton report = Theme.button(t("Zgłoś problem", "Report a problem"), "secondary");
        report.addActionListener(e -> reportProblem());
        JButton page = Theme.button(t("Strona projektu", "Project page"), "secondary");
        page.addActionListener(e -> browse(PROJECT));
        for (JButton b : new JButton[] { report, page }) {
            b.putClientProperty("small", true);
            b.setFont(Theme.text(12f));
            b.setToolTipText(tip);
        }
        report.setIcon(new BugIcon());
        report.setIconTextGap(6);
        page.setIcon(suppliedIcon("github.png", new CodeIcon()));
        page.setIconTextGap(6);
        report.getAccessibleContext().setAccessibleDescription(aboutText());
        return new JComponent[] { report, page };
    }

    /**
     * Opens a new issue on GitHub with the system information already in it, so the user only writes what
     * happened; they see all of it before sending. Worked out off the window, since finding Bitwig can take a moment.
     */
    private static void reportProblem() {
        new Thread(() -> {
            String body = "**What happened**\n\n\n**What should have happened**\n\n\n**System information**\n```\n" + SystemInfo.text() + "\n```\n";
            browse(PROJECT + "/issues/new?body=" + java.net.URLEncoder.encode(body, StandardCharsets.UTF_8).replace("+", "%20"));
        }, "report").start();
    }

    private static void browse(String url) {
        Platform.open(url);
    }

    private void show() {
        dockButton.addActionListener(e -> addToDockClicked());
        dockButton.setVisible(false);
        new Thread(() -> {
            Platform.refreshIcon(VERSION);
            boolean offer = (Platform.pinBlocked() == null || (Platform.MAC && Platform.appBundle() != null)) && !Platform.pinned();
            SwingUtilities.invokeLater(() -> dockButton.setVisible(offer));
        }, "dock-check").start();
        installButton.addActionListener(e -> installSelected());
        startButton.addActionListener(e -> start());
        versionBox.addActionListener(e -> refreshButtons());

        content.setBackground(Theme.WELL);
        content.add(patchbayPage(), "patchbay");
        content.add(modsPanel, "mods");
        content.add(devicesPanel, "devices");
        content.add(themesPanel, "themes");
        content.add(sourcesPanel, "sources");
        content.add(settingsPage(), "settings");
        content.add(Guide.page(), "guide");
        content.add(launcherPage, "launcher");
        fillLauncherPage();
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.WELL);
        root.add(topBar(), BorderLayout.NORTH);
        root.add(bottomBar(), BorderLayout.SOUTH);
        root.add(content, BorderLayout.CENTER);
        frame.setContentPane(root);
        java.net.URL iconUrl = PatchbayLauncher.class.getResource("/icon-128.png");
        if (iconUrl != null) frame.setIconImage(new ImageIcon(iconUrl).getImage());
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(980, 640));
        if (keptBounds != null) {
            frame.setBounds(keptBounds);
            if (keptState != Frame.NORMAL) frame.setExtendedState(keptState);
        } else if (savedWindow() != null) {
            frame.setBounds(savedWindow());
            if ("max".equals(I18n.setting("windowState"))) frame.setExtendedState(Frame.MAXIMIZED_BOTH);
        } else {
            frame.setSize(startSize());
            frame.setLocationRelativeTo(null);
        }
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { saveWindow(); }
        });
        frame.addComponentListener(new java.awt.event.ComponentAdapter() {
            private void note() { if (frame.getExtendedState() == Frame.NORMAL) normalBounds = frame.getBounds(); }
            @Override public void componentMoved(java.awt.event.ComponentEvent e) { note(); }
            @Override public void componentResized(java.awt.event.ComponentEvent e) { note(); }
        });
        refreshLocal();
        String tab = keptTab != null ? keptTab : System.getProperty("launcher.tab", currentVersion() == null ? "patchbay" : "mods");
        select(nav.containsKey(tab) ? tab : "patchbay");
        frame.setVisible(true);
        // the first tab would take the keyboard focus and look selected; the page takes it instead
        content.setFocusable(true);
        content.requestFocusInWindow();
        checkReleases();
        checkLauncher();
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
        rebuild();
    }

    private void refreshLocal() {
        String cur = currentVersion();
        installedLabel.setText((cur == null
            ? t("Patchbay nie jest jeszcze zainstalowany.", "Patchbay isn't installed yet.")
            : t("Zainstalowana wersja: ", "Installed version: ") + cur)
            + " · " + t("Źródło: ", "Source: ") + Sources.patchbay().repo());
        activeLabel.setText(cur == null ? " " : "BML " + cur);
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
        if (showUpdate() && cur != null)
            notifyUser(t("Dostępna jest nowa wersja Patchbay: ", "A new Patchbay version is available: ") + newest);
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
                    themesPanel.patchbayChanged();
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
        if (!releases.isEmpty()) showUpdate();
    }

    /** The head of the versions part, the red count beside it and on the Patchbay tab; true when an update is waiting. */
    private boolean showUpdate() {
        String newest = releases.get(0).version(), cur = currentVersion();
        boolean update = cur != null && compareVersions(newest, cur) > 0;
        if (cur == null) latestLabel.setText(t("Najnowsza wersja: ", "Latest version: ") + newest);
        else if (update) latestLabel.setText("<html>" + Theme.UpdateBar.title(1) + " <font color='" + Theme.hex(Theme.MUTED) + "'>· " + cur + " → " + newest + "</font></html>");
        else latestLabel.setText(t("Masz najnowszą wersję.", "You have the latest version."));
        patchbayCount.set(update ? 1 : 0);
        carryLabel.setText(update ? t("Mody i motywy przejdą do nowej wersji.", "Mods and themes move to the new version.") : " ");
        patchbayUpdate = update;
        patchbayTabBadge();
        return update;
    }

    private boolean patchbayUpdate;

    /** The red count on the Patchbay tab: a new Patchbay (a new launcher is shown in the top bar instead). */
    private void patchbayTabBadge() {
        JButton tab = nav.get("patchbay");
        if (tab == null) return;
        tab.putClientProperty("badge", patchbayUpdate ? 1 : 0);
        tab.revalidate();
        tab.repaint();
    }

    // ---------- the launcher's own window: version and updates, what's new, system information ----------

    /** The newest launcher release on GitHub (this version or a newer one); null until checked or when GitHub can't be reached. */
    private SelfUpdate.Release launcherLatest;
    /** "New version 0.9.6" beside the launcher's version in the top bar: shown only while a newer launcher is out; a click opens the launcher's window. */
    private final JLabel launcherLink = new JLabel();
    {
        launcherLink.setIcon(new DownloadIcon());
        launcherLink.setIconTextGap(5);
        launcherLink.setFont(Theme.text(11f));
        launcherLink.setForeground(Theme.ACCENT);
        launcherLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        launcherLink.setToolTipText(t("Co nowego i aktualizacja Launchera", "What's new, and updating the launcher"));
        launcherLink.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { showLauncherPage(); }
        });
        launcherLink.setVisible(false);
    }

    private void checkLauncher() {
        new Thread(() -> {
            try {
                SelfUpdate.Release r = SelfUpdate.latest();
                SwingUtilities.invokeLater(() -> launcherChecked(r));
            } catch (IOException | RuntimeException ignored) {
                // no network or GitHub busy: no launcher update shown, nothing else changes
            }
        }, "launcher-update").start();
        sourcesPanel.loadOnline();
    }

    private void launcherChecked(SelfUpdate.Release r) {
        launcherLatest = r;
        fillLauncherPage();
        if (!SelfUpdate.newer(r)) return;
        launcherLink.setText(t("Nowa wersja ", "New version ") + r.version());
        launcherLink.getAccessibleContext().setAccessibleName(t("Nowa wersja Patchbay Launcher: ", "New Patchbay Launcher version: ") + r.version());
        launcherLink.setVisible(true);
        launcherLink.getParent().revalidate();
        if (!r.version().equals(I18n.setting("launcherNotified"))) {
            I18n.remember("launcherNotified", r.version());
            notifyUser(t("Dostępna jest nowa wersja Patchbay Launcher: ", "A new Patchbay Launcher version is available: ") + r.version());
        }
    }

    /** The launcher's own page, opened from its name and version in the top bar (and from "New version"). */
    private final JPanel launcherPage = new JPanel(new BorderLayout());
    /** The update under way: the page is not rebuilt while it runs, so its progress stays on screen. */
    private boolean launcherUpdating;

    private void showLauncherPage() {
        select("launcher");
        fillLauncherPage();
    }

    /**
     * This version and whether a newer one is out, with Update and the release page; what's new; the
     * system information for a report, with Copy. Rebuilt when the release arrives and each time it is
     * opened (the system information).
     */
    private void fillLauncherPage() {
        if (launcherUpdating) return;
        SelfUpdate.Release r = launcherLatest;
        boolean newer = SelfUpdate.newer(r);
        JLabel state = newer ? Theme.label(VERSION + " · " + t("dostępna jest nowa wersja ", "a new version is available: ") + r.version(), Theme.mono(12f), Theme.ACCENT)
            : Theme.label(VERSION + " · " + (r != null ? t("masz najnowszą wersję", "you have the newest version")
                : t("nie udało się sprawdzić, czy jest nowa wersja", "couldn't check for a new version")), Theme.mono(12f), Theme.MUTED);

        JButton page = Theme.button(t("Strona wydania", "Release page"), "secondary");
        page.addActionListener(e -> browse(r != null && r.page() != null ? r.page() : PROJECT + "/releases"));
        JComponent actions = Theme.row(8, page);
        JLabel status = Theme.label(" ", Theme.text(12f), Theme.MUTED);
        if (newer) {
            JButton update = Theme.button(t("Aktualizuj", "Update"), "primary");
            update.addActionListener(e -> updateLauncher(r, status, update));
            actions = Theme.row(8, page, update);
            status.setText(t("Launcher zamknie się na chwilę i uruchomi ponownie. Patchbay, mody i ustawienia zostają bez zmian.",
                "The launcher closes for a moment and starts again. Patchbay, mods and settings stay as they are."));
        }

        JPanel notes = new JPanel(new BorderLayout());
        notes.setOpaque(false);
        notes.add(whatsNew(r != null && (newer || r.version().equals(VERSION)) ? r.notes() : null), BorderLayout.CENTER);
        JPanel left = section(Theme.label(t("Co nowego w ", "What's new in ") + (newer ? r.version() : VERSION), Theme.bold(13f), Theme.TEXT), null, notes);

        JTextArea info = new JTextArea("…");
        info.setFont(Theme.mono(11f));
        info.setEditable(false);
        info.setOpaque(false);
        info.setForeground(Theme.TEXT);
        info.setLineWrap(true);
        info.setWrapStyleWord(true);
        JButton copy = Theme.button(t("Kopiuj", "Copy"), "secondary");
        copy.setEnabled(false);
        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(info.getText()), null);
            copy.setText(t("Skopiowano", "Copied"));
        });
        new Thread(() -> {
            String text = SystemInfo.text();
            SwingUtilities.invokeLater(() -> { info.setText(text); copy.setEnabled(true); });
        }, "system-info").start();
        JPanel infoBody = new JPanel(new BorderLayout(0, 8));
        infoBody.setOpaque(false);
        infoBody.add(info, BorderLayout.CENTER);
        infoBody.add(Theme.wrapped(t("Wklej to do zgłoszenia problemu. Same wersje i nazwy, bez danych osobistych.",
            "Paste this into a problem report. Versions and names only, nothing personal."), 380, Theme.MUTED, 12f), BorderLayout.SOUTH);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.add(section(Theme.label(t("Informacje o systemie", "System information"), Theme.bold(13f), Theme.TEXT), copy, infoBody));
        JPanel rightTop = new JPanel(new BorderLayout());
        rightTop.setOpaque(false);
        rightTop.add(right, BorderLayout.NORTH);

        JPanel body = new JPanel(new GridLayout(1, 2, 12, 0));
        body.setOpaque(false);
        body.add(left);
        body.add(rightTop);
        launcherPage.removeAll();
        launcherPage.add(Theme.page("Patchbay Launcher", state, actions, body, status, null), BorderLayout.CENTER);
        launcherPage.revalidate();
        launcherPage.repaint();
    }

    /** The release's "What's new" section, or a line pointing to the release page. */
    private static JComponent whatsNew(String releaseNotes) {
        String notes = releaseNotes == null ? null : SelfUpdate.whatsNewHtml(releaseNotes);
        JEditorPane text = new JEditorPane("text/html", "<html><body style='font-family:" + Theme.text(13f).getFamily() + "; font-size:13pt; color:"
            + Theme.hex(Theme.TEXT) + ";'>" + (notes != null ? notes : "<p>" + t("Opis zmian jest na stronie wydania.", "The changes are described on the release page.") + "</p>")
            + "</body></html>");
        text.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        text.setFont(Theme.text(13f));
        text.setEditable(false);
        text.setOpaque(false);
        text.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(text, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    /** Downloads the new launcher, then quits; a script swaps it in and starts it again. A copy that can't update itself opens the release page. */
    private void updateLauncher(SelfUpdate.Release r, JLabel status, JButton update) {
        Path root = SelfUpdate.installRoot();
        if (root == null) {
            status.setText(t("Ta kopia nie może się zaktualizować sama: pobierz nową wersję ze strony wydania.",
                "This copy can't update itself: get the new version from the release page."));
            browse(r.page() != null ? r.page() : PROJECT + "/releases");
            return;
        }
        launcherUpdating = true;
        update.setEnabled(false);
        new Thread(() -> {
            try {
                SelfUpdate.prepare(r, root, line -> SwingUtilities.invokeLater(() -> status.setText(line)));
                SwingUtilities.invokeLater(() -> {
                    status.setText(t("Uruchamianie nowej wersji…", "Starting the new version…"));
                    javax.swing.Timer quit = new javax.swing.Timer(600, e -> System.exit(0));
                    quit.setRepeats(false);
                    quit.start();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    launcherUpdating = false;
                    update.setEnabled(true);
                    status.setText(" ");
                    JOptionPane.showMessageDialog(frame, String.valueOf(ex.getMessage()), "Patchbay Launcher", JOptionPane.ERROR_MESSAGE);
                });
            }
        }, "launcher-self-update").start();
    }

    private void start() {
        if (patchbayRunning()) {
            JOptionPane.showMessageDialog(frame, t("Patchbay jest już uruchomiony.", "Patchbay is already running."),
                "Patchbay Launcher", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        setBusy(true, t("Sprawdzanie wersji Bitwiga…", "Checking the Bitwig version…"));
        new Thread(() -> {
            String bitwig = Platform.bitwigVersion(), cur = currentVersion();
            Path root = cur == null ? null : folderOf(cur);
            List<String> covered = Compat.versions(root, Platform.suffix());
            boolean unsupported = bitwig != null && !covered.isEmpty() && Compat.target(root, bitwig, Platform.suffix()) == null;
            SwingUtilities.invokeLater(() -> {
                setBusy(false, "");
                if (!unsupported || startAnyway(bitwig, cur, covered)) launch();
            });
        }, "bitwig-check").start();
    }

    /**
     * Patchbay has no map for the installed Bitwig, so it would stop with "exit code 1": says so in words,
     * with the Bitwig versions this Patchbay covers. True when the user starts it all the same.
     */
    private boolean startAnyway(String bitwig, String patchbay, List<String> covered) {
        String list = covered.size() <= 6 ? String.join(", ", covered) : String.join(", ", covered.subList(0, 5)) + " … " + covered.get(covered.size() - 1);
        String message = "<html><body style='width:420px'><p><b>Patchbay " + patchbay + t(" nie obsługuje Twojego Bitwiga ", " doesn't support your Bitwig ") + bitwig + ".</b></p><br>"
            + "<p>" + t("Obsługiwane wersje Bitwiga (", "Supported Bitwig versions (") + Compat.platformName(Platform.suffix()) + "): " + list + "</p><br><p>"
            + t("Z tym Bitwigiem Patchbay się nie uruchomi (zakończy się błędem „exit code 1”). Sprawdź w sekcji Patchbay, czy jest nowsza wersja, albo zainstaluj obsługiwaną wersję Bitwiga.",
                "Patchbay won't start with this Bitwig (it stops with \"exit code 1\"). Check the Patchbay section for a newer version, or install a supported Bitwig version.")
            + "</p></body></html>";
        String cancel = t("Anuluj", "Cancel"), anyway = t("Uruchom mimo to", "Start anyway");
        int pick = JOptionPane.showOptionDialog(frame, message, "Patchbay Launcher", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
            null, new String[] { anyway, cancel }, cancel);
        return pick == 0;
    }

    private void launch() {
        try {
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
        if (Platform.MAC) {
            System.setProperty("apple.awt.application.name", "Patchbay Launcher");
            System.setProperty("apple.awt.application.appearance", Theme.lightChosen() ? "NSAppearanceNameAqua" : "NSAppearanceNameDarkAqua");
        }
        try {
            Theme.loadFonts();
            Theme.palette(Theme.lightChosen());
        } catch (Throwable t) {
            startFailed(t);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                Theme.install();
                new PatchbayLauncher().show();
            } catch (Throwable t) {
                startFailed(t);
            }
        });
    }

    /** What went wrong at start, for the window and for a report: the versions first, then Java's own account. */
    static String failureText(Throwable t) {
        java.io.StringWriter trace = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(trace));
        String info;
        try {
            info = SystemInfo.text();
        } catch (Throwable unknown) {
            info = "Patchbay Launcher " + VERSION + "\nSystem: " + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " (" + System.getProperty("os.arch") + ")\nJava: " + System.getProperty("java.version");
        }
        return info + "\nJava home: " + System.getProperty("java.home") + "\n\n" + trace;
    }

    /**
     * The launcher couldn't open its window: says so instead of closing without a word, keeps the details in
     * logs/start-error.log, and offers to copy them or to show the folder. Plain Swing, since the look may be the thing that failed.
     */
    private static void startFailed(Throwable t) {
        String text = failureText(t);
        System.err.println(text);
        Path log = data().resolve("logs").resolve("start-error.log");
        try {
            Files.createDirectories(log.getParent());
            Files.writeString(log, text);
        } catch (Exception ignored) {
            // the window below still shows the details
        }
        if (GraphicsEnvironment.isHeadless()) { System.exit(1); return; }
        String copy = t("Skopiuj szczegóły", "Copy the details"), folder = t("Pokaż folder z logami", "Show the log folder"), close = t("Zamknij", "Close");
        String first = t.toString();
        String message = t("Patchbay Launcher nie mógł się uruchomić.", "Patchbay Launcher couldn't start.") + "\n\n"
            + (first.length() > 300 ? first.substring(0, 300) + "…" : first) + "\n\n"
            + t("Szczegóły są w pliku: ", "The details are in the file: ") + log;
        while (true) {
            int pick = JOptionPane.showOptionDialog(null, message, "Patchbay Launcher", JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE,
                null, new String[] { copy, folder, close }, close);
            if (pick == 0) Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(text), null);
            else if (pick == 1) Platform.open(log.getParent().toString());
            else break;
        }
        System.exit(1);
    }

    /** Offline checks (the 4th argument is a folder of fixtures: two release lists and two mod ZIPs): java -Dlauncher.data=<empty temp folder> -jar launcher.jar --selftest <BML zip> <its version>. */
    static final class SelfTest {
        /** ⌘V and friends paste in text fields on macOS (Swing's own look only knows Ctrl). */
        static void editKeysTest() {
            UIDefaults d = new UIDefaults();
            InputMap im = new InputMap();
            d.put("TextField.focusInputMap", im);
            Theme.macEditKeys(d);
            int cmd = java.awt.event.InputEvent.META_DOWN_MASK;
            check(javax.swing.text.DefaultEditorKit.pasteAction.equals(im.get(KeyStroke.getKeyStroke('V', cmd))), "Cmd+V pastes");
            check(javax.swing.text.DefaultEditorKit.copyAction.equals(im.get(KeyStroke.getKeyStroke('C', cmd))), "Cmd+C copies");
            check(javax.swing.text.DefaultEditorKit.selectAllAction.equals(im.get(KeyStroke.getKeyStroke('A', cmd))), "Cmd+A selects all");
            check("polarity/bitwig-mods".equals(Sources.normalizeRepo("https://github.com/polarity/bitwig-mods")), "a pasted github.com link becomes owner/name");
            check("polarity/bitwig-mods".equals(Sources.normalizeRepo("github.com/polarity/bitwig-mods/")), "a link without https:// too");
            check(Themes.failedNote(List.of()).isEmpty() && Themes.failedNote(List.of("a/b: x")).contains("a/b: x"), "a theme source that can't be read is named");
        }

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
            check(Platform.releasePattern().equals("BML-*-" + (Platform.WINDOWS ? "windows" : Platform.LINUX ? "linux" : "macos") + ".zip"), "release file for this system: " + Platform.releasePattern());
            if (Platform.LINUX) {
                check(Platform.patchbaySettings().toString().endsWith("bml") && !Platform.patchbaySettings().toString().contains("Library"),
                    "Linux: Patchbay settings in the XDG config folder: " + Platform.patchbaySettings());
                String entry = Platform.desktopEntry(Path.of("/home/a b/x$y/patchbay-launcher"), Path.of("/i/p.png"));
                check(entry.contains("Exec=\"/home/a b/x\\\\$y/patchbay-launcher\"") && entry.contains("StartupWMClass=PatchbayLauncher"),
                    "Linux: menu entry quotes the script's path");
            }
            check(I18n.CODES.size() == 4 && I18n.needs("A", "b", "1.0", null).contains("b"), "four languages, requirement note built");
            if (args.length < 3) { System.out.println("SELFTEST_OK (logic only)"); return; }

            Path zip = Path.of(args[1]);
            String version = args[2];
            check(System.getProperty("launcher.data") != null, "launcher.data is set for the test");
            check(currentVersion() == null, "nothing installed at first");
            installZip(zip, version, s -> { });
            check(version.equals(currentVersion()), "installed version is current");
            Path ui = folderOf(version).resolve(Platform.startScript());
            // compatibility with the user's Bitwig, from the map packs this Patchbay ships
            Path installed = folderOf(version);
            String sys = Platform.suffix();
            Compat.Target t614 = Compat.target(installed, "6.1.4", sys);
            check(t614 != null && t614.build().version().equals("6.1.4"), "map pack for Bitwig 6.1.4 on " + sys + ": " + (t614 == null ? null : t614.packId()));
            check(Compat.versions(installed, sys).contains("6.1.4"), "Bitwig versions with a map: " + Compat.versions(installed, sys));
            String other = sys.equals("windows") ? "macos" : "windows";
            check(Compat.check(t614, "6.1.4", sys, "6.1.3", other, other.equals("macos") ? "universal" : "x64").state() == Compat.State.TRANSLATED,
                "mod built on " + other + " 6.1.3: translated");
            check(Compat.check(t614, "6.1.4", sys, "6.1.4", sys, t614 == null ? null : t614.build().arch()).state() == Compat.State.NATIVE, "mod built on this Bitwig: native");
            check(Compat.check(t614, "6.1.4", sys, "5.3.13", "windows", "x64").state() == Compat.State.UNAVAILABLE, "mod built on a Bitwig the map doesn't know: not available");
            check(Compat.target(installed, "6.1.5", sys) == null
                && Compat.check(null, "6.1.5", sys, "6.1.3", "windows", "x64").state() == Compat.State.UNAVAILABLE, "no map for Bitwig 6.1.5: nothing loads");
            check(Compat.check(t614, null, sys, "6.1.3", "windows", "x64").state() == Compat.State.UNKNOWN, "Bitwig unknown: no verdict");
            check("6.1".equals(Compat.norm("6.1.0")) && "6.1.4".equals(Compat.norm("6.1.4")), "6.1.0 counts as 6.1");
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
            switchOffTest(folderOf(version).resolve("mods"));
            if (args.length >= 4 && Files.isRegularFile(Path.of(args[3], "csabacsaba_bw6themes.tree.json"))) themesTest(Path.of(args[3]));
            sourcesTest();
            selfUpdateTest();
            editKeysTest();
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

            Devices.setOn(normalizer, false);
            check(Devices.rank(normalizer) == 3 && !Files.exists(Devices.folderOf(normalizer).resolve("Normalizer.bwdevice")), "switched off: file renamed, shown as off");
            Devices.setOn(normalizer, true);
            check(Devices.rank(normalizer) == 0, "switched on again: the same file back");
            Devices.download(normalizer, (i, target) -> Files.copy(sample, target));
            Devices.setOn(normalizer, false);
            Devices.download(normalizer, (i, target) -> Files.copy(sample, target));
            check(Devices.rank(normalizer) == 1 && !Files.exists(Devices.offFile(normalizer)), "downloading a switched-off device switches it on");
            Devices.remove(normalizer);
            check(Devices.rank(normalizer) == 2 && !Files.exists(Devices.folderOf(normalizer)), "uninstalled: file and empty folder gone");
        }

        static void themesTest(Path fixtures) throws Exception {
            List<Themes.Item> bw6 = Themes.parseTree(Files.readString(fixtures.resolve("csabacsaba_bw6themes.tree.json")), "csabacsaba/bw6themes", "");
            check(bw6.stream().map(Themes.Item::name).toList().equals(List.of("black", "blackfire", "live12", "s1")), "four theme folders in bw6themes: " + bw6.stream().map(Themes.Item::name).toList());
            Themes.Item live = bw6.get(2);
            check(live.isFolder() && live.files().size() == 1 && live.files().get(0).path().equals("bw6b4/live12/theme.bte")
                && "bw6b4/live12/live_screenshot.png".equals(live.preview()), "a theme folder brings its .bte; the screenshot is only a preview");
            List<Themes.Item> mixed = Themes.parseTree(Files.readString(fixtures.resolve("test_lone-themes.tree.json")), "test/lone-themes", "");
            check(mixed.stream().map(Themes.Item::name).toList().equals(List.of("Midnight", "Neon Glow", "Sand", "Sea")), "lone .bte files and folders: " + mixed.stream().map(Themes.Item::name).toList());
            Themes.Item midnight = mixed.get(0), neon = mixed.get(1);
            check(!midnight.isFolder() && "Midnight.png".equals(midnight.preview()), "lone file with a picture of the same name");
            check(neon.files().stream().map(Themes.File::path).sorted().toList().equals(List.of("Neon Glow/mod.json", "Neon Glow/overlays/knob.svg", "Neon Glow/theme.bte"))
                && "Neon Glow/preview.png".equals(neon.preview()), "folder theme keeps overlays and mod.json, not its preview");
            check(Themes.parseTree(Files.readString(fixtures.resolve("test_lone-themes.tree.json")), "test/lone-themes", "pair").size() == 2, "folder filter");
            check(Themes.derivedId("Neon Glow").equals("neon-glow") && Themes.derivedId("Café  Noir!").equals("cafe-noir")
                && Themes.derivedId("2024 dark").equals("c-2024-dark") && Themes.derivedId("--live12--").equals("live12"), "theme IDs as Patchbay makes them");

            String tree = "{\"tree\":[{\"path\":\"nord.json\",\"type\":\"blob\",\"sha\":\"a\",\"size\":1},{\"path\":\"Frost.bte\",\"type\":\"blob\",\"sha\":\"c\",\"size\":1}]}";
            check(Themes.parseTree(tree, "o/r", "").stream().map(Themes.Item::name).toList().equals(List.of("Frost")), "only .bte files are themes (.json files are not listed)");
            String six = "// x\nGradient: true\nGrey 0: #101010\nAccent (default): #ff6a1a\n" + java.util.stream.IntStream.range(0, 12).mapToObj(n -> "Colour " + n + ": #10203" + (n % 10)).collect(java.util.stream.Collectors.joining("\n"));
            check(Themes.refusal(six) == null && Themes.refusal(six.replace("Grey 0", "Panel body")) != null && Themes.refusal("hello") != null, "a .bte file is added only when it has Bitwig 6's main colours");
            Path comps = Files.createTempDirectory("themes-local");
            Path picked = Files.createTempDirectory("themes-pick").resolve("My Theme.bte");
            Files.writeString(picked, six);
            Themes.addFile(comps, picked);
            Path old = picked.resolveSibling("Old.bte");
            Files.writeString(old, six.replace("Grey 0", "Panel body"));
            boolean oldRefused = false;
            try { Themes.addFile(comps, old); } catch (IOException e) { oldRefused = true; }
            boolean wrongType = false;
            try { Themes.addFile(comps, Files.writeString(picked.resolveSibling("x.json"), six)); } catch (IOException e) { wrongType = true; }
            List<Themes.Item> mine = Themes.localThemes(comps, List.of());
            check(oldRefused && wrongType && mine.size() == 1 && mine.get(0).name().equals("My Theme") && mine.get(0).isLocal()
                && Themes.rank(comps, mine.get(0)) == 1 && Themes.idOf(comps, mine.get(0)).equals("my-theme"), "a theme added from a file is listed as a local one; old and non-.bte files are refused");
            check(Categories.of("Arranger: Add Automation Points").equals("Arranger") && Categories.of("piano roll: Strum").equals("Piano Roll")
                && Categories.of("Browser (side): Hide Parts").equals("Browser") && Categories.of("Helper: X").equals("Framework")
                && Categories.of("Superpowers: Y").equals("Other") && Categories.of("Scale Fold").equals("Other"),
                "categories: the fixed list first, earlier names put in their place, anything else Other");
            check(Categories.ALL.size() == 15 && Categories.ALL.stream().allMatch(c -> Categories.class.getResource("/icons/" + c.icon() + ".png") != null)
                && Categories.class.getResource("/icons/favorites.png") != null && Categories.class.getResource("/icons/all.png") != null, "every category has its icon in the jar");
            check(Mods.category("Arranger: Add Automation Points").equals("Arranger") && Mods.category("Scale Fold").isEmpty() && Mods.category(null).isEmpty(), "a mod's category is what stands before the colon in its name");
            Mods.Available dated = Mods.parseCatalog("[{\"html_url\":\"https://x\",\"published_at\":\"2026-01-02T00:00:00Z\",\"assets\":[{\"name\":\"a-mod-1.0.0.zip\",\"browser_download_url\":\"https://x/a-mod-1.0.0.zip\",\"created_at\":\"2026-03-04T05:06:07Z\"},"
                + "{\"name\":\"b-mod-1.0.0.zip\",\"browser_download_url\":\"https://x/b-mod-1.0.0.zip\"}]}]", "o/r").get("a-mod");
            check(dated.published().startsWith("2026-03-04") && Mods.parseCatalog("[{\"published_at\":\"2026-01-02T00:00:00Z\",\"assets\":[{\"name\":\"b-mod-1.0.0.zip\",\"browser_download_url\":\"https://x/b-mod-1.0.0.zip\"}]}]", "o/r").get("b-mod").published().startsWith("2026-01-02"),
                "a mod version's date: its file's, else its release's");
            check(Mods.matches("", "Arranger: Add Automation Points", "add-automation-points") && Mods.matches("  ARR  points ", "Arranger: Add Automation Points", "x")
                && Mods.matches("automation-points", "Arranger", "add-automation-points") && !Mods.matches("piano", "Arranger: Add Automation Points", "add-automation-points")
                && Mods.matches("shaboo", "Arranger: Add Automation Points", "add-automation-points", "shaboogen"), "Search finds mods by every typed word, in the name, the id or the author");
            check(failureText(new IllegalStateException("boom")).startsWith("Patchbay Launcher " + VERSION) && failureText(new IllegalStateException("boom")).contains("IllegalStateException: boom"), "a start failure is described with the versions and Java's account");
            Map<String, Color> parsed = Themes.colours("// c\nGrey 0: #101010\nAccent (default): #ff6a1a80\nShort: #abc\nBad: #12\nNot a colour\n");
            check(parsed.size() == 3 && parsed.get("Grey 0").equals(new Color(0x101010)) && parsed.get("Accent (default)").getAlpha() == 0x80 && parsed.get("Short").equals(new Color(0xAABBCC)), "a theme's colours are read by name");
            check(Themes.drawnPreview(six) != null && Themes.drawnPreview(six).getWidth() == 560 && Themes.drawnPreview("hello") == null
                && comps.resolve("My Theme.bte").equals(Themes.bteOnDisk(comps, mine.get(0))), "a theme without a screenshot gets a picture of its own colours");
            Path bset = Files.createTempDirectory("backup-settings"), bsrc = Files.createTempDirectory("backup-src").resolve("sources.json"), bzip = Files.createTempDirectory("backup-zip").resolve("b.zip");
            Files.writeString(bset.resolve("enabled-mods.properties"), "mod.a=true\n");
            Files.writeString(bset.resolve("selected-components.properties"), "component.x=my-theme\n");
            Files.writeString(bset.resolve("bml.properties"), "bitwig=/Applications/Bitwig Studio.app\n");
            Files.writeString(bsrc, "{\"sources\":[]}");
            check(Backup.save(bzip, bset, comps, bsrc) == 4, "a backup holds the two Patchbay settings files, the sources and the theme files");
            Path rset = Files.createTempDirectory("restore-settings"), rcomp = Files.createTempDirectory("restore-comp"), rsrc = Files.createTempDirectory("restore-src").resolve("sources.json");
            check(Backup.restore(bzip, rset, rcomp, rsrc) == 4 && Files.readString(rset.resolve("enabled-mods.properties")).equals("mod.a=true\n")
                && Files.exists(rcomp.resolve("My Theme.bte")) && Files.exists(rsrc) && !Files.exists(rset.resolve("bml.properties")), "restoring puts them back, and nothing else");
            check(Backup.restore(bzip, rset, null, rsrc) == 3 && Backup.target("components/../../x", rset, rcomp, rsrc) == null && Backup.target("patchbay/bml.properties", rset, rcomp, rsrc) == null, "without Patchbay the theme files are left out; unsafe or unknown entries are skipped");
            boolean notBackup = false;
            try { Backup.restore(picked, rset, rcomp, rsrc); } catch (IOException e) { notBackup = true; }
            check(notBackup, "a file that isn't a backup is refused");
            check(Themes.linkedRepos("[a](https://github.com/lenninst/Macchiato_BitwigTheme.git) [b](https://github.com/o/r) https://github.com/Berikai/awesome-bitwig-themes https://github.com/o/r", "Berikai/awesome-bitwig-themes")
                .equals(List.of("lenninst/Macchiato_BitwigTheme", "o/r")), "a list repository's links: each once, without itself");

            if (System.getProperty("launcher.bmlsettings") == null) System.setProperty("launcher.bmlsettings", data().resolve("bml-settings").toString());
            Path components = Themes.components();
            check(components != null && components.startsWith(folderOf(currentVersion())), "themes go into the active Patchbay's components/");
            Themes.Fetcher local = (repo, path, target) -> Files.copy(fixtures.resolve(repo.replace('/', '_')).resolve(path), target, StandardCopyOption.REPLACE_EXISTING);
            check(Themes.rank(components, live) == 2, "not installed yet");
            Themes.install(components, live, local);
            check(Files.isRegularFile(components.resolve("live12/theme.bte")) && !Files.exists(components.resolve("live12/live_screenshot.png")), "installed as components/live12/theme.bte");
            check(Themes.rank(components, live) == 1, "installed and up to date");
            Themes.install(components, midnight, local);
            Themes.install(components, neon, local);
            check(Files.isRegularFile(components.resolve("Midnight.bte")) && Files.isRegularFile(components.resolve("Neon Glow/overlays/knob.svg")), "lone file and folder with overlays installed");
            Files.writeString(components.resolve("live12/theme.bte"), "older");
            check(Themes.rank(components, live) == 0, "changed theme shows as update");
            boolean refused = false;
            try { Themes.install(components, live, (repo, path, target) -> Files.writeString(target, "tampered")); } catch (IOException e) { refused = true; }
            check(refused && Files.readString(components.resolve("live12/theme.bte")).equals("older"), "wrong hash refused, installed theme kept");
            try (Stream<Path> st = Files.list(components)) { check(st.noneMatch(x -> x.getFileName().toString().startsWith(".theme-")), "no staging folder left"); }

            check(Themes.idOf(components, neon).equals("neon-glow-theme") && Themes.idOf(components, live).equals("live12") && Themes.idOf(components, midnight).equals("midnight"), "ID from mod.json, else from the name");
            Path settings = Devices.patchbaySettings();
            Files.createDirectories(settings);
            Files.writeString(settings.resolve("enabled-mods.properties"), "mod.other-mod=true\n");
            Files.writeString(settings.resolve("selected-components.properties"), "component.x.group.y.ids=z\n");
            check(Themes.activeId() == null, "no theme chosen yet");
            Themes.choose("live12");
            check("live12".equals(Themes.activeId()), "theme chosen");
            String enabled = Files.readString(settings.resolve("enabled-mods.properties")), selected = Files.readString(settings.resolve("selected-components.properties"));
            check(enabled.contains("mod.other-mod=true") && enabled.contains("mod.bte-theme-loader=true") && selected.contains("component.x.group.y.ids=z")
                && selected.contains("component.bte-theme-loader.group.themes.ids=live12"), "Patchbay's other settings kept");
            Themes.choose(null);
            check(Themes.activeId() == null && Files.readString(settings.resolve("enabled-mods.properties")).contains("mod.bte-theme-loader=false"), "themes turned off");
            Themes.remove(Themes.target(components, neon));
            check(!Files.exists(components.resolve("Neon Glow")) && Themes.rank(components, neon) == 2, "theme removed");
        }

        static void selfUpdateTest() throws Exception {
            String json = "{\"tag_name\":\"v9.9.9\",\"html_url\":\"https://github.com/arius77R/patchbay-launcher/releases/tag/v9.9.9\",\"assets\":["
                + "{\"name\":\"Patchbay-Launcher-9.9.9-macos.dmg\",\"browser_download_url\":\"https://x/Patchbay-Launcher-9.9.9-macos.dmg\"},"
                + "{\"name\":\"Patchbay-Launcher-9.9.9-macos.zip\",\"browser_download_url\":\"https://x/Patchbay-Launcher-9.9.9-macos.zip\",\"digest\":\"sha256:AB12\"},"
                + "{\"name\":\"Patchbay-Launcher-9.9.9-windows.zip\",\"browser_download_url\":\"https://x/Patchbay-Launcher-9.9.9-windows.zip\"},"
                + "{\"name\":\"Patchbay-Launcher-9.9.9-linux.tar.gz\",\"browser_download_url\":\"https://x/Patchbay-Launcher-9.9.9-linux.tar.gz\"},"
                + "{\"name\":\"SHA256SUMS.txt\",\"browser_download_url\":\"https://x/SHA256SUMS.txt\"}]}";
            SelfUpdate.Release r = SelfUpdate.parse(json);
            check(r != null && r.version().equals("9.9.9") && r.url().endsWith(SelfUpdate.assetSuffix()), "launcher release: this system's package " + (r == null ? null : r.url()));
            check(!Platform.MAC || "ab12".equals(r.sha256()), "launcher release: SHA-256 from GitHub's digest");
            check(SelfUpdate.newer(r) && !SelfUpdate.newer(new SelfUpdate.Release(VERSION, null, "x", null, null)), "a newer launcher is offered, the same one is not");
            Path tmp = Files.createTempDirectory("su");
            Path root;
            if (Platform.MAC) {
                root = tmp.resolve("Patchbay Launcher.app");
                Files.createDirectories(root.resolve("Contents/Resources"));
            } else {
                root = tmp.resolve(SelfUpdate.packageRoot());
                Files.createDirectories(root.resolve("app"));
                Files.writeString(root.resolve(Platform.WINDOWS ? "Patchbay Launcher.exe" : "patchbay-launcher"), "");
            }
            Path jar = SelfUpdate.jarIn(root);
            Files.writeString(jar, "");
            check(root.equals(SelfUpdate.rootFor(jar)), "install found from launcher.jar: " + SelfUpdate.rootFor(jar));
            check(SelfUpdate.rootFor(tmp.resolve("build").resolve("launcher.jar")) == null, "a jar outside an install can't update itself");
            String unix = SelfUpdate.unixScript(4242, root, tmp.resolve("new"), tmp.resolve("stage"));
            String win = SelfUpdate.windowsScript(4242, root, tmp.resolve("new"), tmp.resolve("stage"));
            check(unix.contains("kill -0 4242") && unix.contains(".old-update") && unix.contains(Platform.MAC ? "/usr/bin/open" : "nohup"), "swap script for macOS and Linux");
            check(win.contains("Wait-Process -Id 4242") && win.contains("Start-Process") && win.contains("Rename-Item"), "swap script for Windows");
            check(SelfUpdate.unixScript(1, Path.of("/tmp/it's/X.app"), Path.of("/n"), Path.of("/s")).contains("'/tmp/it'\\''s/X.app'"), "quotes in folder names are escaped");
        }

        static void sourcesTest() throws Exception {
            check(Sources.all().size() == 5 && Sources.patchbay().repo().equals("shaboogen-dev/Patchbay"), "five built-in sources, Patchbay from shaboogen-dev");
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
            check(back.size() == 7 && !back.get(2).enabled() && back.get(5).repo().equals("me/my-mods"), "sources saved and read back");
            check(Sources.enabled(Sources.MODS).stream().map(Sources.Source::repo).toList().equals(List.of("shaboogen-dev/Mods", "me/my-mods")), "a switched-off source is left out");
            check(Sources.patchbay().repo().equals("shaboogen-dev/Patchbay"), "Patchbay keeps the source that's on");
            String json = "[{\"tag_name\":\"v2\",\"assets\":[{\"name\":\"BML-2.0.0-macos.zip\",\"browser_download_url\":\"https://x/BML-2.0.0-macos.zip\"},{\"name\":\"BML-2.0.0-linux.zip\",\"browser_download_url\":\"https://x/BML-2.0.0-linux.zip\"}]}]";
            List<Release> r = parseReleases(json, Sources.glob("BML-*-macos.zip"));
            check(r.size() == 1 && r.get(0).version().equals("2.0.0"), "Patchbay releases read with the source's pattern");
            List<Sources.Source> edited = new ArrayList<>(Sources.BUILTIN);
            edited.set(2, new Sources.Source("mods-polarity", Sources.MODS, "me/bitwig-mods-fork", "*.zip", true, true));
            edited.removeIf(x -> x.id().equals("devices-polarity"));
            Sources.save(edited);
            List<Sources.Source> got = Sources.all();
            Sources.Source fork = got.stream().filter(x -> x.id().equals("mods-polarity")).findFirst().orElseThrow();
            check(fork.builtin() && fork.repo().equals("me/bitwig-mods-fork") && Sources.changed(fork), "a built-in source can be changed and stays built-in");
            check(got.stream().noneMatch(x -> x.id().equals("devices-polarity")) && Sources.removedBuiltins().equals(Set.of("devices-polarity")), "a built-in source can be removed");
            check(Sources.enabled(Sources.DEVICES).isEmpty() && Sources.enabled(Sources.MODS).stream().anyMatch(x -> x.repo().equals("me/bitwig-mods-fork")), "sections follow the changes");
            Sources.save(Sources.BUILTIN);
            check(Sources.all().equals(Sources.BUILTIN) && Sources.removedBuiltins().isEmpty(), "back to the built-in sources");
        }

        /** Switching mods off and on in Patchbay's list, the warning for mods others need, and uninstalling. */
        static void switchOffTest(Path mods) throws Exception {
            check(System.getProperty("launcher.bmlsettings") != null, "launcher.bmlsettings is set for the test");
            Files.createDirectories(mods.resolve("needed-lib"));
            Files.writeString(mods.resolve("needed-lib/mod.json"), "{\"mod\":{\"id\":\"needed-lib\",\"name\":\"Framework: Lib\",\"version\":\"1.0.0\"}}");
            Files.createDirectories(mods.resolve("user-mod"));
            Files.writeString(mods.resolve("user-mod/mod.json"), "{\"mod\":{\"id\":\"user-mod\",\"name\":\"Arranger: User\",\"version\":\"1.0.0\"},\"requires\":[{\"id\":\"needed-lib\"}]}");
            Mods.setEnabled("needed-lib", true);
            Mods.setEnabled("user-mod", true);
            check(Mods.enabled("needed-lib") && Mods.enabled("user-mod"), "mods switched on in Patchbay's list");
            check(Mods.neededBy("needed-lib", mods).equals(List.of("Arranger: User")), "a switched-on mod that needs it is named");
            Mods.setEnabled("user-mod", false);
            check(!Mods.enabled("user-mod") && Files.isRegularFile(mods.resolve("user-mod/mod.json")), "switched off: unticked, files kept");
            check(Mods.neededBy("needed-lib", mods).isEmpty(), "a switched-off mod doesn't count as needing it");
            Mods.uninstall("needed-lib", mods);
            check(!Files.exists(mods.resolve("needed-lib")) && !Files.readString(Mods.enabledFile()).contains("needed-lib"), "uninstalled: folder and tick gone");
            boolean refused = false;
            try { Mods.uninstall("../x", mods); } catch (IOException e) { refused = true; }
            check(refused, "an ID outside mods is refused");
            Mods.uninstall("user-mod", mods);
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

            Mods.Available wrong = new Mods.Available("scale-fold", "0.1.0-dev.4", "https://x", "0".repeat(64), "t", null, "");
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
