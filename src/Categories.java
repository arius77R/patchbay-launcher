import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.Icon;

/**
 * The fixed list of mod categories from docs/PUBLISHING.md (rule 8): the part of a mod's name before the
 * colon. A name that already uses one of them keeps it; the names mods had before the list are put in the
 * category that took their place; anything else, or a name without a category, is Other. Each category has
 * its own colour, always the same, and an icon from res/icons.
 */
final class Categories {
    private Categories() { }

    record Category(String name, String icon) { }

    static final List<Category> ALL = List.of(
        new Category("Arranger", "arranger"), new Category("Clip Launcher", "clip-launcher"), new Category("Piano Roll", "piano-roll"),
        new Category("Audio Editor", "audio-editor"), new Category("Automation", "automation"), new Category("Mixer", "mixer"),
        new Category("Devices", "devices"), new Category("Browser", "browser"), new Category("Inspector", "inspector"),
        new Category("Transport", "transport"), new Category("Interface", "interface"), new Category("Commands", "commands"),
        new Category("Project", "project"), new Category("Framework", "framework"), new Category("Other", "other"));
    static final String OTHER = "Other";

    /** The names mods used before the fixed list, and the category that took their place. */
    private static final Map<String, String> EARLIER = Map.ofEntries(
        Map.entry("track", "Arranger"), Map.entry("clips", "Clip Launcher"), Map.entry("audio clip", "Audio Editor"),
        Map.entry("detail editor", "Audio Editor"), Map.entry("device", "Devices"), Map.entry("parameter", "Devices"),
        Map.entry("browser (side)", "Browser"), Map.entry("panel", "Interface"), Map.entry("notifications", "Interface"),
        Map.entry("commander", "Commands"), Map.entry("shortcuts", "Commands"), Map.entry("export", "Project"),
        Map.entry("helper", "Framework"));

    /** The category of a mod's name ("Browser (side): Hide Parts" → Browser); Other when it has none from the list. */
    static String of(String modName) {
        String written = Mods.category(modName).toLowerCase(Locale.ROOT);
        for (Category c : ALL) if (c.name().toLowerCase(Locale.ROOT).equals(written)) return c.name();
        return EARLIER.getOrDefault(written, OTHER);
    }

    static Category find(String name) {
        for (Category c : ALL) if (c.name().equals(name)) return c;
        return null;
    }

    /** The category's own colour: neighbours on the list never share one. */
    static Color colour(String name) {
        Category c = find(name);
        return c == null ? Theme.MUTED : Theme.categoryColour(ALL.indexOf(c));
    }

    private static final java.util.Set<String> DEVICE_TYPES = java.util.Set.of("audio-fx", "instrument", "note-fx", "note-generator", "modulator", "grid-module");

    /** The icon of a device type ("Audio FX" → audio-fx); a type without its own icon gets the devices one. */
    static String deviceIcon(String type) {
        String name = type == null ? "" : type.strip().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        return DEVICE_TYPES.contains(name) ? name : "devices";
    }

    private static final Map<String, BufferedImage> PICTURES = new HashMap<>();

    /** An icon from res/icons (white, 40 px) drawn in a colour at size×size; a small dot when there's no such icon. */
    static Icon icon(String name, Color colour, int size) {
        BufferedImage white = PICTURES.computeIfAbsent(name == null ? "" : name, Categories::load);
        return new Icon() {
            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                if (white == null) {
                    g2.setColor(colour);
                    int d = Math.max(6, size / 3);
                    g2.fillOval(x + (size - d) / 2, y + (size - d) / 2, d, d);
                } else {
                    BufferedImage tinted = new BufferedImage(white.getWidth(), white.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    Graphics2D t = tinted.createGraphics();
                    t.drawImage(white, 0, 0, null);
                    t.setComposite(AlphaComposite.SrcIn);
                    t.setColor(colour);
                    t.fillRect(0, 0, white.getWidth(), white.getHeight());
                    t.dispose();
                    g2.drawImage(tinted, x, y, size, size, null);
                }
                g2.dispose();
            }
        };
    }

    private static BufferedImage load(String name) {
        if (name.isEmpty()) return null;
        try (InputStream in = Categories.class.getResourceAsStream("/icons/" + name + ".png")) {
            return in == null ? null : ImageIO.read(in);
        } catch (Exception unreadable) {
            return null;
        }
    }
}
