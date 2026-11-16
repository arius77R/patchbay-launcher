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
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The launcher's look: the dark greys of Patchbay's own window (its PatchbayStyle), Bitwig's orange
 * as the accent, red for what is waiting (updates, as macOS shows them), Inter and JetBrains Mono
 * (bundled), thin frames with small corners.
 */
public final class Theme {
    private Theme() { }

    // Patchbay's PatchbayStyle palette.
    static final Color TITLE = new Color(0x17181A), FRAME = new Color(0x26282B), HEAD = new Color(0x313337);
    static final Color WELL = new Color(0x1C1D20), LINE = new Color(0x3B3D42), HAIR = new Color(0x25262A);
    static final Color TEXT = new Color(0xE4E5E7), SOFT = new Color(0xC9CBCF), MUTED = new Color(0x9C9FA5), DIM = new Color(0x63666C);
    static final Color FAULT = new Color(0xF0524A), FAULT_TEXT = new Color(0xF48A84);
    /** Bitwig's orange: the accent for everything you can do (ticks, the main button of a section, selection). */
    static final Color ACCENT = new Color(0xFF6A1A), ACCENT_INK = new Color(0x17181A), ACCENT_SEL = new Color(0x33241B), ACCENT_SOFT = new Color(0xFFA36B);
    /** The button that starts Patchbay. */
    static final Color START = new Color(0x2EC4B6), START_INK = new Color(0x06201D);
    /** Something waiting for you: the count on a tab, "update" in a list (as macOS marks updates). */
    static final Color BADGE = new Color(0xE5372E), UPDATE = new Color(0xFF6B62);
    static final Color CONTROL = new Color(0x2E3034), CONTROL_HOVER = new Color(0x36383D), EDGE = new Color(0x2E3034), NAV_ON = new Color(0x3A2619);

    private static Font regular, medium, semibold, mono;

    /** Loads the bundled fonts (Inter, JetBrains Mono); without them the system's faces stand in. */
    static void loadFonts() {
        regular = font("/fonts/Inter-Regular.ttf");
        medium = font("/fonts/Inter-Medium.ttf");
        semibold = font("/fonts/Inter-SemiBold.ttf");
        mono = font("/fonts/JetBrainsMonoNL-Regular.ttf");
    }

    private static Font font(String resource) {
        try (InputStream in = Theme.class.getResourceAsStream(resource)) {
            if (in == null) return null;
            Font f = Font.createFont(Font.TRUETYPE_FONT, in);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(f);
            return f;
        } catch (Exception ex) {
            return null;
        }
    }

    static Font text(float size) { return regular != null ? regular.deriveFont(size) : new Font(Font.SANS_SERIF, Font.PLAIN, Math.round(size)); }
    static Font medium(float size) { return medium != null ? medium.deriveFont(size) : text(size); }
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
        FontUIResource f = new FontUIResource(text(13f));
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
        d.put("TextField.border", new Round(4, LINE, new Insets(7, 10, 7, 10)));
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

    /** primary (accent), secondary (grey), start (starts Patchbay), nav (tabs), danger (outlined red). A client property "badge" (Integer) adds a red count. */
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
            b.setBorder(new EmptyBorder(0, 14, 0, 14));
            b.setFont(k.equals("primary") || k.equals("start") ? bold(13f) : text(13f));
            b.setHorizontalAlignment(SwingConstants.CENTER);
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override public Dimension getPreferredSize(JComponent c) {
            Dimension d = super.getPreferredSize(c);
            String k = kind((AbstractButton) c);
            int h = k.equals("nav") ? 30 : 32;
            if (Boolean.TRUE.equals(c.getClientProperty("small"))) h = 28;
            int w = d.width + (c.getClientProperty("badge") instanceof Integer n && n > 0 ? 26 : 0);
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
                case "start" -> !b.isEnabled() ? CONTROL : m.isPressed() ? START.darker() : m.isRollover() ? START.brighter() : START;
                case "nav" -> on ? NAV_ON : m.isRollover() ? FRAME : null;
                case "danger" -> m.isRollover() ? new Color(0x2E1A19) : null;
                default -> !b.isEnabled() ? new Color(0x26282B) : m.isPressed() ? LINE : m.isRollover() ? CONTROL_HOVER : CONTROL;
            };
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (bg != null) {
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), k.equals("nav") ? 6 : 8, k.equals("nav") ? 6 : 8);
            }
            if (k.equals("secondary") && b.isEnabled()) {
                g2.setColor(LINE);
                g2.drawRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, 8, 8);
            }
            if (k.equals("danger")) {
                g2.setColor(FAULT);
                g2.drawRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, 8, 8);
            }
            if (b.isFocusOwner()) {
                g2.setColor(ACCENT);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, c.getWidth() - 3, c.getHeight() - 3, 6, 6);
            }
            if (c.getClientProperty("badge") instanceof Integer n && n > 0) {
                String s = String.valueOf(n);
                g2.setFont(bold(11f));
                FontMetrics fm = g2.getFontMetrics();
                int w = Math.max(18, fm.stringWidth(s) + 10), x = c.getWidth() - w - 12, y = (c.getHeight() - 18) / 2;
                g2.setColor(BADGE);
                g2.fillRoundRect(x, y, w, 18, 18, 18);
                g2.setColor(Color.WHITE);
                g2.drawString(s, x + (w - fm.stringWidth(s)) / 2, y + (18 + fm.getAscent() - fm.getDescent()) / 2);
            }
            g2.dispose();
            super.paint(g, c);
        }

        @Override protected void paintText(Graphics g, AbstractButton b, Rectangle r, String text) {
            String k = kind(b);
            boolean on = Boolean.TRUE.equals(b.getClientProperty("current"));
            Color fg = !b.isEnabled() ? DIM : switch (k) {
                case "primary" -> ACCENT_INK;
                case "start" -> START_INK;
                case "nav" -> on ? Color.WHITE : SOFT;
                case "danger" -> FAULT_TEXT;
                default -> TEXT;
            };
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(fg);
            if (k.equals("nav") && on) g2.setFont(bold(13f));
            FontMetrics fm = g2.getFontMetrics();
            int shift = b.getClientProperty("badge") instanceof Integer n && n > 0 ? -13 : 0;
            BasicGraphicsUtils.drawStringUnderlineCharAt(b, g2, text, -1, r.x + shift, r.y + fm.getAscent());
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
            g2.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), 8, 8);
            g2.setColor(comboBox.hasFocus() ? ACCENT : LINE);
            g2.setStroke(new BasicStroke(comboBox.hasFocus() ? 1.5f : 1f));
            g2.drawRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, 8, 8);
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
                g2.fillRoundRect(x, y, 18, 18, 6, 6);
                g2.setColor(ACCENT_INK);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawPolyline(new int[] { x + 4, x + 8, x + 14 }, new int[] { y + 9, y + 13, y + 5 }, 3);
            } else {
                g2.setColor(enabled ? DIM : HAIR);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(x + 1, y + 1, 16, 16, 5, 5);
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
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            g2.setColor(EDGE);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
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

    /**
     * "Update available" across a section, as macOS shows it: the text and what is new on the left,
     * a red count and a chevron on the right. Hidden while there is nothing to update; clicking it runs onClick.
     */
    static final class UpdateBar extends JButton {
        private int count;
        private String detail = "";

        UpdateBar(Runnable onClick) {
            putClientProperty("kind", "plain");
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addActionListener(e -> onClick.run());
            setVisible(false);
        }

        static String title(int count) {
            return count == 1 ? PatchbayLauncher.t("Dostępne uaktualnienie", "Update available")
                : PatchbayLauncher.t("Dostępne uaktualnienia", "Updates available");
        }

        void show(int count, String detail) {
            this.count = count;
            this.detail = detail == null ? "" : detail;
            getAccessibleContext().setAccessibleName(title(count) + ": " + count + (this.detail.isEmpty() ? "" : ". " + this.detail));
            setToolTipText(this.detail.isEmpty() ? null : this.detail);
            setVisible(count > 0);
            repaint();
        }

        @Override public Dimension getPreferredSize() { return new Dimension(200, 40); }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, 40); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g2.setColor(getModel().isRollover() ? CONTROL : FRAME);
            g2.fillRoundRect(0, 0, w, h, 8, 8);
            g2.setColor(isFocusOwner() ? ACCENT : getModel().isRollover() ? LINE : EDGE);
            g2.setStroke(new BasicStroke(isFocusOwner() ? 2f : 1f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);
            // right: count and chevron
            String n = String.valueOf(count);
            g2.setFont(bold(12f));
            FontMetrics bm = g2.getFontMetrics();
            int bw = Math.max(20, bm.stringWidth(n) + 12), bx = w - 14 - 8 - 10 - bw, by = (h - 20) / 2;
            g2.setColor(BADGE);
            g2.fillRoundRect(bx, by, bw, 20, 20, 20);
            g2.setColor(Color.WHITE);
            g2.drawString(n, bx + (bw - bm.stringWidth(n)) / 2, by + (20 + bm.getAscent() - bm.getDescent()) / 2);
            g2.setColor(MUTED);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int cx = w - 14 - 6, cy = h / 2;
            g2.drawPolyline(new int[] { cx, cx + 5, cx }, new int[] { cy - 5, cy, cy + 5 }, 3);
            // left: title and what is new, cut to fit
            g2.setFont(text(13f));
            FontMetrics fm = g2.getFontMetrics();
            String head = title(count);
            int x = 14, base = (h + fm.getAscent() - fm.getDescent()) / 2, room = bx - 12 - x;
            g2.setColor(TEXT);
            g2.drawString(head, x, base);
            int used = fm.stringWidth(head);
            if (!detail.isEmpty() && room - used > 40) {
                String more = " · " + detail;
                while (more.length() > 4 && fm.stringWidth(more) > room - used) more = more.substring(0, more.length() - 2) + "…";
                if (more.endsWith("……")) more = more.substring(0, more.length() - 1);
                g2.setColor(MUTED);
                g2.drawString(more, x + used, base);
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

    /** Text that wraps to the width it is given; width is what it asks for when nothing gives it more. */
    static JLabel wrapped(String text, int width, Color color, float size) {
        String safe = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        JLabel l = new Wrap("<html>" + safe + "</html>", width);
        l.setFont(text(size));
        l.setForeground(color);
        return l;
    }

    /**
     * An HTML label that wraps at its current width, so text in a panel the user can widen fills it.
     * It asks for the given width; a layout that gives it more (a column, the centre of a border layout) gets taller-to-shorter text.
     */
    static final class Wrap extends JLabel {
        private final int hint;
        private int measured = -1;

        Wrap(String html, int hint) {
            super(html);
            this.hint = hint;
            setVerticalAlignment(SwingConstants.TOP);
            addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override public void componentResized(java.awt.event.ComponentEvent e) {
                    if (getWidth() != measured) { measured = getWidth(); revalidate(); }
                }
            });
        }

        private int heightAt(int width) {
            Object v = getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey);
            Insets in = getInsets();
            if (!(v instanceof javax.swing.text.View view)) return super.getPreferredSize().height;
            int w = Math.max(20, width - in.left - in.right);
            view.setSize(w, 0);
            return (int) Math.ceil(view.getPreferredSpan(javax.swing.text.View.Y_AXIS)) + in.top + in.bottom;
        }

        @Override public Dimension getPreferredSize() {
            if (isPreferredSizeSet()) return super.getPreferredSize();
            return new Dimension(hint, heightAt(getWidth() > 0 ? getWidth() : hint));
        }
        @Override public Dimension getMinimumSize() { return new Dimension(Math.min(hint, 60), getPreferredSize().height); }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
    }

    /** A picture that takes the width it is given (up to its own) and keeps its proportions; at most maxHeight tall. */
    static final class Picture extends JComponent {
        private java.awt.image.BufferedImage image;
        private final int hint, maxHeight;
        private int measured = -1;

        Picture(int hint, int maxHeight) {
            this.hint = hint;
            this.maxHeight = maxHeight;
            addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override public void componentResized(java.awt.event.ComponentEvent e) {
                    if (getWidth() != measured) { measured = getWidth(); revalidate(); repaint(); }
                }
            });
        }

        void setImage(java.awt.image.BufferedImage img) { image = img; revalidate(); repaint(); }

        @Override public javax.accessibility.AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {
                @Override public javax.accessibility.AccessibleRole getAccessibleRole() { return javax.accessibility.AccessibleRole.ICON; }
            };
            return accessibleContext;
        }

        private Dimension fit(int width) {
            if (image == null) return new Dimension(0, 0);
            double scale = Math.min(1.0, Math.min((double) width / image.getWidth(), (double) maxHeight / image.getHeight()));
            return new Dimension(Math.max(1, (int) (image.getWidth() * scale)), Math.max(1, (int) (image.getHeight() * scale)));
        }

        @Override public Dimension getPreferredSize() {
            if (image == null) return new Dimension(0, 0);
            return new Dimension(Math.min(hint, image.getWidth()), fit(getWidth() > 0 ? getWidth() : hint).height);
        }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }

        @Override protected void paintComponent(Graphics g) {
            if (image == null) return;
            Dimension d = fit(getWidth());
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.drawImage(image, 0, 0, d.width, d.height, null);
            g2.dispose();
        }
    }

    /** A copy no wider than maxWidth, scaled once with care, for pictures shown many times. */
    static java.awt.image.BufferedImage shrink(java.awt.image.BufferedImage img, int maxWidth) {
        if (img.getWidth() <= maxWidth) return img;
        int w = maxWidth, h = Math.max(1, (int) Math.round(img.getHeight() * (double) maxWidth / img.getWidth()));
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(img.getScaledInstance(w, h, Image.SCALE_SMOOTH), 0, 0, null);
        g.dispose();
        return out;
    }

    // ---------- the list and its details, side by side ----------

    /** The details' share of the width, the same in every section; dragging the divider changes it, a double-click resets it. */
    static final double DETAILS_SHARE = 0.36;
    private static final java.util.List<JSplitPane> SPLITS = new java.util.ArrayList<>();

    static double detailsShare() {
        try {
            double v = Double.parseDouble(String.valueOf(I18n.setting("detailsShare")));
            return Math.max(0.2, Math.min(0.5, v));
        } catch (RuntimeException none) {
            return DETAILS_SHARE;
        }
    }

    /** A list on the left and its details on the right with a divider between them that the user can drag. */
    static JSplitPane split(JComponent list, JComponent details) {
        JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, list, details);
        sp.setUI(new SplitUI());
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.setDividerSize(16);
        list.setMinimumSize(new Dimension(520, 100));
        details.setMinimumSize(new Dimension(280, 100));
        sp.setResizeWeight(1 - detailsShare());
        sp.getAccessibleContext().setAccessibleName(PatchbayLauncher.t("Podział między listą a szczegółami", "Divider between the list and the details"));
        SPLITS.add(sp);
        sp.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) { place(sp, detailsShare()); }
        });
        return sp;
    }

    /** Puts the divider where the details get this share of the width, keeping both sides above their minimum. */
    private static void place(JSplitPane sp, double share) {
        int room = sp.getWidth() - sp.getDividerSize();
        if (room <= 0) return;
        int details = (int) Math.round(room * share);
        details = Math.max(sp.getRightComponent().getMinimumSize().width, Math.min(details, room - sp.getLeftComponent().getMinimumSize().width));
        sp.setResizeWeight(1 - share);
        sp.setDividerLocation(room - details);
    }

    private static void remember(double share) {
        I18n.remember("detailsShare", String.format(Locale.ROOT, "%.3f", share));
        for (JSplitPane other : SPLITS) place(other, share);
    }

    /** The divider: a short bar in the middle that turns orange under the pointer; dragging it or double-clicking resets. */
    static final class SplitUI extends javax.swing.plaf.basic.BasicSplitPaneUI {
        @Override public javax.swing.plaf.basic.BasicSplitPaneDivider createDefaultDivider() {
            javax.swing.plaf.basic.BasicSplitPaneDivider d = new javax.swing.plaf.basic.BasicSplitPaneDivider(this) {
                private boolean over;
                {
                    setBorder(null);
                    setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
                    addMouseListener(new java.awt.event.MouseAdapter() {
                        @Override public void mouseEntered(java.awt.event.MouseEvent e) { over = true; repaint(); }
                        @Override public void mouseExited(java.awt.event.MouseEvent e) { over = false; repaint(); }
                        @Override public void mouseReleased(java.awt.event.MouseEvent e) {
                            JSplitPane sp = splitPane;
                            int room = sp.getWidth() - sp.getDividerSize();
                            if (room > 0) remember((double) (room - sp.getDividerLocation()) / room);
                        }
                        @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                            if (e.getClickCount() == 2) remember(DETAILS_SHARE);
                        }
                    });
                }
                @Override public void paint(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(over ? ACCENT : DIM);
                    int w = 4, h = 44;
                    g2.fillRoundRect((getWidth() - w) / 2, (getHeight() - h) / 2, w, h, 4, 4);
                    g2.dispose();
                }
            };
            return d;
        }
        @Override public void installUI(JComponent c) {
            super.installUI(c);
            c.setBorder(null);
        }
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
        head.setBorder(new EmptyBorder(16, 18, 12, 18));
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel t = label(title, bold(20f), TEXT);
        titles.add(t);
        if (subtitle != null) {
            titles.add(Box.createVerticalStrut(3));
            if (subtitle instanceof JLabel l && l.getClientProperty("prose") == null) l.setFont(mono(12f));
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
            wrap.setBorder(new EmptyBorder(0, 18, footLeft == null && footRight == null ? 18 : 0, 18));
            wrap.add(body, BorderLayout.CENTER);
            p.add(wrap, BorderLayout.CENTER);
        }
        if (footLeft != null || footRight != null) {
            JPanel foot = new JPanel(new BorderLayout(12, 0));
            foot.setOpaque(false);
            foot.setBorder(new EmptyBorder(12, 18, 14, 18));
            if (footLeft != null) foot.add(footLeft, BorderLayout.CENTER);
            if (footRight != null) foot.add(footRight, BorderLayout.EAST);
            p.add(foot, BorderLayout.SOUTH);
        }
        return p;
    }

    // ---------- tables ----------

    static void styleTable(JTable table) {
        table.setRowHeight(34);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setGridColor(HAIR);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setFillsViewportHeight(true);
        table.setBackground(WELL);
        table.setSelectionBackground(ACCENT_SEL);
        table.setSelectionForeground(TEXT);
        table.setFont(text(13f));
        JTableHeader h = table.getTableHeader();
        h.setReorderingAllowed(false);
        h.setPreferredSize(new Dimension(10, 28));
        h.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, v == null ? "" : v.toString().toUpperCase(Locale.ROOT), false, false, r, c);
                l.setBackground(FRAME);
                l.setForeground(MUTED);
                l.setFont(mono(11f));
                l.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, EDGE), new EmptyBorder(0, 10, 0, 6)));
                return l;
            }
        });
        DefaultTableCellRenderer cells = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, s, false, r, c);
                l.setBorder(new EmptyBorder(0, 10, 0, 6));
                l.setFont(text(13f));
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
                // the selected row is marked with an accent edge on its left, as in the design
                box.setBorderPainted(s);
                box.setBorder(s ? BorderFactory.createMatteBorder(0, 2, 0, 0, ACCENT) : new EmptyBorder(0, 0, 0, 0));
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
                l.setFont(medium(13f));
                l.setForeground(dim ? MUTED : TEXT);
                return l;
            }
        };
    }

    /**
     * Status as a small drawn mark and coloured text; the value is "kind|label" with kind update (red dot),
     * installed (tick), new (plus), off (cross: can't run here) or active (accent dot).
     */
    static TableCellRenderer pillCells() {
        return new TableCellRenderer() {
            private final Status cell = new Status();
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                String[] parts = v == null ? new String[] { "", "" } : v.toString().split("\\|", 2);
                cell.kind = parts[0];
                cell.text = parts.length > 1 ? parts[1] : "";
                cell.setBackground(s ? ACCENT_SEL : WELL);
                cell.setToolTipText(cell.text.isEmpty() ? null : cell.text);
                return cell;
            }
        };
    }

    static Color statusColor(String kind) {
        return switch (kind) { case "update" -> UPDATE; case "active" -> ACCENT; case "installed" -> SOFT; case "off" -> DIM; default -> MUTED; };
    }

    /** The mark before a status: dot, tick, plus or cross, drawn so it never depends on a font's glyphs. */
    static void paintMark(Graphics2D g2, String kind, int x, int cy, Color color) {
        g2.setColor(color);
        switch (kind) {
            case "update", "active" -> g2.fillOval(x + 1, cy - 4, 8, 8);
            case "installed" -> {
                g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawPolyline(new int[] { x, x + 3, x + 9 }, new int[] { cy, cy + 3, cy - 4 }, 3);
            }
            case "off" -> {
                g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(x + 1, cy - 4, x + 8, cy + 3);
                g2.drawLine(x + 8, cy - 4, x + 1, cy + 3);
            }
            default -> {
                g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(x + 1, cy, x + 9, cy);
                g2.drawLine(x + 5, cy - 4, x + 5, cy + 4);
            }
        }
    }

    private static final class Status extends JComponent {
        String kind = "", text = "";
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(getBackground());
            g2.fillRect(0, 0, getWidth(), getHeight());
            if (text.isEmpty()) { g2.dispose(); return; }
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Color color = statusColor(kind);
            int cy = getHeight() / 2, x = 10;
            paintMark(g2, kind, x, cy, color);
            g2.setFont(kind.equals("update") || kind.equals("active") ? medium(13f) : text(13f));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(color);
            String shown = text;
            int room = getWidth() - x - 16 - 6;
            if (fm.stringWidth(shown) > room) {
                while (shown.length() > 1 && fm.stringWidth(shown + "…") > room) shown = shown.substring(0, shown.length() - 1);
                shown += "…";
            }
            g2.drawString(shown, x + 16, cy + (fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }

    /** A scroll pane with a hairline frame, for tables and lists. */
    static JScrollPane framed(JComponent inside) {
        JScrollPane s = new JScrollPane(inside);
        s.setBorder(BorderFactory.createLineBorder(EDGE));
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
            g.fill = GridBagConstraints.HORIZONTAL;
            p.add(value, g);
            g.fill = GridBagConstraints.NONE;
        }
        return p;
    }
}
