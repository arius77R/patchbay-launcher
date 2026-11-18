import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where the launcher gets things: GitHub repositories in three groups. Patchbay comes from one
 * repository at a time (its releases), mods from the releases of any number of repositories,
 * devices from the files of any number of repositories. The built-in sources can be switched off
 * but not removed; the user's own are kept in sources.json in the launcher's folder.
 */
final class Sources {
    private Sources() { }

    static final String PATCHBAY = "patchbay", MODS = "mods", DEVICES = "devices", THEMES = "themes";
    static final List<String> GROUPS = List.of(PATCHBAY, MODS, DEVICES, THEMES);

    /** filter: a file pattern for releases (* = any text; for Patchbay it marks the version), a folder for devices and themes. */
    record Source(String id, String group, String repo, String filter, boolean builtin, boolean enabled) {
        Source withEnabled(boolean on) { return new Source(id, group, repo, filter, builtin, on); }
    }

    static final List<Source> BUILTIN = List.of(
        new Source("patchbay", PATCHBAY, "shaboogen-dev/Patchbay", Platform.releasePattern(), true, true),
        new Source("mods-shaboogen", MODS, "shaboogen-dev/Mods", "*.zip", true, true),
        new Source("mods-polarity", MODS, "polarity/bitwig-mods", "*.zip", true, true),
        new Source("devices-polarity", DEVICES, "polarity/bitwig-devices", "", true, true),
        new Source("themes-csabacsaba", THEMES, "csabacsaba/bw6themes", "", true, true));

    static Path file() { return PatchbayLauncher.data().resolve("sources.json"); }

    /** The built-in source with this ID as the launcher ships it, or null. */
    static Source original(String id) {
        return BUILTIN.stream().filter(b -> b.id().equals(id)).findFirst().orElse(null);
    }

    /** A built-in source the user has changed (group, repository or files). */
    static boolean changed(Source s) {
        Source o = s.builtin() ? original(s.id()) : null;
        return o != null && !(o.group().equals(s.group()) && o.repo().equals(s.repo()) && o.filter().equals(s.filter()));
    }

    /** IDs of the built-in sources the user removed. */
    static Set<String> removedBuiltins() {
        Set<String> out = new HashSet<>();
        for (Source b : BUILTIN) out.add(b.id());
        for (Source s : all()) out.remove(s.id());
        return out;
    }

    /**
     * Built-in sources first (with the user's on/off and changes; removed ones left out), then the user's own, in the order added.
     */
    static List<Source> all() {
        Map<String, Boolean> switched = new HashMap<>();
        Map<String, Source> changes = new HashMap<>();
        Set<String> removed = new HashSet<>();
        List<Source> own = new ArrayList<>();
        try {
            if (Files.isRegularFile(file())) {
                Map<String, Object> root = Mods.Json.object(Mods.Json.parse(Files.readString(file())));
                for (Object o : Mods.Json.array(root.get("sources"))) {
                    Map<String, Object> m = Mods.Json.object(o);
                    String id = Mods.Json.text(m, "id"), group = Mods.Json.text(m, "group"), repo = Mods.Json.text(m, "repo");
                    boolean on = !Boolean.FALSE.equals(m.get("enabled"));
                    if (id == null) continue;
                    if (Boolean.TRUE.equals(m.get("builtin"))) {
                        if (Boolean.TRUE.equals(m.get("removed"))) { removed.add(id); continue; }
                        switched.put(id, on);
                        Source shipped = original(id);
                        if (shipped != null && repo != null && normalizeRepo(repo) != null) {
                            String g = group != null && GROUPS.contains(group) ? group : shipped.group(), f = Mods.Json.text(m, "filter");
                            changes.put(id, new Source(id, g, normalizeRepo(repo), f == null ? shipped.filter() : f, true, on));
                        }
                        continue;
                    }
                    if (group == null || !GROUPS.contains(group) || repo == null || normalizeRepo(repo) == null) continue;
                    String filter = Mods.Json.text(m, "filter");
                    own.add(new Source(id, group, normalizeRepo(repo), filter == null ? "" : filter, false, on));
                }
            }
        } catch (IOException | RuntimeException unreadable) {
            // a broken file falls back to the built-in sources
        }
        List<Source> out = new ArrayList<>();
        for (Source b : BUILTIN) {
            if (removed.contains(b.id())) continue;
            out.add(changes.getOrDefault(b.id(), b).withEnabled(switched.getOrDefault(b.id(), true)));
        }
        out.addAll(own);
        return out;
    }

    /** Saves the list; a built-in source missing from it is saved as removed, a changed one with its changes. */
    static void save(List<Source> sources) throws IOException {
        List<Object> list = new ArrayList<>();
        Set<String> present = new HashSet<>();
        for (Source s : sources) {
            present.add(s.id());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.id());
            m.put("builtin", s.builtin());
            m.put("enabled", s.enabled());
            if (!s.builtin() || changed(s)) {
                m.put("group", s.group());
                m.put("repo", s.repo());
                m.put("filter", s.filter());
            }
            list.add(m);
        }
        for (Source b : BUILTIN) {
            if (present.contains(b.id())) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.id());
            m.put("builtin", true);
            m.put("removed", true);
            list.add(m);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema_version", 1.0);
        root.put("sources", list);
        Files.createDirectories(file().getParent());
        Path temp = file().resolveSibling("sources.json.tmp");
        Files.writeString(temp, Mods.Json.write(root) + "\n");
        Files.move(temp, file(), StandardCopyOption.REPLACE_EXISTING);
    }

    // ---------- recommended sources ----------

    // ---------- sources whose repository is gone ----------

    /** "group repo" of the sources GitHub said don't exist (HTTP 404) the last time each was read. */
    private static final Set<String> MISSING = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Runs (on the event thread) when the set above changes, so the Sources section can show it. */
    static volatile Runnable missingChanged = () -> { };

    /** Notes what reading a source's repository said: gone (404) or there; anything else (no network, rate limit) leaves it as it was. */
    static void answered(String group, String repo, IOException failure) {
        String key = group + " " + repo.toLowerCase(Locale.ROOT);
        boolean change = failure instanceof PatchbayLauncher.Gone ? MISSING.add(key) : failure == null && MISSING.remove(key);
        if (change) SwingUtilities.invokeLater(missingChanged);
    }

    static boolean missing(Source s) { return MISSING.contains(s.group() + " " + s.repo().toLowerCase(Locale.ROOT)); }

    static Set<String> missingRepos(String group) {
        Set<String> out = new HashSet<>();
        for (Source s : enabled(group)) if (missing(s)) out.add(s.repo());
        return out;
    }

    /** The list of sources the launcher's author keeps on GitHub: sources.json in this repository (Polecane). */
    static final String LIST_REPO = "arius77R/bitwig-mods-list";

    /** A repository on GitHub to add as a source: from the author's list or found by its topic. */
    record Suggestion(String group, String repo, String note) { }

    /**
     * Every source on the author's list (LIST_REPO/sources.json), the ones the user has too. The list lives
     * on GitHub, so a new source reaches everyone without a new launcher version. No list there yet means
     * none: an empty list, not an error.
     */
    static List<Suggestion> listed() throws IOException {
        String f = System.getProperty("launcher.fixtures"); // offline test
        String json;
        if (f != null) {
            Path fx = Path.of(f).resolve("launcher-sources.json");
            if (!Files.isRegularFile(fx)) return List.of();
            json = Files.readString(fx);
        } else {
            try {
                json = PatchbayLauncher.httpText("https://raw.githubusercontent.com/" + LIST_REPO + "/HEAD/sources.json");
            } catch (IOException none) {
                if (String.valueOf(none.getMessage()).contains("HTTP 404")) return List.of();
                throw none;
            }
        }
        Set<String> seen = new HashSet<>();
        List<Suggestion> out = new ArrayList<>();
        for (Object o : Mods.Json.array(Mods.Json.object(Mods.Json.parse(json)).get("sources"))) {
            Map<String, Object> m = Mods.Json.object(o);
            String group = Mods.Json.text(m, "group"), repo = normalizeRepo(String.valueOf(Mods.Json.text(m, "repo")));
            if (group == null || group.equals(PATCHBAY) || !GROUPS.contains(group) || repo == null) continue;
            if (seen.add(group + " " + repo.toLowerCase(Locale.ROOT))) out.add(new Suggestion(group, repo, Mods.Json.text(m, "note")));
        }
        return out;
    }

    /**
     * What a repository's author withdrew because it causes problems (withdrawn.json at its top, PUBLISHING):
     * mod ids ("mods") or device file names ("devices") with the reason. Empty when there is no such file or
     * it can't be had.
     */
    static Map<String, String> withdrawn(String repo, String list) {
        Map<String, String> out = new HashMap<>();
        try {
            String f = System.getProperty("launcher.fixtures"); // offline test
            String json;
            if (f != null) {
                Path fx = Path.of(f).resolve(repo.replace('/', '_') + ".withdrawn.json");
                if (!Files.isRegularFile(fx)) return out;
                json = Files.readString(fx);
            } else {
                json = PatchbayLauncher.httpText("https://raw.githubusercontent.com/" + repo + "/HEAD/withdrawn.json");
            }
            for (Object o : Mods.Json.array(Mods.Json.object(Mods.Json.parse(json)).get(list))) {
                Map<String, Object> m = Mods.Json.object(o);
                String id = Mods.Json.text(m, list.equals("mods") ? "id" : "file"), reason = Mods.Json.text(m, "reason");
                if (id != null && !id.isBlank()) out.put(id.strip(), reason == null ? "" : reason.strip());
            }
        } catch (IOException | RuntimeException none) {
            // no withdrawn.json (404) or not readable: nothing withdrawn
        }
        return out;
    }

    /** GitHub topics an author puts on a repository so the launcher lists it (PUBLISHING), and the group each stands for. */
    static final Map<String, String> TOPICS = new LinkedHashMap<>(Map.of("patchbay-mod", MODS, "patchbay-devices", DEVICES, "patchbay-themes", THEMES));

    /**
     * Repositories on GitHub with one of the launcher's topics, with their description, the ones the user
     * has too. A topic that can't be searched is skipped unless all fail.
     */
    static List<Suggestion> discovered() throws IOException {
        String f = System.getProperty("launcher.fixtures"); // offline test
        Set<String> seen = new HashSet<>();
        List<Suggestion> out = new ArrayList<>();
        IOException failed = null;
        int searched = 0;
        for (Map.Entry<String, String> topic : TOPICS.entrySet()) {
            String json;
            try {
                if (f != null) {
                    Path fx = Path.of(f).resolve("launcher-topic-" + topic.getKey() + ".json");
                    json = Files.isRegularFile(fx) ? Files.readString(fx) : "{\"items\":[]}";
                } else {
                    json = PatchbayLauncher.httpText("https://api.github.com/search/repositories?q=topic:" + topic.getKey() + "&sort=updated&per_page=100");
                }
                searched++;
            } catch (IOException ex) {
                failed = ex;
                continue;
            }
            for (Object o : Mods.Json.array(Mods.Json.object(Mods.Json.parse(json)).get("items"))) {
                Map<String, Object> m = Mods.Json.object(o);
                String repo = normalizeRepo(String.valueOf(Mods.Json.text(m, "full_name")));
                if (repo == null || Boolean.TRUE.equals(m.get("archived")) || Boolean.TRUE.equals(m.get("fork"))) continue;
                if (seen.add(topic.getValue() + " " + repo.toLowerCase(Locale.ROOT))) out.add(new Suggestion(topic.getValue(), repo, Mods.Json.text(m, "description")));
            }
        }
        if (searched == 0 && failed != null) throw failed;
        out.sort(Comparator.comparing((Suggestion x) -> GROUPS.indexOf(x.group())).thenComparing(x -> x.repo().toLowerCase(Locale.ROOT)));
        return out;
    }

    /** Adds a recommended source, switched on, with the usual files for its group. */
    static void add(Suggestion s) throws IOException {
        List<Source> list = new ArrayList<>(all());
        list.add(new Source("own-" + System.currentTimeMillis(), s.group(), s.repo(), inRepository(s.group()) ? "" : "*.zip", false, true));
        save(list);
    }

    static List<Source> enabled(String group) {
        return all().stream().filter(s -> s.group().equals(group) && s.enabled()).toList();
    }

    /** The one Patchbay source in use; the built-in one when none is switched on. */
    static Source patchbay() {
        List<Source> on = enabled(PATCHBAY);
        return on.isEmpty() ? BUILTIN.get(0) : on.get(0);
    }

    static final Pattern REPO = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})/[A-Za-z0-9._-]{1,100}");

    /** "owner/name" from what the user typed or pasted (a github.com link works too); null when it isn't one. */
    static String normalizeRepo(String typed) {
        if (typed == null) return null;
        String s = typed.strip();
        s = s.replaceFirst("^(?i)(https?://)?(www\\.)?github\\.com/", "");
        s = s.replaceFirst("\\.git$", "");
        String[] parts = s.split("/");
        if (parts.length < 2) return null;
        String repo = parts[0] + "/" + parts[1];
        return REPO.matcher(repo).matches() ? repo : null;
    }

    /** A file pattern as a regular expression; each * becomes a group. Null for an empty pattern. */
    static Pattern glob(String pattern) {
        if (pattern == null || pattern.isBlank()) return null;
        String[] parts = pattern.strip().split("\\*", -1);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) b.append("(.+?)");
            if (!parts[i].isEmpty()) b.append(Pattern.quote(parts[i]));
        }
        return Pattern.compile(b.toString());
    }

    static String groupName(String group) {
        return switch (group) {
            case PATCHBAY -> "Patchbay";
            case MODS -> PatchbayLauncher.t("Mody", "Mods");
            case THEMES -> PatchbayLauncher.t("Motywy", "Themes");
            default -> PatchbayLauncher.t("Urządzenia", "Devices");
        };
    }

    static String describe(Source s) {
        String where = inRepository(s.group())
            ? PatchbayLauncher.t("Pliki w repozytorium", "Files in the repository")
                + (s.filter().isBlank() ? "" : " · " + s.filter())
            : PatchbayLauncher.t("Wydania", "Releases") + " · " + s.filter();
        return (s.builtin() ? changed(s) ? PatchbayLauncher.t("Zmienione przez Ciebie · ", "Changed by you · ") : "" : PatchbayLauncher.t("Dodane przez Ciebie · ", "Added by you · ")) + where;
    }

    /** Devices and themes are files in the repository itself; Patchbay and mods come from releases. */
    static boolean inRepository(String group) { return group.equals(DEVICES) || group.equals(THEMES); }

    /** What GitHub says about a repository: who owns it, what it is, when it last changed. */
    record RepoInfo(String owner, String description, String changed, int stars, String license) { }

    static RepoInfo repoInfo(String repo) throws IOException {
        String f = System.getProperty("launcher.fixtures"); // offline test
        String json = f != null ? Files.readString(Path.of(f).resolve("launcher-repo-" + repo.replace('/', '_') + ".json"))
            : PatchbayLauncher.httpText("https://api.github.com/repos/" + repo);
        Map<String, Object> m = Mods.Json.object(Mods.Json.parse(json));
        Map<String, Object> owner = m.get("owner") instanceof Map<?, ?> ? Mods.Json.object(m.get("owner")) : Map.of();
        Map<String, Object> licence = m.get("license") instanceof Map<?, ?> ? Mods.Json.object(m.get("license")) : Map.of();
        String pushed = Mods.Json.text(m, "pushed_at");
        return new RepoInfo(Objects.requireNonNullElse(Mods.Json.text(owner, "login"), repo.substring(0, repo.indexOf('/'))),
            Mods.Json.text(m, "description"), pushed == null || pushed.length() < 10 ? null : pushed.substring(0, 10),
            m.get("stargazers_count") instanceof Number n ? n.intValue() : 0, Mods.Json.text(licence, "spdx_id"));
    }

    /** What a repository holds for its group: how many mods, devices or themes (0: nothing the launcher can use), or, for a list of theme repositories, how many it links to. */
    record Holds(int count, boolean list) { }

    static Holds found(Suggestion x) throws IOException {
        return switch (x.group()) {
            case MODS -> new Holds(Mods.parseCatalog(Mods.releasePages(x.repo(), System.getProperty("launcher.fixtures")).get(0), x.repo(), glob("*.zip")).size(), false);
            case THEMES -> {
                int own = Themes.fetchRepo(x.repo(), "", false).size();
                yield own > 0 ? new Holds(own, false) : new Holds(Themes.listLinks(x.repo()), true);
            }
            default -> new Holds(Devices.fetchRepo(x.repo(), "").size(), false);
        };
    }

    /** What the repository holds for this group, in a sentence for the user. */
    static String check(Source s) {
        try {
            switch (s.group()) {
                case PATCHBAY -> {
                    List<PatchbayLauncher.Release> found = PatchbayLauncher.parseReleases(
                        PatchbayLauncher.httpText("https://api.github.com/repos/" + s.repo() + "/releases?per_page=100"), glob(s.filter()));
                    return found.isEmpty()
                        ? PatchbayLauncher.t("Repozytorium istnieje, ale w jego wydaniach nie ma plików pasujących do wzoru.", "The repository exists, but its releases have no files matching the pattern.")
                        : PatchbayLauncher.t("Znaleziono wersje Patchbay: ", "Patchbay versions found: ") + found.size()
                            + PatchbayLauncher.t(". Najnowsza: ", ". Latest: ") + found.get(0).version();
                }
                case MODS -> {
                    int n = Mods.parseCatalog(PatchbayLauncher.httpText("https://api.github.com/repos/" + s.repo() + "/releases?per_page=100"),
                        s.repo(), glob(s.filter())).size();
                    return n == 0
                        ? PatchbayLauncher.t("Repozytorium istnieje, ale w jego wydaniach nie ma ZIP-ów modów (nazwa-wersja.zip).", "The repository exists, but its releases have no mod ZIPs (name-version.zip).")
                        : PatchbayLauncher.t("Znaleziono mody: ", "Mods found: ") + n;
                }
                case THEMES -> {
                    int n = Themes.fetchRepo(s.repo(), s.filter()).size();
                    return n == 0
                        ? PatchbayLauncher.t("Repozytorium istnieje, ale nie ma w nim motywów (plików .bte).", "The repository exists, but it holds no themes (.bte files).")
                        : PatchbayLauncher.t("Znaleziono motywy: ", "Themes found: ") + n;
                }
                default -> {
                    List<Devices.Item> items = Devices.fetchRepo(s.repo(), s.filter());
                    return items.isEmpty()
                        ? PatchbayLauncher.t("Repozytorium istnieje, ale nie ma w nim plików .bwdevice, .bwmodulator ani .bwmodule.", "The repository exists, but it holds no .bwdevice, .bwmodulator or .bwmodule files.")
                        : PatchbayLauncher.t("Znaleziono pliki urządzeń: ", "Device files found: ") + items.size();
                }
            }
        } catch (IOException | RuntimeException ex) {
            String m = String.valueOf(ex.getMessage());
            if (m.contains("HTTP 404"))
                return PatchbayLauncher.t("Nie ma takiego repozytorium albo jest prywatne.", "There is no such repository, or it is private.");
            return m;
        }
    }

    // ---------- the section ----------

    static final class Panel extends JPanel {
        private final Runnable changed;
        private final JPanel groups = new JPanel();
        private final JPanel form = new Theme.Card(new BorderLayout(), Theme.FRAME);
        private List<Source> sources;
        /** The source being edited, or a new one (id null). */
        private Source draft;
        private String draftGroup = MODS;
        /** Nothing picked yet: the form only says what to do; no row is marked. */
        private boolean idle = true;
        private final JTextField repoField = new JTextField();
        private final JTextField filterField = new JTextField();
        private final JLabel message = new JLabel();
        /** Brings back the built-in sources as the launcher ships them; shown while one is removed or changed. */
        private final JButton restoreButton = Theme.button(PatchbayLauncher.t("Przywróć wbudowane", "Restore built-in"), "secondary");
        private int nextId;
        /** "Lista źródeł" (the user's sources) or "Polecane" (recommended ones to add), switched by the tabs above. */
        private final CardLayout views = new CardLayout();
        private final JPanel viewCards = new JPanel(views);
        private final JButton activeTab = Theme.button(PatchbayLauncher.t("Aktywne", "Active"), "nav");
        private final JButton foundTab = Theme.button(PatchbayLauncher.t("Lista źródeł", "Source list"), "nav");
        private final JButton recommendedTab = Theme.button(PatchbayLauncher.t("Polecane", "Recommended"), "nav");
        private final JPanel foundList = new JPanel();
        private final JPanel recommended = new JPanel();
        private JComponent listActions;
        /** Refresh, beside the lists from GitHub (Lista źródeł, Polecane): asks GitHub again. */
        private JButton refreshOnline;
        /** "active", "found" or "recommended": the tab on view. */
        private String view = "active";
        /** The author's list of sources (Polecane: the ones not active yet); null until loaded, or when it can't be had (failed). */
        private List<Suggestion> listed;
        private boolean listedFailed;
        /** Repositories found on GitHub by topic; null until searched. */
        private List<Suggestion> discovered;
        private boolean discoveredFailed;
        /** The repository picked in Lista źródeł and in Polecane (group and name), shown in the card beside each. */
        private String pickedFound, pickedRecommended;
        private final JPanel foundCard = new Theme.Card(new BorderLayout(), Theme.FRAME);
        private final JPanel recommendedCard = new Theme.Card(new BorderLayout(), Theme.FRAME);
        /** What GitHub said about a repository, and how many things it holds (-1: couldn't check); filled as they are picked. */
        private final Map<String, RepoInfo> infos = new HashMap<>();
        private final Map<String, Holds> counts = new HashMap<>();
        /** Gets how many recommended sources the user hasn't looked at yet: the red count on the Sources tab. */
        private final java.util.function.IntConsumer unseen;

        /** changed runs after the user changes the sources, so the other sections reload; unseen gets the count of new recommended ones. */
        Panel(Runnable changed, java.util.function.IntConsumer unseen) {
            super(new BorderLayout());
            this.changed = changed;
            this.unseen = unseen;
            sources = all();
            groups.setOpaque(false);
            groups.setLayout(new BoxLayout(groups, BoxLayout.Y_AXIS));
            JScrollPane scroll = new JScrollPane(groups);
            scroll.setBorder(BorderFactory.createEmptyBorder());
            scroll.getViewport().setOpaque(false);
            scroll.setOpaque(false);
            scroll.getVerticalScrollBar().setUnitIncrement(16);
            form.setPreferredSize(new Dimension(316, 10));
            form.setBorder(new EmptyBorder(18, 18, 18, 18));
            JPanel body = new JPanel(new BorderLayout(16, 0));
            body.setOpaque(false);
            body.add(scroll, BorderLayout.CENTER);
            body.add(form, BorderLayout.EAST);
            JButton add = Theme.button(PatchbayLauncher.t("Dodaj źródło", "Add source"), "secondary");
            add.addActionListener(e -> startNew(MODS));
            restoreButton.addActionListener(e -> restoreBuiltins());
            JButton refresh = Theme.button(PatchbayLauncher.t("Odśwież", "Refresh"), "secondary");
            refresh.addActionListener(e -> refreshOnline());
            refreshOnline = refresh;
            listActions = Theme.row(8, restoreButton, add);
            JPanel actions = Theme.row(8, listActions, refresh);

            viewCards.setOpaque(false);
            viewCards.add(body, "active");
            viewCards.add(withCard(foundList, foundCard), "found");
            viewCards.add(withCard(recommended, recommendedCard), "recommended");
            JPanel tabs = Theme.tabStrip(PatchbayLauncher.t("Widok źródeł", "Sources view"));
            activeTab.addActionListener(e -> showView("active"));
            foundTab.addActionListener(e -> showView("found"));
            recommendedTab.addActionListener(e -> showView("recommended"));
            tabs.add(activeTab);
            tabs.add(foundTab);
            tabs.add(recommendedTab);
            JPanel both = new JPanel(new BorderLayout(0, 12));
            both.setOpaque(false);
            both.add(Theme.row(0, tabs), BorderLayout.NORTH);
            both.add(viewCards, BorderLayout.CENTER);
            add(Theme.page(PatchbayLauncher.t("Źródła", "Sources"),
                prose(Theme.label(PatchbayLauncher.t("Repozytoria GitHub, z których Launcher pobiera Patchbay, mody, urządzenia i motywy.",
                    "GitHub repositories the launcher gets Patchbay, mods, devices and themes from."), Theme.text(13f), Theme.MUTED)),
                actions, both, null, null), BorderLayout.CENTER);
            showView("active");
            buildOnline();
            missingChanged = () -> { rebuild(); countUnseen(); };
            repoField.setFont(Theme.mono(13f));
            filterField.setFont(Theme.mono(13f));
            rebuild();
        }

        // ---------- Lista źródeł and Polecane: sources on GitHub to add ----------

        /** A list on the left and the card for the picked repository on the right, as in Aktywne. */
        private static JComponent withCard(JPanel column, JPanel card) {
            card.setPreferredSize(new Dimension(316, 10));
            card.setBorder(new EmptyBorder(18, 18, 18, 18));
            JPanel both = new JPanel(new BorderLayout(16, 0));
            both.setOpaque(false);
            both.add(scrolled(column), BorderLayout.CENTER);
            both.add(card, BorderLayout.EAST);
            return both;
        }

        private static JScrollPane scrolled(JPanel column) {
            column.setOpaque(false);
            column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
            JScrollPane scroll = new JScrollPane(column);
            scroll.setBorder(BorderFactory.createEmptyBorder());
            scroll.getViewport().setOpaque(false);
            scroll.setOpaque(false);
            scroll.getVerticalScrollBar().setUnitIncrement(16);
            return scroll;
        }

        /** Gets the recommended sources and searches GitHub by topic (in the background). */
        void loadOnline() {
            new Thread(() -> {
                List<Suggestion> picked, topics;
                try {
                    picked = listed();
                } catch (IOException | RuntimeException none) {
                    picked = null; // no network or GitHub busy: the tab says the list couldn't be had
                }
                try {
                    topics = discovered();
                } catch (IOException | RuntimeException none) {
                    topics = null;
                }
                List<Suggestion> p = picked, t = topics;
                SwingUtilities.invokeLater(() -> onlineLoaded(p, t));
            }, "online-sources").start();
        }

        private void onlineLoaded(List<Suggestion> picked, List<Suggestion> topics) {
            listedFailed = picked == null;
            listed = picked == null ? List.of() : picked;
            discoveredFailed = topics == null;
            discovered = topics == null ? List.of() : topics;
            buildOnline();
            if (view.equals("recommended") && isShowing()) markSeen(); else countUnseen();
        }

        /** Refresh: the author's list, the topic search and the repositories' cards, from GitHub again. */
        private void refreshOnline() {
            infos.clear();
            counts.clear();
            listed = null;
            discovered = null;
            buildOnline();
            loadOnline();
        }

        /** The Sources section came on screen: recommended ones on view count as seen. */
        void shown() {
            if (view.equals("recommended")) markSeen();
        }

        private void showView(String which) {
            view = which;
            activeTab.putClientProperty("current", which.equals("active"));
            foundTab.putClientProperty("current", which.equals("found"));
            recommendedTab.putClientProperty("current", which.equals("recommended"));
            activeTab.repaint();
            foundTab.repaint();
            recommendedTab.repaint();
            views.show(viewCards, which);
            listActions.setVisible(which.equals("active"));
            refreshOnline.setVisible(!which.equals("active"));
            if (which.equals("recommended")) markSeen();
        }

        private Set<String> seen() {
            return new HashSet<>(List.of(String.valueOf(I18n.setting("sourcesSeen")).split(",")));
        }

        /** The red count on the Polecane tab and on the Sources tab: recommended sources not looked at yet. */
        private void countUnseen() {
            Set<String> seen = seen();
            List<Suggestion> fresh = recommendedNow();
            int n = fresh == null ? 0 : (int) fresh.stream().filter(x -> !seen.contains(x.repo())).count();
            recommendedTab.putClientProperty("badge", n);
            recommendedTab.getAccessibleContext().setAccessibleDescription(n > 0 ? PatchbayLauncher.t("Nowe: ", "New: ") + n : null);
            recommendedTab.revalidate();
            recommendedTab.repaint();
            unseen.accept(n + (int) sources.stream().filter(x -> x.enabled() && missing(x)).count());
        }

        private void markSeen() {
            List<Suggestion> fresh = recommendedNow();
            if (fresh == null) return;
            Set<String> seen = seen();
            for (Suggestion x : fresh) seen.add(x.repo());
            seen.remove("null");
            I18n.remember("sourcesSeen", String.join(",", seen));
            countUnseen();
        }

        private static String key(Suggestion x) { return x.group() + " " + x.repo().toLowerCase(Locale.ROOT); }

        /** The sources the user has (Aktywne), by group and name. */
        private Set<String> active() {
            Set<String> out = new HashSet<>();
            for (Source x : sources) out.add(x.group() + " " + x.repo().toLowerCase(Locale.ROOT));
            return out;
        }

        /** Polecane: the author's list without the sources the user has; null until loaded. */
        private List<Suggestion> recommendedNow() {
            if (listed == null) return null;
            Set<String> active = active();
            return listed.stream().filter(x -> !active.contains(key(x))).toList();
        }

        /**
         * Lista źródeł: everything on GitHub, the author's list and the repositories with a topic, the active
         * ones marked; Polecane: the author's list without the active ones. Each in one framed list like a group
         * of sources, with a line when there are none, and the card for the picked repository beside it.
         */
        private void buildOnline() {
            Set<String> picked = new HashSet<>(), active = active();
            if (listed != null) for (Suggestion x : listed) picked.add(key(x));
            List<Suggestion> everything = null;
            if (listed != null && discovered != null) {
                Map<String, Suggestion> byKey = new LinkedHashMap<>();
                for (Suggestion x : listed) byKey.put(key(x), x);
                for (Suggestion x : discovered) byKey.putIfAbsent(key(x), x);
                everything = new ArrayList<>(byKey.values());
                everything.sort(Comparator.comparing((Suggestion x) -> GROUPS.indexOf(x.group())).thenComparing(x -> x.repo().toLowerCase(Locale.ROOT)));
            }
            List<Suggestion> fresh = recommendedNow();
            fill(foundList, PatchbayLauncher.t("Lista źródeł", "Source list"),
                PatchbayLauncher.t("lista ", "the list ") + LIST_REPO + PatchbayLauncher.t(" i repozytoria z tematem patchbay-mod, patchbay-devices albo patchbay-themes",
                    " and repositories with the topic patchbay-mod, patchbay-devices or patchbay-themes"),
                everything, discoveredFailed && listedFailed, picked, active, true);
            fill(recommended, PatchbayLauncher.t("Polecane", "Recommended"),
                PatchbayLauncher.t("z listy ", "from the list ") + LIST_REPO + PatchbayLauncher.t(", jeszcze nie w aktywnych", ", not active yet"),
                fresh, listedFailed, picked, active, false);
            fillCard(foundCard, find(everything, pickedFound), picked, active);
            fillCard(recommendedCard, find(fresh, pickedRecommended), picked, active);
        }

        private static Suggestion find(List<Suggestion> items, String key) {
            if (items == null || key == null) return null;
            return items.stream().filter(x -> key(x).equals(key)).findFirst().orElse(null);
        }

        private void fill(JPanel column, String title, String hint, List<Suggestion> items, boolean failed, Set<String> picked, Set<String> active, boolean inFound) {
            column.removeAll();
            JPanel box = new JPanel(new BorderLayout());
            box.setOpaque(false);
            box.setBorder(BorderFactory.createLineBorder(Theme.HAIR));
            box.setAlignmentX(LEFT_ALIGNMENT);
            JPanel head = new JPanel(new BorderLayout(12, 0));
            head.setBackground(Theme.FRAME);
            head.setBorder(new EmptyBorder(8, 14, 8, 10));
            head.add(Theme.row(10, Theme.label(title, Theme.bold(15f), Theme.TEXT), Theme.label(hint, Theme.text(12f), Theme.MUTED)), BorderLayout.WEST);
            box.add(head, BorderLayout.NORTH);
            JPanel list = new JPanel();
            list.setOpaque(false);
            list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
            if (items == null || items.isEmpty()) {
                String text = items == null ? PatchbayLauncher.t("Sprawdzam źródła na GitHubie…", "Checking the sources on GitHub…")
                    : failed ? PatchbayLauncher.t("Nie udało się pobrać listy źródeł z GitHuba.", "Couldn't get the list of sources from GitHub.")
                    : PatchbayLauncher.t("Brak nowych źródeł. Masz już wszystkie.", "No new sources. You have them all.");
                JLabel none = Theme.label(text, Theme.text(12f), Theme.MUTED);
                none.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.HAIR), new EmptyBorder(10, 14, 10, 12)));
                none.setAlignmentX(LEFT_ALIGNMENT);
                none.setMaximumSize(new Dimension(Integer.MAX_VALUE, none.getPreferredSize().height));
                list.add(none);
            } else {
                for (Suggestion x : items) list.add(onlineRow(x, inFound && picked.contains(key(x)), inFound && active.contains(key(x)), inFound));
            }
            box.add(list, BorderLayout.CENTER);
            box.setMaximumSize(new Dimension(Integer.MAX_VALUE, box.getPreferredSize().height));
            column.add(box);
            column.revalidate();
            column.repaint();
        }

        /** The repository's name (null: none) with its group and, when they hold, "polecane" and "w aktywnych". */
        private static JPanel tags(String repo, String group, boolean picked, boolean active) {
            List<Component> parts = new ArrayList<>();
            if (repo != null) parts.add(Theme.label(repo, Theme.mono(13f), Theme.TEXT));
            parts.add(badge(groupName(group)));
            if (picked) parts.add(badge(PatchbayLauncher.t("polecane", "recommended")));
            if (active) parts.add(badge(PatchbayLauncher.t("w aktywnych", "active")));
            return Theme.row(8, parts.toArray(new Component[0]));
        }

        /** One repository on GitHub: its name, group and description; a click shows it in the card beside the list. */
        private JComponent onlineRow(Suggestion x, boolean markPicked, boolean markActive, boolean inFound) {
            boolean selected = key(x).equals(inFound ? pickedFound : pickedRecommended);
            JPanel row = new JPanel(new BorderLayout(12, 0));
            row.setBackground(selected ? Theme.ACCENT_SEL : Theme.WELL);
            row.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.HAIR),
                selected ? BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, Theme.ACCENT), new EmptyBorder(8, 11, 8, 12))
                    : new EmptyBorder(8, 14, 8, 12)));
            row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            JPanel top = tags(x.repo(), x.group(), markPicked, markActive);
            top.setAlignmentX(LEFT_ALIGNMENT);
            text.add(top);
            if (x.note() != null && !x.note().isBlank()) {
                text.add(Box.createVerticalStrut(3));
                JLabel note = Theme.label(x.note(), Theme.text(12f), Theme.MUTED);
                note.setAlignmentX(LEFT_ALIGNMENT);
                text.add(note);
            }
            row.add(text, BorderLayout.CENTER);
            row.getAccessibleContext().setAccessibleName(x.repo());
            java.awt.event.MouseAdapter pick = new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) { pick(x, inFound); }
            };
            row.addMouseListener(pick);
            for (Component c : text.getComponents()) c.addMouseListener(pick);
            for (Component c : top.getComponents()) c.addMouseListener(pick);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
            return row;
        }

        /** Marks the repository, shows it in the card and asks GitHub about it (once). */
        private void pick(Suggestion x, boolean inFound) {
            if (inFound) pickedFound = key(x); else pickedRecommended = key(x);
            buildOnline();
            if (infos.containsKey(key(x)) || counts.containsKey(key(x))) return;
            new Thread(() -> {
                RepoInfo info;
                try {
                    info = repoInfo(x.repo());
                } catch (IOException | RuntimeException ex) {
                    info = null;
                }
                Holds n;
                try {
                    n = found(x);
                } catch (IOException | RuntimeException ex) {
                    n = new Holds(-1, false);
                }
                RepoInfo i = info;
                Holds count = n;
                SwingUtilities.invokeLater(() -> {
                    if (i != null) infos.put(key(x), i);
                    counts.put(key(x), count);
                    buildOnline();
                });
            }, "repo-info").start();
        }

        /** The card beside Lista źródeł and Polecane: who made the picked repository, what it is and holds, and Add to active. */
        private void fillCard(JPanel card, Suggestion x, Set<String> picked, Set<String> active) {
            card.removeAll();
            JPanel col = new JPanel();
            col.setOpaque(false);
            col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
            if (x == null) {
                add(col, Theme.label(PatchbayLauncher.t("Repozytorium", "Repository"), Theme.bold(18f), Theme.TEXT), 10);
                add(col, Theme.wrapped(PatchbayLauncher.t("Kliknij repozytorium na liście, żeby zobaczyć, kto je zrobił i co w nim jest, i dodać je do aktywnych.",
                    "Click a repository in the list to see who made it and what's in it, and to add it to the active sources."), 230, Theme.MUTED, 13f), 0);
                card.add(col, BorderLayout.NORTH);
                card.revalidate();
                card.repaint();
                return;
            }
            String k = key(x);
            RepoInfo info = infos.get(k);
            Holds held = counts.get(k);
            Integer count = held == null ? null : held.count();
            boolean on = active.contains(k);
            add(col, tags(null, x.group(), picked.contains(k), on), 8);
            add(col, Theme.wrapped(x.repo().replace("/", "/\u200B"), 230, Theme.TEXT, 18f), 10);
            String about = info != null && info.description() != null && !info.description().isBlank() ? info.description() : x.note();
            if (about != null && !about.isBlank()) add(col, Theme.wrapped(about, 230, Theme.SOFT, 14f), 12);
            String holds = count == null ? PatchbayLauncher.t("sprawdzam…", "checking…")
                : count < 0 ? PatchbayLauncher.t("nie udało się sprawdzić", "couldn't check")
                : count == 0 ? PatchbayLauncher.t("nic, co Launcher umie pobrać", "nothing the launcher can get")
                : held.list() ? PatchbayLauncher.t("lista: ", "a list: ") + count + PatchbayLauncher.t(" repozytoriów z motywami", " theme repositories")
                : groupName(x.group()) + ": " + count;
            List<String[]> facts = new ArrayList<>();
            facts.add(new String[] { PatchbayLauncher.t("Autor", "Author"), info != null ? info.owner() : x.repo().substring(0, x.repo().indexOf('/')) });
            facts.add(new String[] { PatchbayLauncher.t("Zawiera", "Holds"), holds });
            if (info != null && info.changed() != null) facts.add(new String[] { PatchbayLauncher.t("Ostatnia zmiana", "Last change"), info.changed(), "mono" });
            if (info != null && info.license() != null && !"NOASSERTION".equals(info.license())) facts.add(new String[] { PatchbayLauncher.t("Licencja", "Licence"), info.license() });
            if (info != null) facts.add(new String[] { PatchbayLauncher.t("Gwiazdki", "Stars"), String.valueOf(info.stars()), "mono" });
            JPanel table = Theme.facts(facts.toArray(new String[0][]));
            add(col, table, 0);
            card.add(Theme.scrolling(col), BorderLayout.CENTER);

            JButton addIt = Theme.button(on ? PatchbayLauncher.t("Już w aktywnych", "Already active") : PatchbayLauncher.t("Dodaj do aktywnych", "Add to active"), "primary");
            // one on the author's list can be added even when GitHub couldn't be asked; one found by topic only when it holds something
            addIt.setEnabled(!on && count != null && (count > 0 || count < 0 && picked.contains(k)));
            addIt.addActionListener(e -> addOnline(x, card));
            JButton page = Theme.button(PatchbayLauncher.t("Strona repozytorium", "Repository page"), "secondary");
            page.addActionListener(e -> Platform.open("https://github.com/" + x.repo()));
            JPanel south = new JPanel(new GridLayout(0, 1, 0, 8));
            south.setOpaque(false);
            south.setBorder(new EmptyBorder(12, 0, 0, 0));
            south.add(addIt);
            south.add(page);
            card.add(south, BorderLayout.SOUTH);
            card.revalidate();
            card.repaint();
        }

        private void addOnline(Suggestion x, JComponent row) {
            try {
                Sources.add(x);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(row, String.valueOf(ex.getMessage()), "Patchbay Launcher", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (key(x).equals(pickedFound)) pickedFound = null;
            if (key(x).equals(pickedRecommended)) pickedRecommended = null;
            sources = all();
            rebuild();
            buildOnline();
            countUnseen();
            changed.run();
        }

        private void rebuild() {
            if (listed != null) { buildOnline(); countUnseen(); } // an added or removed source changes what is marked active
            restoreButton.setVisible(!removedBuiltins().isEmpty() || sources.stream().anyMatch(Sources::changed));
            groups.removeAll();
            for (String g : GROUPS) {
                groups.add(section(g));
                groups.add(Box.createVerticalStrut(14));
            }
            groups.revalidate();
            groups.repaint();
            buildForm();
        }

        private JComponent section(String group) {
            JPanel box = new JPanel(new BorderLayout());
            box.setOpaque(false);
            box.setBorder(BorderFactory.createLineBorder(Theme.HAIR));
            box.setAlignmentX(LEFT_ALIGNMENT);
            JPanel head = new JPanel(new BorderLayout(12, 0));
            head.setBackground(Theme.FRAME);
            head.setBorder(new EmptyBorder(6, 14, 6, 10));
            String hint = switch (group) {
                case PATCHBAY -> PatchbayLauncher.t("jedno aktywne źródło", "one active source");
                case MODS -> PatchbayLauncher.t("ZIP-y modów z wydań", "mod ZIPs from releases");
                case THEMES -> PatchbayLauncher.t("motywy .bte", ".bte themes");
                default -> PatchbayLauncher.t("pliki .bwdevice i .bwmodulator", ".bwdevice and .bwmodulator files");
            };
            head.add(Theme.row(10, Theme.label(groupName(group), Theme.bold(15f), Theme.TEXT), Theme.label(hint, Theme.text(12f), Theme.MUTED)), BorderLayout.WEST);
            JButton add = Theme.button(PatchbayLauncher.t("+ Dodaj", "+ Add"), "secondary");
            add.putClientProperty("small", true);
            add.getAccessibleContext().setAccessibleName(PatchbayLauncher.t("Dodaj źródło: ", "Add source: ") + groupName(group));
            add.addActionListener(e -> startNew(group));
            head.add(add, BorderLayout.EAST);
            box.add(head, BorderLayout.NORTH);
            JPanel list = new JPanel();
            list.setOpaque(false);
            list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
            for (Source s : sources) if (s.group().equals(group)) list.add(sourceRow(s));
            if (list.getComponentCount() == 0) {
                JLabel none = Theme.label(PatchbayLauncher.t("Brak źródeł. Kliknij + Dodaj albo Przywróć wbudowane.", "No sources. Click + Add or Restore built-in."), Theme.text(12f), Theme.MUTED);
                none.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.HAIR), new EmptyBorder(10, 14, 10, 12)));
                none.setAlignmentX(LEFT_ALIGNMENT);
                none.setMaximumSize(new Dimension(Integer.MAX_VALUE, none.getPreferredSize().height));
                list.add(none);
            }
            box.add(list, BorderLayout.CENTER);
            box.setMaximumSize(new Dimension(Integer.MAX_VALUE, box.getPreferredSize().height));
            return box;
        }

        private JComponent sourceRow(Source s) {
            boolean selected = !idle && draft != null && s.id().equals(draft.id());
            JPanel row = new JPanel(new BorderLayout(12, 0));
            row.setBackground(selected ? Theme.ACCENT_SEL : Theme.WELL);
            // the row being edited is marked like a selected list row: a darker fill and an orange edge on the left
            row.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.HAIR),
                selected ? BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, Theme.ACCENT), new EmptyBorder(8, 11, 8, 12))
                    : new EmptyBorder(8, 14, 8, 12)));
            Theme.Switch sw = new Theme.Switch(s.enabled(),
                (s.enabled() ? PatchbayLauncher.t("Wyłącz ", "Turn off ") : PatchbayLauncher.t("Włącz ", "Turn on ")) + s.repo(),
                on -> toggle(s, on));
            JPanel left = new JPanel(new GridBagLayout());
            left.setOpaque(false);
            left.add(sw);
            row.add(left, BorderLayout.WEST);
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            JLabel repo = Theme.label(s.repo(), Theme.mono(13f), s.enabled() ? Theme.TEXT : Theme.MUTED);
            List<Component> parts = new ArrayList<>(List.of(repo));
            if (s.builtin()) parts.add(badge(changed(s) ? PatchbayLauncher.t("wbudowane · zmienione", "built-in · changed") : PatchbayLauncher.t("wbudowane", "built-in")));
            if (missing(s)) parts.add(fault(PatchbayLauncher.t("nie istnieje", "doesn't exist")));
            JPanel top = Theme.row(8, parts.toArray(new Component[0]));
            top.setAlignmentX(LEFT_ALIGNMENT);
            text.add(top);
            text.add(Box.createVerticalStrut(3));
            JLabel meta = Theme.label(describe(s), Theme.text(12f), Theme.MUTED);
            meta.setAlignmentX(LEFT_ALIGNMENT);
            text.add(meta);
            row.add(text, BorderLayout.CENTER);
            JButton edit = Theme.button(PatchbayLauncher.t("Edytuj", "Edit"), "secondary");
            edit.putClientProperty("small", true);
            edit.setFont(Theme.text(12f));
            edit.getAccessibleContext().setAccessibleName(PatchbayLauncher.t("Edytuj ", "Edit ") + s.repo());
            edit.addActionListener(e -> { idle = false; draft = s; draftGroup = s.group(); message.setText(""); rebuild(); });
            JPanel right = new JPanel(new GridBagLayout());
            right.setOpaque(false);
            right.add(edit);
            row.add(right, BorderLayout.EAST);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
            return row;
        }

        /** A short result line ("Saved.", what Check found) in a framed box. */
        private static JComponent note(String text) {
            JLabel m = Theme.wrapped(text, 204, Theme.TEXT, 12f);
            m.setOpaque(true);
            m.setBackground(Theme.WELL);
            m.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.LINE), new EmptyBorder(8, 10, 8, 10)));
            m.getAccessibleContext().setAccessibleDescription(text);
            return m;
        }

        /** A subtitle that is a sentence keeps the text face (numbers and versions get the mono one). */
        private static JLabel prose(JLabel l) { l.putClientProperty("prose", true); return l; }

        /** A badge in the fault colour: a source whose repository is gone. */
        private static JComponent fault(String text) {
            JLabel l = Theme.label(text, Theme.text(11f), Theme.FAULT_TEXT);
            l.setOpaque(true);
            l.setBackground(Theme.CONTROL);
            l.setBorder(new EmptyBorder(1, 7, 1, 7));
            return l;
        }

        private static JComponent badge(String text) {
            JLabel l = Theme.label(text, Theme.text(11f), Theme.MUTED);
            l.setOpaque(true);
            l.setBackground(Theme.CONTROL);
            l.setBorder(new EmptyBorder(1, 7, 1, 7));
            return l;
        }

        private void toggle(Source s, boolean on) {
            List<Source> next = new ArrayList<>();
            for (Source x : sources) {
                if (x.id().equals(s.id())) next.add(x.withEnabled(on));
                else if (on && s.group().equals(PATCHBAY) && x.group().equals(PATCHBAY)) next.add(x.withEnabled(false));
                else next.add(x);
            }
            if (s.group().equals(PATCHBAY) && next.stream().noneMatch(x -> x.group().equals(PATCHBAY) && x.enabled())) {
                message.setText(PatchbayLauncher.t("Patchbay potrzebuje jednego włączonego źródła.", "Patchbay needs one source switched on."));
                rebuild();
                return;
            }
            commit(next);
        }

        private void startNew(String group) {
            idle = false;
            draft = null;
            draftGroup = group;
            repoField.setText("");
            filterField.setText(group.equals(PATCHBAY) ? Platform.releasePattern() : group.equals(MODS) ? "*.zip" : "");
            message.setText("");
            rebuild();
            repoField.requestFocusInWindow();
        }

        private void buildForm() {
            if (idle) {
                form.removeAll();
                JPanel col = new JPanel();
                col.setOpaque(false);
                col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
                add(col, Theme.label(PatchbayLauncher.t("Źródło", "Source"), Theme.bold(18f), Theme.TEXT), 10);
                add(col, Theme.wrapped(PatchbayLauncher.t(
                    "Kliknij Edytuj przy źródle, żeby zmienić jego ustawienia, albo + Dodaj, żeby dodać nowe. Przełącznikiem włączasz i wyłączasz źródło.",
                    "Click Edit beside a source to change its settings, or + Add to add a new one. The switch turns a source on and off."), 230, Theme.MUTED, 13f), 12);
                if (message.getText() != null && !message.getText().isEmpty()) add(col, note(message.getText()), 10);
                form.add(col, BorderLayout.NORTH);
                form.revalidate();
                form.repaint();
                return;
            }
            form.removeAll();
            boolean isNew = draft == null;
            boolean builtin = !isNew && draft.builtin();
            if (!isNew) {
                repoField.setText(draft.repo());
                filterField.setText(draft.filter());
            }
            JPanel col = new JPanel();
            col.setOpaque(false);
            col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
            add(col, Theme.label(isNew ? PatchbayLauncher.t("Nowe źródło: ", "New source: ") + groupName(draftGroup) : PatchbayLauncher.t("Edytuj źródło", "Edit source"), Theme.bold(18f), Theme.TEXT), 14);
            if (!isNew && missing(draft)) {
                add(col, note(PatchbayLauncher.t("Repozytorium zostało usunięte, jest prywatne albo zmieniło nazwę. Mody i urządzenia z niego działają dalej, ale nie dostaną aktualizacji. Sprawdź konto autora albo usuń źródło z listy.",
                    "The repository was deleted, made private or renamed. Mods and devices from it still work, but won't get updates. Check the author's account, or remove the source from the list.")), 8);
                String owner = draft.repo().substring(0, draft.repo().indexOf('/'));
                JButton author = Theme.button(PatchbayLauncher.t("Strona autora", "Author's page"), "secondary");
                author.addActionListener(e -> Platform.open("https://github.com/" + owner));
                author.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
                add(col, author, 14);
            }

            add(col, Theme.label(PatchbayLauncher.t("Grupa", "Group"), Theme.text(12f), Theme.MUTED), 6);
            JPanel picks = new JPanel(new GridLayout(2, 2, 2, 2));
            picks.setBackground(Theme.WELL);
            picks.setBorder(new EmptyBorder(3, 3, 3, 3));
            for (String g : GROUPS) {
                JButton b = Theme.button(groupName(g), g.equals(draftGroup) ? "nav" : "nav");
                b.putClientProperty("current", g.equals(draftGroup));
                b.putClientProperty("small", true);
                b.setHorizontalAlignment(SwingConstants.CENTER);
                b.setFont(Theme.text(12f));
                b.addActionListener(e -> { draftGroup = g; if (draft == null) filterField.setText(g.equals(PATCHBAY) ? Platform.releasePattern() : g.equals(MODS) ? "*.zip" : ""); buildForm(); });
                picks.add(b);
            }
            add(col, picks, 14);

            add(col, Theme.label(PatchbayLauncher.t("Repozytorium GitHub (właściciel/nazwa)", "GitHub repository (owner/name)"), Theme.text(12f), Theme.MUTED), 6);
            repoField.putClientProperty("JTextField.placeholderText", "polarity/bitwig-mods");
            repoField.getAccessibleContext().setAccessibleName(PatchbayLauncher.t("Repozytorium GitHub", "GitHub repository"));
            add(col, repoField, 14);

            add(col, Theme.label(PatchbayLauncher.t("Skąd brać pliki", "Where files come from"), Theme.text(12f), Theme.MUTED), 6);
            add(col, Theme.label(inRepository(draftGroup)
                ? PatchbayLauncher.t("Pliki w repozytorium", "Files in the repository")
                : PatchbayLauncher.t("Wydania (pliki ZIP)", "Releases (ZIP files)"), Theme.text(14f), Theme.TEXT), 14);

            String filterLabel = switch (draftGroup) {
                case PATCHBAY -> PatchbayLauncher.t("Plik z wydania (* = wersja)", "Release file (* = version)");
                case MODS -> PatchbayLauncher.t("Które pliki z wydania", "Which release files");
                default -> PatchbayLauncher.t("Folder w repozytorium (puste = wszystkie)", "Folder in the repository (empty = all)");
            };
            add(col, Theme.label(filterLabel, Theme.text(12f), Theme.MUTED), 6);
            filterField.getAccessibleContext().setAccessibleName(filterLabel);
            add(col, filterField, 12);

            if (builtin) add(col, Theme.wrapped(changed(draft)
                ? PatchbayLauncher.t("Źródło wbudowane, zmienione przez Ciebie. „Przywróć domyślne” wraca do ustawień z Launchera.",
                    "A built-in source you changed. \"Restore default\" goes back to the launcher's settings.")
                : PatchbayLauncher.t("Źródło wbudowane w Launcher. Możesz je zmienić albo usunąć; „Przywróć wbudowane” na górze przywraca je w każdej chwili.",
                    "A source built into the launcher. You can change or remove it; \"Restore built-in\" at the top brings it back at any time."), 214, Theme.MUTED, 12f), 10);
            if (draftGroup.equals(PATCHBAY) && !builtin) add(col, Theme.wrapped(PatchbayLauncher.t("Patchbay pobierany jest z jednego źródła naraz. Włączenie tego wyłączy pozostałe.",
                "Patchbay comes from one source at a time. Switching this one on switches the others off."), 214, Theme.MUTED, 12f), 10);
            if (message.getText() != null && !message.getText().isEmpty()) {
                add(col, note(message.getText()), 10);
            }
            form.add(col, BorderLayout.NORTH);

            JPanel buttons = new JPanel();
            buttons.setOpaque(false);
            buttons.setLayout(new BoxLayout(buttons, BoxLayout.Y_AXIS));
            JPanel pair = new JPanel(new GridLayout(1, 2, 8, 0));
            pair.setOpaque(false);
            JButton check = Theme.button(PatchbayLauncher.t("Sprawdź", "Check"), "secondary");
            check.addActionListener(e -> runCheck());
            JButton save = Theme.button(isNew ? PatchbayLauncher.t("Dodaj", "Add") : PatchbayLauncher.t("Zapisz", "Save"), "primary");
            save.addActionListener(e -> saveDraft());
            pair.add(check);
            pair.add(save);
            pair.setAlignmentX(LEFT_ALIGNMENT);
            buttons.add(pair);
            if (builtin && changed(draft)) {
                buttons.add(Box.createVerticalStrut(8));
                JButton reset = Theme.button(PatchbayLauncher.t("Przywróć domyślne", "Restore default"), "secondary");
                reset.setAlignmentX(LEFT_ALIGNMENT);
                reset.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
                reset.addActionListener(e -> resetDraft());
                buttons.add(reset);
            }
            if (!isNew) {
                buttons.add(Box.createVerticalStrut(8));
                JButton remove = Theme.button(PatchbayLauncher.t("Usuń źródło", "Remove source"), "danger");
                remove.setAlignmentX(LEFT_ALIGNMENT);
                remove.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
                remove.addActionListener(e -> removeDraft());
                buttons.add(remove);
            }
            form.add(buttons, BorderLayout.SOUTH);
            form.revalidate();
            form.repaint();
        }

        private static void add(JPanel col, JComponent c, int gapAfter) {
            c.setAlignmentX(LEFT_ALIGNMENT);
            if (c instanceof JTextField) c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            if (c instanceof JPanel) c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
            col.add(c);
            col.add(Box.createVerticalStrut(gapAfter));
        }

        /** The form as a source, or null with a message when the repository isn't valid. */
        private Source fromForm() {
            String repo = normalizeRepo(repoField.getText());
            if (repo == null) {
                message.setText(PatchbayLauncher.t("Wpisz repozytorium w postaci właściciel/nazwa, np. polarity/bitwig-mods.",
                    "Type the repository as owner/name, for example polarity/bitwig-mods."));
                buildForm();
                return null;
            }
            String filter = filterField.getText().strip();
            if (filter.isEmpty() && !inRepository(draftGroup)) filter = draftGroup.equals(PATCHBAY) ? Platform.releasePattern() : "*.zip";
            String id = draft != null ? draft.id() : "own-" + System.currentTimeMillis() + "-" + (nextId++);
            return new Source(id, draftGroup, repo, filter, draft != null && draft.builtin(), draft == null || draft.enabled());
        }

        private void runCheck() {
            Source s = fromForm();
            if (s == null) return;
            message.setText(PatchbayLauncher.t("Sprawdzanie…", "Checking…"));
            buildForm();
            new Thread(() -> {
                String result = check(s);
                SwingUtilities.invokeLater(() -> { message.setText(result); buildForm(); });
            }, "source-check").start();
        }

        private void saveDraft() {
            Source s = fromForm();
            if (s == null) return;
            boolean isNew = draft == null;
            if (isNew) {
                int ok = JOptionPane.showConfirmDialog(this,
                    PatchbayLauncher.t("Mody i urządzenia to kod uruchamiany wewnątrz Bitwiga, a motywy zmieniają jego wygląd. Dodawaj tylko repozytoria, którym ufasz.\n\nDodać ",
                        "Mods and devices are code that runs inside Bitwig, and themes change how it looks. Only add repositories you trust.\n\nAdd ") + s.repo() + "?",
                    "Patchbay Launcher", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
                if (ok != JOptionPane.OK_OPTION) return;
            }
            List<Source> next = new ArrayList<>();
            boolean replaced = false;
            for (Source x : sources) {
                if (x.id().equals(s.id())) { next.add(s); replaced = true; }
                else next.add(x);
            }
            if (!replaced) next.add(s);
            if (s.group().equals(PATCHBAY) && s.enabled())
                next.replaceAll(x -> x.group().equals(PATCHBAY) && !x.id().equals(s.id()) ? x.withEnabled(false) : x);
            draft = s;
            message.setText(isNew ? PatchbayLauncher.t("Dodano.", "Added.") : PatchbayLauncher.t("Zapisano.", "Saved."));
            commit(next);
        }

        private void removeDraft() {
            if (draft == null) return;
            Source gone = draft;
            List<Source> next = new ArrayList<>(sources);
            next.removeIf(x -> x.id().equals(gone.id()));
            if (gone.group().equals(PATCHBAY) && next.stream().noneMatch(x -> x.group().equals(PATCHBAY))) {
                message.setText(PatchbayLauncher.t("Patchbay potrzebuje co najmniej jednego źródła. Dodaj inne, zanim usuniesz to.",
                    "Patchbay needs at least one source. Add another one before you remove this one."));
                buildForm();
                return;
            }
            int ok = JOptionPane.showConfirmDialog(this,
                PatchbayLauncher.t("Usunąć źródło ", "Remove the source ") + gone.repo() + "?"
                    + (gone.builtin() ? PatchbayLauncher.t("\n\nTo źródło wbudowane: „Przywróć wbudowane” przywróci je później.", "\n\nIt is built in: \"Restore built-in\" brings it back later.") : ""),
                "Patchbay Launcher", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) return;
            if (gone.group().equals(PATCHBAY) && gone.enabled() && next.stream().noneMatch(x -> x.group().equals(PATCHBAY) && x.enabled())) {
                String first = next.stream().filter(x -> x.group().equals(PATCHBAY)).findFirst().orElseThrow().id();
                next.replaceAll(x -> x.id().equals(first) ? x.withEnabled(true) : x);
            }
            idle = true;
            draft = null;
            message.setText(PatchbayLauncher.t("Usunięto ", "Removed ") + gone.repo() + ".");
            commit(next);
        }

        /** A changed built-in source back to the launcher's settings (on/off kept). */
        private void resetDraft() {
            Source o = draft == null ? null : original(draft.id());
            if (o == null) return;
            Source back = o.withEnabled(draft.enabled());
            List<Source> next = new ArrayList<>(sources);
            next.replaceAll(x -> x.id().equals(back.id()) ? back : x);
            draft = back;
            draftGroup = back.group();
            message.setText(PatchbayLauncher.t("Przywrócono ustawienia z Launchera.", "Back to the launcher's settings."));
            commit(next);
        }

        /** Every built-in source as shipped, removed ones back; the user's own stay. Patchbay keeps a single source on. */
        private void restoreBuiltins() {
            int ok = JOptionPane.showConfirmDialog(this, PatchbayLauncher.t(
                    "Przywrócić wbudowane źródła tak, jak są w Launcherze? Twoje zmiany w nich znikną; źródła dodane przez Ciebie zostaną.",
                    "Bring back the built-in sources as the launcher ships them? Your changes to them are dropped; sources you added stay."),
                "Patchbay Launcher", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) return;
            List<Source> own = sources.stream().filter(x -> !x.builtin()).toList();
            boolean ownPatchbayOn = own.stream().anyMatch(x -> x.group().equals(PATCHBAY) && x.enabled());
            List<Source> next = new ArrayList<>();
            for (Source b : BUILTIN) {
                Source now = sources.stream().filter(x -> x.id().equals(b.id())).findFirst().orElse(null);
                boolean on = b.group().equals(PATCHBAY) ? !ownPatchbayOn && (now == null || now.enabled()) : now == null || now.enabled();
                next.add(b.withEnabled(on));
            }
            next.addAll(own);
            if (next.stream().noneMatch(x -> x.group().equals(PATCHBAY) && x.enabled()))
                next.replaceAll(x -> x.id().equals(BUILTIN.get(0).id()) ? x.withEnabled(true) : x);
            idle = true;
            draft = null;
            message.setText(PatchbayLauncher.t("Przywrócono wbudowane źródła.", "Built-in sources restored."));
            commit(next);
        }

        private void commit(List<Source> next) {
            try {
                save(next);
                sources = all();
                if (draft != null) {
                    Source keep = draft;
                    draft = sources.stream().filter(x -> x.id().equals(keep.id())).findFirst().orElse(keep);
                }
                rebuild();
                changed.run();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, String.valueOf(ex.getMessage()), "Patchbay Launcher", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
