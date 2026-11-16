import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The launcher's look: the colors and faces of Patchbay's own window (its PatchbayStyle and the
 * Outfit and JetBrains Mono fonts it ships), so the two windows read as one product.
 */
public final class Theme {
    private Theme() { }

    // Patchbay's PatchbayStyle palette.
    static final Color TITLE = new Color(0x17181A), FRAME = new Color(0x26282B), HEAD = new Color(0x313337);
    static final Color WELL = new Color(0x1C1D20), LINE = new Color(0x3B3D42), HAIR = new Color(0x25262A);
    static final Color TEXT = new Color(0xE4E5E7), SOFT = new Color(0xC9CBCF), MUTED = new Color(0x9C9FA5), DIM = new Color(0x63666C);
    static final Color FAULT = new Color(0xF0524A), FAULT_TEXT = new Color(0xF48A84);
    /** Bitwig's orange, for the button that starts Patchbay (and with it Bitwig). */
    static final Color BITWIG = new Color(0xFF6A1A), BITWIG_INK = new Color(0x17181A);
    static final Color ACCENT = new Color(0x2EC4B6), ACCENT_INK = new Color(0x06201D), ACCENT_SEL = new Color(0x1E3836), ACCENT_SOFT = new Color(0x7FDCD2);
    static final Color CONTROL = new Color(0x2E3034), CONTROL_HOVER = new Color(0x36383D);

    private static Font regular, semibold, mono;

    /** Loads Patchbay's fonts from its control jar; without one the system's faces stand in. */
    static void loadFonts(Path controlJar) {
        if (controlJar == null || !Files.isRegularFile(controlJar)) return;
        try (ZipFile z = new ZipFile(controlJar.toFile())) {
            regular = font(z, "dev/bitwigre/bml/fonts/Outfit-Regular.ttf", regular);
            semibold = font(z, "dev/bitwigre/bml/fonts/Outfit-SemiBold.ttf", semibold);
            mono = font(z, "dev/bitwigre/bml/fonts/JetBrainsMono-Regular.ttf", mono);
        } catch (Exception ignored) { }
    }

    private static Font font(ZipFile z, String path, Font fallback) {
        ZipEntry e = z.getEntry(path);
        if (e == null) return fallback;
        try (InputStream in = z.getInputStream(e)) {
            Font f = Font.createFont(Font.TRUETYPE_FONT, in);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(f);
            return f;
        } catch (Exception ex) {
            return fallback;
        }
    }

    static Font text(float size) { return regular != null ? regular.deriveFont(size) : new Font(Font.SANS_SERIF, Font.PLAIN, Math.round(size)); }
    static Font bold(float size) { return semibold != null ? semibold.deriveFont(size) : new Font(Font.SANS_SERIF, Font.BOLD, Math.round(size)); }
    static Font mono(float size) { return mono != null ? mono.deriveFont(size) : new Font(Font.MONOSPACED, Font.PLAIN, Math.round(size)); }

    /** Sets the look before any window is built. */
    static void install() {
        try {
            UIManager.setLookAndFeel(new MetalLookAndFeel());
        } catch (Exception ignored) { }
        UIDefaults d = UIManager.getDefaults();
        d.put("ClassLoader", Theme.class.getClassLoader());
        d.put("ButtonUI", FlatButtonUI.class.getName());
        d.put("ScrollBarUI", ThinScrollBarUI.class.getName());
        d.put("ComboBoxUI", FlatComboUI.class.getName());
        FontUIResource f = new FontUIResource(text(14f));
        for (String k : new String[] { "Label", "Button", "ComboBox", "TextField", "TextArea", "Table", "TableHeader",
            "OptionPane", "List", "CheckBox", "ToolTip", "ProgressBar", "OptionPane.message", "OptionPane.button", "EditorPane" })
            d.put(k.endsWith(".message") || k.endsWith(".button") ? k + "Font" : k + ".font", f);
        put(d, WELL, "Panel.background", "OptionPane.background", "Viewport.background", "ScrollPane.background",
            "Table.background", "SplitPane.background", "CheckBox.background", "TextArea.background", "EditorPane.background", "ProgressBar.background");
        put(d, TEXT, "Label.foreground", "Panel.foreground", "OptionPane.messageForeground", "Table.foreground", "CheckBox.foreground",
            "TextField.foreground", "TextArea.foreground", "ComboBox.foreground", "Button.foreground", "TextField.caretForeground",
            "ComboBox.selectionForeground", "Table.selectionForeground", "List.foreground", "List.selectionForeground", "ToolTip.foreground", "EditorPane.foreground");
        put(d, ACCENT_SEL, "Table.selectionBackground", "ComboBox.selectionBackground", "List.selectionBackground", "TextField.selectionBackground");
        put(d, CONTROL, "ComboBox.background", "ComboBox.buttonBackground", "List.background", "ToolTip.background");
        put(d, TITLE, "TextField.background", "TextField.inactiveBackground");
        put(d, MUTED, "Label.disabledForeground", "ComboBox.disabledForeground", "TextField.inactiveForeground");
        put(d, HAIR, "Table.gridColor", "SplitPane.dividerFocusColor", "Separator.foreground");
        put(d, ACCENT, "ProgressBar.foreground", "ProgressBar.selectionBackground");
        put(d, CONTROL, "ComboBox.buttonShadow", "ComboBox.buttonHighlight", "ComboBox.buttonDarkShadow", "ComboBox.disabledBackground");
        d.put("ProgressBar.border", BorderFactory.createEmptyBorder());
        d.put("ProgressBar.selectionForeground", new ColorUIResource(ACCENT_INK));
        d.put("ComboBox.border", BorderFactory.createEmptyBorder(4, 8, 4, 4));
        d.put("TextField.border", new Round(6, LINE, new Insets(7, 10, 7, 10)));
        d.put("ToolTip.border", BorderFactory.createEmptyBorder(6, 8, 6, 8));
        d.put("ScrollPane.border", BorderFactory.createEmptyBorder());
        d.put("SplitPane.border", BorderFactory.createEmptyBorder());
        d.put("SplitPaneDivider.border", BorderFactory.createEmptyBorder());
        d.put("CheckBox.icon", new CheckIcon());
        d.put("Table.focusCellHighlightBorder", BorderFactory.createEmptyBorder());
        d.put("List.focusCellHighlightBorder", BorderFactory.createEmptyBorder(2, 8, 2, 8));
    }

    private static void put(UIDefaults d, Color c, String... keys) {
        for (String k : keys) d.put(k, new ColorUIResource(c));
    }

    // ---------- buttons ----------

    /** primary (accent), secondary (grey), nav (side menu), danger (outlined red). */
    static JButton button(String text, String kind) {
        JButton b = new JButton(text);
        b.putClientProperty("kind", kind);
        b.updateUI();
        return b;
    }

    public static final class FlatButtonUI extends BasicButtonUI {
        public static ComponentUI createUI(JComponent c) { return new FlatButtonUI(); }

        private static String kind(AbstractButton b) {
            Object k = b.getClientProperty("kind");
            return k == null ? "secondary" : k.toString();
        }

        @Override protected void installDefaults(AbstractButton b) {
            super.installDefaults(b);
            String k = kind(b);
            b.setOpaque(false);
            b.setContentAreaFilled(false);
            b.setFocusPainted(false);
            b.setRolloverEnabled(true);
            b.setBorder(new EmptyBorder(0, k.equals("nav") ? 12 : 14, 0, k.equals("nav") ? 12 : 14));
            b.setFont(k.equals("primary") || k.equals("bitwig") ? bold(14f) : k.equals("nav") ? text(14f) : text(13f));
            b.setHorizontalAlignment(k.equals("nav") ? SwingConstants.LEFT : SwingConstants.CENTER);
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override public Dimension getPreferredSize(JComponent c) {
            Dimension d = super.getPreferredSize(c);
            String k = kind((AbstractButton) c);
            int h = k.equals("primary") || k.equals("bitwig") || k.equals("nav") ? 38 : 34;
            if (Boolean.TRUE.equals(c.getClientProperty("small"))) h = 28;
            int w = d.width + (c.getClientProperty("badge") instanceof Integer n && n > 0 ? 34 : 0);
            return new Dimension(w, h);
        }

        @Override public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            ButtonModel m = b.getModel();
            String k = kind(b);
            if (k.equals("plain")) return;
            boolean on = Boolean.TRUE.equals(b.getClientProperty("current"));
            Color bg = switch (k) {
                case "primary" -> !b.isEnabled() ? CONTROL : m.isPressed() ? ACCENT.darker() : m.isRollover() ? ACCENT.brighter() : ACCENT;
                case "bitwig" -> !b.isEnabled() ? CONTROL : m.isPressed() ? new Color(0xE05A10) : m.isRollover() ? new Color(0xFF8340) : BITWIG;
                case "nav" -> on ? ACCENT_SEL : m.isRollover() ? FRAME : null;
                case "danger" -> m.isRollover() ? new Color(0x2E1A19) : null;
                default -> !b.isEnabled() ? new Color(0x26282B) : m.isPressed() ? LINE : m.isRollover() ? CONTROL_HOVER : CONTROL;
            };
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (bg != null) {
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), 12, 12);
            }
            if (k.equals("danger")) {
                g2.setColor(FAULT);
                g2.drawRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, 12, 12);
            }
            if (b.isFocusOwner() && !k.equals("nav")) {
                g2.setColor(ACCENT);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, c.getWidth() - 3, c.getHeight() - 3, 10, 10);
            }
            if (c.getClientProperty("badge") instanceof Integer n && n > 0) {
                String s = String.valueOf(n);
                g2.setFont(bold(12f));
                FontMetrics fm = g2.getFontMetrics();
                int w = Math.max(20, fm.stringWidth(s) + 12), x = c.getWidth() - w - 10, y = (c.getHeight() - 20) / 2;
                g2.setColor(ACCENT);
                g2.fillRoundRect(x, y, w, 20, 20, 20);
                g2.setColor(ACCENT_INK);
                g2.drawString(s, x + (w - fm.stringWidth(s)) / 2, y + (20 + fm.getAscent() - fm.getDescent()) / 2);
            }
            g2.dispose();
            super.paint(g, c);
        }

        @Override protected void paintText(Graphics g, AbstractButton b, Rectangle r, String text) {
            String k = kind(b);
            boolean on = Boolean.TRUE.equals(b.getClientProperty("current"));
            Color fg = !b.isEnabled() ? DIM : switch (k) {
                case "primary" -> ACCENT_INK;
                case "bitwig" -> BITWIG_INK;
                case "nav" -> on ? Color.WHITE : SOFT;
                case "danger" -> FAULT_TEXT;
                default -> TEXT;
            };
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(fg);
            if (k.equals("nav") && on) g2.setFont(bold(14f));
            FontMetrics fm = g2.getFontMetrics();
            BasicGraphicsUtils.drawStringUnderlineCharAt(b, g2, text, -1, r.x, r.y + fm.getAscent());
        }

        @Override protected void paintFocus(Graphics g, AbstractButton b, Rectangle v, Rectangle t, Rectangle i) { }
    }

    /** A drop-down like the buttons: a grey rounded field with a chevron, no focus line inside. */
    public static final class FlatComboUI extends javax.swing.plaf.basic.BasicComboBoxUI {
        public static ComponentUI createUI(JComponent c) { return new FlatComboUI(); }

        @Override protected void installDefaults() {
            super.installDefaults();
            comboBox.setOpaque(false);
            comboBox.setBorder(new EmptyBorder(0, 10, 0, 4));
            comboBox.setBackground(CONTROL);
            comboBox.setForeground(TEXT);
            comboBox.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override protected JButton createArrowButton() {
            JButton b = new JButton() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(isEnabled() ? MUTED : DIM);
                    g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    int cx = getWidth() / 2, cy = getHeight() / 2;
                    g2.drawPolyline(new int[] { cx - 4, cx, cx + 4 }, new int[] { cy - 2, cy + 2, cy - 2 }, 3);
                    g2.dispose();
                }
            };
            b.putClientProperty("kind", "plain");
            b.setOpaque(false);
            b.setContentAreaFilled(false);
            b.setBorderPainted(false);
            b.setFocusable(false);
            b.setBorder(new EmptyBorder(0, 0, 0, 0));
            b.setPreferredSize(new Dimension(28, 28));
            b.getAccessibleContext().setAccessibleName(PatchbayLauncher.t("Rozwiń listę", "Open the list"));
            return b;
        }

        @Override public void paint(Graphics g, JComponent c) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(!c.isEnabled() ? FRAME : comboBox.isPopupVisible() ? CONTROL_HOVER : CONTROL);
            g2.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), 12, 12);
            if (comboBox.hasFocus()) {
                g2.setColor(ACCENT);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(1, 1, c.getWidth() - 3, c.getHeight() - 3, 10, 10);
            }
            g2.dispose();
            super.paint(g, c);
        }

        @Override public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) { }

        @Override public void paintCurrentValue(Graphics g, Rectangle bounds, boolean hasFocus) {
            Object v = comboBox.getSelectedItem();
            if (v == null) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(comboBox.getFont());
            g2.setColor(comboBox.isEnabled() ? TEXT : DIM);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(v.toString(), bounds.x, bounds.y + (bounds.height + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }

        @Override protected javax.swing.plaf.basic.ComboPopup createPopup() {
            javax.swing.plaf.basic.BasicComboPopup p = new javax.swing.plaf.basic.BasicComboPopup(comboBox);
            p.setBorder(BorderFactory.createLineBorder(LINE));
            p.getList().setBackground(FRAME);
            p.getList().setForeground(TEXT);
            p.getList().setSelectionBackground(ACCENT_SEL);
            p.getList().setSelectionForeground(Color.WHITE);
            p.getList().setFont(comboBox.getFont());
            return p;
        }
    }

    public static final class ThinScrollBarUI extends BasicScrollBarUI {
        public static ComponentUI createUI(JComponent c) { return new ThinScrollBarUI(); }

        @Override protected void configureScrollBarColors() {
            thumbColor = CONTROL_HOVER;
            trackColor = WELL;
        }
        @Override protected JButton createDecreaseButton(int o) { return zero(); }
        @Override protected JButton createIncreaseButton(int o) { return zero(); }
        private static JButton zero() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }
        @Override public Dimension getPreferredSize(JComponent c) {
            return scrollbar.getOrientation() == JScrollBar.VERTICAL ? new Dimension(10, 48) : new Dimension(48, 10);
        }
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            g.setColor(trackColor);
            g.fillRect(r.x, r.y, r.width, r.height);
        }
        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isThumbRollover() ? LINE : thumbColor);
            g2.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 6, 6);
            g2.dispose();
        }
    }

    /** A rounded check box like Patchbay's: filled accent with a dark tick when on. */
    static final class CheckIcon implements Icon {
        @Override public int getIconWidth() { return 18; }
        @Override public int getIconHeight() { return 18; }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            boolean on = c instanceof AbstractButton b && b.getModel().isSelected();
            boolean enabled = c.isEnabled();
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (on) {
                g2.setColor(enabled ? ACCENT : DIM);
                g2.fillRoundRect(x, y, 18, 18, 8, 8);
                g2.setColor(ACCENT_INK);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawPolyline(new int[] { x + 4, x + 8, x + 14 }, new int[] { y + 9, y + 13, y + 5 }, 3);
            } else {
                g2.setColor(enabled ? DIM : HAIR);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(x + 1, y + 1, 16, 16, 7, 7);
            }
            g2.dispose();
        }
    }

    /** A line around a component with rounded corners. */
    static final class Round extends AbstractBorder {
        private final int radius;
        private final Color color;
        private final Insets pad;
        Round(int radius, Color color, Insets pad) { this.radius = radius; this.color = color; this.pad = pad; }
        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(c.isFocusOwner() ? ACCENT : color);
            g2.drawRoundRect(x, y, w - 1, h - 1, radius * 2, radius * 2);
            g2.dispose();
        }
        @Override public Insets getBorderInsets(Component c) { return new Insets(pad.top, pad.left, pad.bottom, pad.right); }
    }

    /** A column for a scroll pane that takes the pane's width, so rows with right-hand parts never run off the edge. */
    static final class Column extends JPanel implements Scrollable {
        Column() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    /** A panel with a rounded fill and a hairline, for cards. */
    static class Card extends JPanel {
        private final Color fill;
        Card(LayoutManager layout, Color fill) {
            super(layout);
            this.fill = fill;
            setOpaque(false);
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
            g2.setColor(HAIR);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            g2.dispose();
        }
    }

    /** An on/off switch (a real toggle button: Tab reaches it, Space flips it); the consumer gets the new state. */
    static final class Switch extends JToggleButton {
        Switch(boolean on, String label, Consumer<Boolean> changed) {
            super("", on);
            putClientProperty("kind", "switch");
            setPreferredSize(new Dimension(40, 24));
            setMinimumSize(new Dimension(40, 24));
            setToolTipText(label);
            getAccessibleContext().setAccessibleName(label);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(0, 0, 0, 0));
            addActionListener(e -> changed.accept(isSelected()));
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean on = isSelected();
            int x = (getWidth() - 36) / 2, y = (getHeight() - 20) / 2;
            g2.setColor(on ? ACCENT : LINE);
            g2.fillRoundRect(x, y, 36, 20, 20, 20);
            g2.setColor(on ? ACCENT_INK : MUTED);
            g2.fillOval(x + (on ? 18 : 2), y + 2, 16, 16);
            if (isFocusOwner()) {
                g2.setColor(ACCENT);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(x - 2, y - 2, 39, 23, 24, 24);
            }
            g2.dispose();
        }
    }

    // ---------- labels and layout ----------

    static JLabel label(String text, Font font, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font);
        l.setForeground(color);
        return l;
    }

    /** Text that wraps at the given width. */
    static JLabel wrapped(String text, int width, Color color, float size) {
        String safe = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        JLabel l = new JLabel("<html><body style='width:" + width + "px'>" + safe + "</body></html>");
        l.setFont(text(size));
        l.setForeground(color);
        return l;
    }

    /** "Track: Hibernate Tracks" as a grey prefix and a light name. */
    static String twoTone(String name) {
        int colon = name.indexOf(':');
        String safe = name.replace("&", "&amp;").replace("<", "&lt;");
        if (colon < 0 || colon > 30) return "<html><nobr>" + safe + "</nobr></html>";
        return "<html><nobr><font color='#9C9FA5'>" + safe.substring(0, colon + 1) + "</font>" + safe.substring(colon + 1) + "</nobr></html>";
    }

    static JPanel row(int gap, Component... parts) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) p.add(Box.createHorizontalStrut(gap));
            p.add(parts[i]);
        }
        return p;
    }

    /**
     * A section page: a title and a line under it, actions on the right, the body, and a footer
     * with a status line on the left and actions on the right. Any part may be null.
     */
    static JPanel page(String title, JComponent subtitle, JComponent actions, JComponent body, JComponent footLeft, JComponent footRight) {
        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBackground(WELL);
        JPanel head = new JPanel(new BorderLayout(16, 0));
        head.setOpaque(false);
        head.setBorder(new EmptyBorder(24, 24, 16, 24));
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel t = label(title, bold(26f), TEXT);
        titles.add(t);
        if (subtitle != null) {
            titles.add(Box.createVerticalStrut(4));
            titles.add(subtitle);
        }
        head.add(titles, BorderLayout.CENTER);
        if (actions != null) {
            JPanel right = new JPanel(new BorderLayout());
            right.setOpaque(false);
            right.add(actions, BorderLayout.SOUTH);
            head.add(right, BorderLayout.EAST);
        }
        p.add(head, BorderLayout.NORTH);
        if (body != null) {
            JPanel wrap = new JPanel(new BorderLayout());
            wrap.setOpaque(false);
            wrap.setBorder(new EmptyBorder(0, 24, footLeft == null && footRight == null ? 24 : 0, 24));
            wrap.add(body, BorderLayout.CENTER);
            p.add(wrap, BorderLayout.CENTER);
        }
        if (footLeft != null || footRight != null) {
            JPanel foot = new JPanel(new BorderLayout(12, 0));
            foot.setOpaque(false);
            foot.setBorder(new EmptyBorder(14, 24, 18, 24));
            if (footLeft != null) foot.add(footLeft, BorderLayout.CENTER);
            if (footRight != null) foot.add(footRight, BorderLayout.EAST);
            p.add(foot, BorderLayout.SOUTH);
        }
        return p;
    }

    // ---------- tables ----------

    static void styleTable(JTable table) {
        table.setRowHeight(38);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setGridColor(HAIR);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setFillsViewportHeight(true);
        table.setBackground(WELL);
        table.setSelectionBackground(ACCENT_SEL);
        table.setSelectionForeground(TEXT);
        table.setFont(text(14f));
        JTableHeader h = table.getTableHeader();
        h.setReorderingAllowed(false);
        h.setPreferredSize(new Dimension(10, 34));
        h.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, false, false, r, c);
                l.setBackground(FRAME);
                l.setForeground(MUTED);
                l.setFont(text(12f));
                l.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, HAIR), new EmptyBorder(0, 10, 0, 6)));
                return l;
            }
        });
        DefaultTableCellRenderer cells = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, s, false, r, c);
                l.setBorder(new EmptyBorder(0, 10, 0, 6));
                l.setFont(text(14f));
                l.setForeground(TEXT);
                return l;
            }
        };
        table.setDefaultRenderer(String.class, cells);
        table.setDefaultRenderer(Object.class, cells);
        TableCellRenderer checks = table.getDefaultRenderer(Boolean.class);
        table.setDefaultRenderer(Boolean.class, (t, v, s, f, r, c) -> {
            Component comp = checks.getTableCellRendererComponent(t, v, s, false, r, c);
            if (comp instanceof JCheckBox box) {
                box.setBorderPainted(false);
                box.setBorder(new EmptyBorder(0, 0, 0, 0));
                box.setHorizontalAlignment(SwingConstants.CENTER);
                box.setBackground(s ? ACCENT_SEL : WELL);
                box.setEnabled(t.isCellEditable(r, c) || Boolean.TRUE.equals(v));
            }
            return comp;
        });
        if (table.getDefaultEditor(Boolean.class) instanceof DefaultCellEditor e && e.getComponent() instanceof JCheckBox box) {
            box.setHorizontalAlignment(SwingConstants.CENTER);
            box.setBackground(ACCENT_SEL);
        }
    }

    /** Versions in the mono face. */
    static DefaultTableCellRenderer monoCells(Color color) {
        return new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, s, false, r, c);
                l.setBorder(new EmptyBorder(0, 10, 0, 6));
                l.setFont(mono(12f));
                l.setForeground(color);
                return l;
            }
        };
    }

    /** Names with a grey "Group:" prefix; a name starting with "~" is dimmed (a mod that can't run here). */
    static DefaultTableCellRenderer nameCells() {
        return new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                String name = v == null ? "" : v.toString();
                boolean dim = name.startsWith("~");
                if (dim) name = name.substring(1);
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, dim ? "<html><nobr>" + name.replace("&", "&amp;").replace("<", "&lt;") + "</nobr></html>" : twoTone(name), s, false, r, c);
                l.setToolTipText(name.isEmpty() ? null : name);
                l.setBorder(new EmptyBorder(0, 10, 0, 6));
                l.setFont(text(14f));
                l.setForeground(dim ? MUTED : TEXT);
                return l;
            }
        };
    }

    /** Status as a pill; the value is "kind|label" with kind update, installed, new or off (outlined: can't run here). */
    static TableCellRenderer pillCells() {
        return new TableCellRenderer() {
            private final Pill pill = new Pill();
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                String[] parts = v == null ? new String[] { "", "" } : v.toString().split("\\|", 2);
                pill.kind = parts[0];
                pill.text = parts.length > 1 ? parts[1] : "";
                pill.setBackground(s ? ACCENT_SEL : WELL);
                return pill;
            }
        };
    }

    private static final class Pill extends JComponent {
        String kind = "", text = "";
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(getBackground());
            g2.fillRect(0, 0, getWidth(), getHeight());
            if (text.isEmpty()) { g2.dispose(); return; }
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(bold(12f));
            FontMetrics fm = g2.getFontMetrics();
            int w = fm.stringWidth(text) + 18, h = 22, x = 10, y = (getHeight() - h) / 2;
            Color bg = switch (kind) { case "update" -> ACCENT; case "installed" -> ACCENT_SEL; default -> CONTROL; };
            Color fg = switch (kind) { case "update" -> ACCENT_INK; case "installed" -> ACCENT_SOFT; default -> SOFT; };
            if (kind.equals("installed") && getBackground().equals(ACCENT_SEL)) bg = WELL;
            if (kind.equals("off")) {
                g2.setColor(MUTED);
                g2.drawRoundRect(x, y, w - 1, h - 1, h, h);
                g2.setColor(MUTED);
                g2.drawString(text, x + 9, y + (h + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
                return;
            }
            g2.setColor(bg);
            g2.fillRoundRect(x, y, w, h, h, h);
            g2.setColor(fg);
            g2.drawString(text, x + 9, y + (h + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }

    /** A scroll pane with a hairline frame, for tables and lists. */
    static JScrollPane framed(JComponent inside) {
        JScrollPane s = new JScrollPane(inside);
        s.setBorder(BorderFactory.createLineBorder(HAIR));
        s.getViewport().setBackground(WELL);
        return s;
    }

    /** Key/value lines (Author, Available, Source…) between two hairlines. */
    static JPanel facts(String[][] rows) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, LINE), new EmptyBorder(10, 0, 10, 0)));
        GridBagConstraints g = new GridBagConstraints();
        g.anchor = GridBagConstraints.NORTHWEST;
        g.insets = new Insets(3, 0, 3, 12);
        for (int i = 0; i < rows.length; i++) {
            g.gridy = i;
            g.gridx = 0; g.weightx = 0;
            p.add(label(rows[i][0], text(13f), MUTED), g);
            g.gridx = 1; g.weightx = 1;
            boolean monoValue = rows[i].length > 2;
            // long values ("Bitwig 6.1.7 · Windows", "owner/repository") wrap instead of ending in "…"
            JLabel value = monoValue ? label(rows[i][1], mono(12f), TEXT)
                : wrapped(rows[i][1].replace("/", "/\u200B"), 104, TEXT, 13f);
            value.setToolTipText(rows[i][1]);
            p.add(value, g);
        }
        return p;
    }
}
