import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * The side panel beside a list (Mods, Devices): All, Favourites, then each category with its icon and how many
 * it holds. A click shows only that part of the list. « folds the panel down to its icons (each says its name
 * and count on hovering), » opens it again; the choice and the fold are remembered. When the list beside has
 * too little room, the panel folds to its icons by itself until there is room again.
 */
final class SidePanel extends JPanel {
    record Entry(String key, String label, Icon icon, int count) { }

    static final String ALL = "all", FAVOURITES = "favourites";
    private static final int WIDE = 186, NARROW = 48, ROW = 32;
    /** The width the list needs beside the open panel to show its names in full. */
    private static final int ROOM = 620;

    private final String setting;
    private final Consumer<String> picked;
    private final JPanel items = new JPanel();
    private final JLabel heading = Theme.label(PatchbayLauncher.t("KATEGORIE", "CATEGORIES"), Theme.bold(11f), Theme.MUTED);
    private final JButton fold = Theme.button("«", "secondary");
    /** narrow: folded by the user (remembered); tight: folded because the list has too little room; opened: unfolded by hand while tight. */
    private boolean narrow, tight, opened;
    private String selected;

    /** setting: the prefix the fold and the choice are remembered under; picked runs with the chosen key. */
    SidePanel(String setting, Consumer<String> picked) {
        super(new BorderLayout());
        this.setting = setting;
        this.picked = picked;
        narrow = "true".equals(I18n.setting(setting + "Narrow"));
        String kept = I18n.setting(setting + "Pick");
        selected = kept == null ? ALL : kept;
        setOpaque(false);
        items.setOpaque(false);
        items.setLayout(new BoxLayout(items, BoxLayout.Y_AXIS));
        fold.setMargin(new Insets(2, 8, 2, 8));
        fold.addActionListener(e -> {
            if (folded() && !narrow) opened = true;
            else if (folded()) narrow = false;
            else { narrow = true; opened = false; }
            I18n.remember(setting + "Narrow", Boolean.toString(narrow));
            relayout();
        });
        JPanel card = new Theme.Card(new BorderLayout(), Theme.FRAME);
        card.setBorder(new EmptyBorder(8, 6, 8, 6));
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.setBorder(new EmptyBorder(0, 6, 6, 0));
        head.add(heading, BorderLayout.CENTER);
        head.add(fold, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(items, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        card.add(scroll, BorderLayout.CENTER);
        add(card, BorderLayout.CENTER);
        layoutHead();
    }

    private boolean folded() { return narrow || tight && !opened; }

    private void layoutHead() {
        heading.setVisible(!folded());
        fold.setText(folded() ? "»" : "«");
        fold.setToolTipText(folded() ? PatchbayLauncher.t("Rozwiń panel", "Open the panel") : PatchbayLauncher.t("Zwiń panel do ikon", "Fold the panel to icons"));
    }

    private void relayout() {
        layoutHead();
        revalidate();
        repaint();
    }

    /** The list beside has too little room for its names: the panel folds to its icons until there is room again. */
    void setTight(boolean t) {
        if (t == tight) return;
        tight = t;
        if (!t) opened = false;
        relayout();
    }

    static String categoryKey(String category) { return "category:" + category; }

    /** All and Favourites with their counts. */
    static List<Entry> top(int all, int favourites) {
        return List.of(
            new Entry(ALL, PatchbayLauncher.t("Wszystkie", "All"), Categories.icon("all", Theme.TEXT, 20), all),
            new Entry(FAVOURITES, PatchbayLauncher.t("Ulubione", "Favourites"), Categories.icon("favorites", Theme.ACCENT, 20), favourites));
    }

    /** The panel at the list's left, the list beside it. */
    static JComponent beside(SidePanel side, JComponent list) {
        JPanel both = new JPanel(new BorderLayout(12, 0));
        both.setOpaque(false);
        both.add(side, BorderLayout.WEST);
        both.add(list, BorderLayout.CENTER);
        both.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) { side.setTight(both.getWidth() - WIDE - 12 < ROOM); }
        });
        return both;
    }

    /** The starred keys kept under this setting (one comma-separated line). */
    static java.util.Set<String> favourites(String setting) {
        java.util.Set<String> out = new java.util.HashSet<>();
        String kept = I18n.setting(setting);
        if (kept != null) for (String k : kept.split(",")) if (!k.isBlank()) out.add(k);
        return out;
    }

    /** A click on the star column turns the row's star on or off, remembers it and runs changed. */
    static void starClicks(JTable table, int column, java.util.function.IntFunction<String> keyOf, java.util.Set<String> starred, String setting, Runnable changed) {
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int r = table.rowAtPoint(e.getPoint()), c = table.columnAtPoint(e.getPoint());
                if (r < 0 || c != column) return;
                String key = keyOf.apply(table.convertRowIndexToModel(r));
                if (!starred.remove(key)) starred.add(key);
                I18n.remember(setting, String.join(",", new java.util.TreeSet<>(starred)));
                changed.run();
            }
        });
    }

    @Override public Dimension getPreferredSize() { return new Dimension(folded() ? NARROW + 12 : WIDE, super.getPreferredSize().height); }
    @Override public Dimension getMinimumSize() { return getPreferredSize(); }

    /** The chosen key: ALL, FAVOURITES or a category's key. */
    String selected() { return selected; }

    /** Shows these entries: top ones (All, Favourites), a line, then the categories. A choice that is gone falls back to All. */
    void show(List<Entry> top, List<Entry> categories) {
        items.removeAll();
        boolean known = false;
        for (Entry e : top) { items.add(new Item(e)); known |= e.key().equals(selected); }
        JComponent line = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(Theme.EDGE);
                g.fillRect(4, getHeight() / 2, getWidth() - 8, 1);
            }
        };
        line.setPreferredSize(new Dimension(10, 13));
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 13));
        line.setAlignmentX(LEFT_ALIGNMENT);
        items.add(line);
        for (Entry e : categories) { items.add(new Item(e)); known |= e.key().equals(selected); }
        if (!known) selected = ALL;
        items.revalidate();
        items.repaint();
    }

    private void pick(String key) {
        selected = key;
        I18n.remember(setting + "Pick", key);
        items.repaint();
        picked.accept(key);
    }

    /** One line: the icon, the name and the count; only the icon while the panel is folded. */
    private final class Item extends JPanel {
        private final Entry entry;
        private boolean over;

        Item(Entry entry) {
            this.entry = entry;
            setOpaque(false);
            setAlignmentX(LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW));
            setPreferredSize(new Dimension(10, ROW));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(entry.label() + " · " + entry.count());
            getAccessibleContext().setAccessibleName(entry.label() + ", " + entry.count());
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { pick(entry.key()); }
                @Override public void mouseEntered(MouseEvent e) { over = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { over = false; repaint(); }
            });
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            boolean on = entry.key().equals(selected);
            if (on || over) {
                g2.setColor(on ? Theme.CONTROL_HOVER : Theme.CONTROL);
                g2.fillRoundRect(0, 1, getWidth(), getHeight() - 2, 8, 8);
            }
            int iconX = folded() ? (getWidth() - entry.icon().getIconWidth()) / 2 : 8;
            entry.icon().paintIcon(this, g2, iconX, (getHeight() - entry.icon().getIconHeight()) / 2);
            if (!folded()) {
                g2.setFont(on ? Theme.bold(13f) : Theme.text(13f));
                FontMetrics fm = g2.getFontMetrics();
                int base = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                String count = String.valueOf(entry.count());
                g2.setFont(Theme.mono(11f));
                int countW = g2.getFontMetrics().stringWidth(count);
                g2.setColor(Theme.MUTED);
                g2.drawString(count, getWidth() - 8 - countW, base);
                g2.setFont(on ? Theme.bold(13f) : Theme.text(13f));
                g2.setColor(Theme.TEXT);
                int room = getWidth() - 8 - countW - 8 - 36;
                String label = entry.label();
                while (label.length() > 1 && fm.stringWidth(label) > room) label = label.substring(0, label.length() - 2) + "…";
                g2.drawString(label, 36, base);
            }
            g2.dispose();
        }
    }
}
