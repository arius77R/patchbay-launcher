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

    // The palette: dark is Patchbay's PatchbayStyle; light is the same layout on warm off-white. Set by palette() before any window is built.
    static Color TITLE, FRAME, HEAD, WELL, LINE, HAIR, TEXT, STRONG, SOFT, MUTED, DIM, FAULT, FAULT_TEXT, DANGER_HOVER, DISABLED;
    /** Bitwig's orange: the accent for everything you can do (ticks, the main button of a section, selection). */
    static Color ACCENT, ACCENT_INK, ACCENT_SEL, ACCENT_SOFT;
    /** The button that starts Patchbay. */
    static Color START, START_INK;
    /** Something waiting for you: the count on a tab, "update" in a list (as macOS marks updates). */
    static Color BADGE, UPDATE;
    static Color CONTROL, CONTROL_HOVER, EDGE, NAV_ON;
    private static boolean light;

    static { palette(false); }

    static boolean light() { return light; }

    /** The user's choice, kept in the launcher's settings: "light" or dark (the default). */
    static boolean lightChosen() { return "light".equals(I18n.setting("appearance")); }

    static void palette(boolean lightMode) {
        light = lightMode;
        if (!lightMode) {
            TITLE = new Color(0x17181A); FRAME = new Color(0x26282B); HEAD = new Color(0x313337);
            WELL = new Color(0x1C1D20); LINE = new Color(0x3B3D42); HAIR = new Color(0x25262A);
            TEXT = new Color(0xE4E5E7); STRONG = Color.WHITE; SOFT = new Color(0xC9CBCF); MUTED = new Color(0x9C9FA5); DIM = new Color(0x63666C);
            FAULT = new Color(0xF0524A); FAULT_TEXT = new Color(0xF48A84); DANGER_HOVER = new Color(0x2E1A19); DISABLED = new Color(0x26282B);
            ACCENT = new Color(0xFF6A1A); ACCENT_INK = new Color(0x17181A); ACCENT_SEL = new Color(0x33241B); ACCENT_SOFT = new Color(0xFFA36B);
            START = new Color(0x2EC4B6); START_INK = new Color(0x06201D);
            BADGE = new Color(0xE5372E); UPDATE = new Color(0xFF6B62);
            CONTROL = new Color(0x2E3034); CONTROL_HOVER = new Color(0x36383D); EDGE = new Color(0x2E3034); NAV_ON = new Color(0x3A2619);
        } else {
            TITLE = new Color(0xFBFBF9); FRAME = new Color(0xFFFFFF); HEAD = new Color(0xF1F1EE);
            WELL = new Color(0xF4F4F1); LINE = new Color(0xCFCFC9); HAIR = new Color(0xE6E6E1);
            TEXT = new Color(0x1D1E20); STRONG = new Color(0x0F1011); SOFT = new Color(0x33353A); MUTED = new Color(0x5C5F64); DIM = new Color(0x9A9CA1);
            FAULT = new Color(0xD93A30); FAULT_TEXT = new Color(0xB3261E); DANGER_HOVER = new Color(0xFCE8E6); DISABLED = new Color(0xECECE8);
            ACCENT = new Color(0xF2600F); ACCENT_INK = new Color(0x17181A); ACCENT_SEL = new Color(0xFCE6D8); ACCENT_SOFT = new Color(0xC2470A);
            START = new Color(0x2EC4B6); START_INK = new Color(0x06201D);
            BADGE = new Color(0xE5372E); UPDATE = new Color(0xC7362E);
            CONTROL = new Color(0xECECE7); CONTROL_HOVER = new Color(0xE2E2DC); EDGE = new Color(0xDEDED9); NAV_ON = new Color(0xFCE3D2);
        }
    }

    /** A colour for HTML in labels. */
    static String hex(Color c) { return String.format("#%06X", c.getRGB() & 0xFFFFFF); }

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

    /**
     * The look is Swing's own, which copies and pastes with Ctrl; on macOS ⌘C, ⌘V, ⌘X, ⌘A and ⌘Z
     * do it too, in every text field.
     */
    static void macEditKeys(UIDefaults d) {
        int cmd = java.awt.event.InputEvent.META_DOWN_MASK;
        String[][] keys = { { "C", javax.swing.text.DefaultEditorKit.copyAction }, { "V", javax.swing.text.DefaultEditorKit.pasteAction },
            { "X", javax.swing.text.DefaultEditorKit.cutAction }, { "A", javax.swing.text.DefaultEditorKit.selectAllAction } };
        for (String map : new String[] { "TextField.focusInputMap", "FormattedTextField.focusInputMap", "PasswordField.focusInputMap",
            "TextArea.focusInputMap", "TextPane.focusInputMap", "EditorPane.focusInputMap" }) {
            if (!(d.get(map) instanceof InputMap im)) continue;
            for (String[] k : keys) im.put(KeyStroke.getKeyStroke(k[0].charAt(0), cmd), k[1]);
            // ⌘← / ⌘→ to the start and end of the line, as everywhere else on a Mac
            im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_LEFT, cmd), javax.swing.text.DefaultEditorKit.beginLineAction);
            im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_RIGHT, cmd), javax.swing.text.DefaultEditorKit.endLineAction);
        }
    }

    private static boolean editMenuOn;

    /** Right-click in any text field: Cut, Copy, Paste, Select all. */
    private static void editMenu() {
        if (editMenuOn || GraphicsEnvironment.isHeadless()) return;
        editMenuOn = true;
        Toolkit.getDefaultToolkit().addAWTEventListener(e -> {
            if (!(e instanceof java.awt.event.MouseEvent m) || !m.isPopupTrigger()
                || !(m.getComponent() instanceof javax.swing.text.JTextComponent t) || !t.isShowing()) return;
            JPopupMenu menu = new JPopupMenu();
            boolean editable = t.isEditable() && t.isEnabled();
            boolean selected = t.getSelectionStart() != t.getSelectionEnd();
            JMenuItem cut = new JMenuItem(PatchbayLauncher.t("Wytnij", "Cut"));
            cut.setEnabled(editable && selected);
            cut.addActionListener(a -> t.cut());
            JMenuItem copy = new JMenuItem(PatchbayLauncher.t("Kopiuj", "Copy"));
            copy.setEnabled(selected);
            copy.addActionListener(a -> t.copy());
            JMenuItem paste = new JMenuItem(PatchbayLauncher.t("Wklej", "Paste"));
            paste.setEnabled(editable);
            paste.addActionListener(a -> t.paste());
            JMenuItem all = new JMenuItem(PatchbayLauncher.t("Zaznacz wszystko", "Select all"));
            all.addActionListener(a -> { t.requestFocusInWindow(); t.selectAll(); });
            menu.add(cut); menu.add(copy); menu.add(paste); menu.addSeparator(); menu.add(all);
            menu.show(t, m.getX(), m.getY());
            m.consume();
        }, AWTEvent.MOUSE_EVENT_MASK);
    }

    /** Sets the look before any window is built. */
    static void install() {
        SPLITS.clear(); // panes of a window built before (language or appearance changed)
        try {
            UIManager.setLookAndFeel(new MetalLookAndFeel());
        } catch (Exception ignored) { }
        UIDefaults d = UIManager.getDefaults();
        d.put("ClassLoader", Theme.class.getClassLoader());
        if (Platform.MAC) macEditKeys(d);
        editMenu();
        // the right-click menu in our colours, not Metal's light grey
        d.put("PopupMenu.background", FRAME);
        d.put("PopupMenu.border", new javax.swing.plaf.BorderUIResource(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(EDGE), new EmptyBorder(4, 0, 4, 0))));
        d.put("MenuItem.background", FRAME);
        d.put("MenuItem.foreground", TEXT);
        d.put("MenuItem.selectionBackground", ACCENT_SEL);
        d.put("MenuItem.selectionForeground", STRONG);
        d.put("MenuItem.disabledForeground", DIM);
        d.put("MenuItem.font", new FontUIResource(text(13f)));
        d.put("MenuItem.border", new javax.swing.plaf.BorderUIResource(new EmptyBorder(5, 12, 5, 24)));
        d.put("MenuItem.acceleratorForeground", MUTED);
        d.put("Separator.foreground", HAIR);
        d.put("Separator.background", FRAME);
        d.put("PopupMenuSeparator.foreground", HAIR);
        d.put("PopupMenuSeparator.background", FRAME);
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
                case "danger" -> m.isRollover() ? DANGER_HOVER : null;
                default -> !b.isEnabled() ? DISABLED : m.isPressed() ? LINE : m.isRollover() ? CONTROL_HOVER : CONTROL;
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
                case "nav" -> on ? STRONG : SOFT;
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
            int x = bounds.x, y = bounds.y + (bounds.height + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(v.toString(), x, y);
            g2.dispose();
        }

        /** The open list shows its values in the middle of each line. */
        @Override protected ListCellRenderer<Object> createRenderer() {
            javax.swing.plaf.basic.BasicComboBoxRenderer.UIResource r = new javax.swing.plaf.basic.BasicComboBoxRenderer.UIResource();
            r.setHorizontalAlignment(SwingConstants.CENTER);
            return r;
        }

        @Override protected javax.swing.plaf.basic.ComboPopup createPopup() {
            javax.swing.plaf.basic.BasicComboPopup p = new javax.swing.plaf.basic.BasicComboPopup(comboBox);
            p.setBorder(BorderFactory.createLineBorder(LINE));
            p.getList().setBackground(FRAME);
            p.getList().setForeground(TEXT);
            p.getList().setSelectionBackground(ACCENT_SEL);
            p.getList().setSelectionForeground(STRONG);
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
            // only our own drawing: the system look (Aqua on macOS) would paint a white button behind it
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
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
     * Dark or light, as a sun and a moon in one track: the round knob sits on the one in use
     * (sun on the left for light, moon on the right for dark). Clicking anywhere switches.
     */
    static final class ModeSwitch extends JToggleButton {
        private final String lightName, darkName;

        ModeSwitch(boolean light, String lightName, String darkName, Consumer<Boolean> changed) {
            super("", light);
            this.lightName = lightName;
            this.darkName = darkName;
            putClientProperty("kind", "switch");
            setPreferredSize(new Dimension(60, 28));
            setMinimumSize(new Dimension(60, 28));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(0, 0, 0, 0));
            // only our own drawing: the system look (Aqua on macOS) would paint a white button behind it
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            describe();
            addActionListener(e -> { describe(); repaint(); changed.accept(isSelected()); });
        }

        private void describe() {
            String now = isSelected() ? lightName : darkName;
            setToolTipText(now);
            getAccessibleContext().setAccessibleName(now);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            boolean light = isSelected();
            int w = 56, h = 26, x = (getWidth() - w) / 2, y = (getHeight() - h) / 2;
            g2.setColor(getModel().isRollover() ? CONTROL_HOVER : CONTROL);
            g2.fillRoundRect(x, y, w, h, h, h);
            g2.setColor(EDGE);
            g2.drawRoundRect(x, y, w - 1, h - 1, h, h);
            int d = h - 6;
            int sunX = x + 3, moonX = x + w - 3 - d, ky = y + 3;
            int kx = light ? sunX : moonX;
            g2.setColor(ACCENT);
            g2.fillOval(kx, ky, d, d);
            float scx = sunX + d / 2f, mcx = moonX + d / 2f, cy = ky + d / 2f;
            sun(g2, scx, cy, light ? Color.WHITE : DIM);
            moon(g2, mcx, cy, light ? DIM : Color.WHITE);
            if (isFocusOwner()) {
                g2.setColor(ACCENT);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(x - 2, y - 2, w + 3, h + 3, h + 4, h + 4);
            }
            g2.dispose();
        }

        private static void sun(Graphics2D g2, float cx, float cy, Color c) {
            g2.setColor(c);
            g2.fill(new java.awt.geom.Ellipse2D.Float(cx - 3.2f, cy - 3.2f, 6.4f, 6.4f));
            g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < 8; i++) {
                double a = Math.PI / 4 * i;
                float c1 = (float) Math.cos(a), s1 = (float) Math.sin(a);
                g2.draw(new java.awt.geom.Line2D.Float(cx + c1 * 5.4f, cy + s1 * 5.4f, cx + c1 * 7.2f, cy + s1 * 7.2f));
            }
        }

        private static void moon(Graphics2D g2, float cx, float cy, Color c) {
            java.awt.geom.Area moon = new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Float(cx - 5.5f, cy - 5.5f, 11f, 11f));
            moon.subtract(new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Float(cx - 2.2f, cy - 7.6f, 11f, 11f)));
            g2.setColor(c);
            g2.fill(moon);
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
        return "<html><nobr><font color='" + hex(MUTED) + "'>" + safe.substring(0, colon + 1) + "</font>" + safe.substring(colon + 1) + "</nobr></html>";
    }

    // Category colours after Bitwig's track colours; the light set is darker so it reads on the pale background.
    private static final Color[] CATEGORY_DARK = { new Color(0xFF5A6E), new Color(0xFF8F3A), new Color(0xF2D34A), new Color(0xBBDD21), new Color(0x4FC42A),
        new Color(0x26D098), new Color(0x1FD0E0), new Color(0x5CA8EE), new Color(0xC36EFF), new Color(0xFF6EC0) };
    private static final Color[] CATEGORY_LIGHT = { new Color(0xC2283E), new Color(0xC45F0A), new Color(0x9A7B00), new Color(0x6E8A00), new Color(0x2F8A12),
        new Color(0x12876A), new Color(0x0A8592), new Color(0x2F6FB8), new Color(0x8A3FC7), new Color(0xC23A86) };

    /** A colour for each category of a list: the categories in alphabetical order take the palette in turn, so neighbours never share one. */
    static final class Categories {
        private final java.util.Map<String, Integer> order = new java.util.HashMap<>();

        void reset(java.util.stream.Stream<String> categories) {
            order.clear();
            categories.filter(c -> !c.isEmpty()).map(c -> c.toLowerCase(Locale.ROOT)).distinct().sorted().forEach(c -> order.put(c, order.size()));
        }

        Color colour(String category) {
            Integer i = order.get(category.toLowerCase(Locale.ROOT));
            Color[] palette = light ? CATEGORY_LIGHT : CATEGORY_DARK;
            return i == null ? MUTED : palette[i % palette.length];
        }
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
    /**
     * A section with a list, the same on every such tab: the title with the counts at the right of its line,
     * an optional strip under it, the list beside its details (see split with tools), and the foot.
     */
    static JPanel listPage(String title, JLabel stats, JComponent strip, JComponent body, JComponent footLeft, JComponent footRight) {
        stats.setFont(mono(12f));
        stats.setHorizontalAlignment(SwingConstants.RIGHT);
        JPanel titleLine = new JPanel(new BorderLayout(16, 0));
        titleLine.setOpaque(false);
        titleLine.add(label(title, bold(20f), TEXT), BorderLayout.WEST);
        titleLine.add(stats, BorderLayout.CENTER);
        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);
        top.add(titleLine, BorderLayout.NORTH);
        if (strip != null) top.add(strip, BorderLayout.SOUTH);
        JPanel all = new JPanel(new BorderLayout(0, 10));
        all.setOpaque(false);
        all.add(top, BorderLayout.NORTH);
        all.add(body, BorderLayout.CENTER);
        JPanel p = page(title, null, null, all, footLeft, footRight);
        // page() adds its own title line: this layout has the title in its first row instead
        p.remove(((BorderLayout) p.getLayout()).getLayoutComponent(BorderLayout.NORTH));
        ((JComponent) ((BorderLayout) p.getLayout()).getLayoutComponent(BorderLayout.CENTER)).setBorder(
            new EmptyBorder(16, 18, footLeft == null && footRight == null ? 18 : 0, 18));
        return p;
    }

    /** A text field that says what it is for while it is empty ("Search"), in a quieter colour. */
    static final class HintField extends JTextField {
        private final String hint;

        HintField(String hint) {
            this.hint = hint;
            getAccessibleContext().setAccessibleName(hint);
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (!getText().isEmpty()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(getFont());
            g2.setColor(DIM);
            Insets in = getInsets();
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(hint, in.left, in.top + (getHeight() - in.top - in.bottom + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }

    /**
     * The list beside its details with a row of controls over each, so the controls follow the divider:
     * tools at the list's left, atListEdge (Tick all) at the list's right edge, atEdge (Refresh) at the window's.
     */
    static JSplitPane split(JComponent tools, JComponent atListEdge, JComponent list, JComponent besideRefresh, JComponent atEdge, JComponent details) {
        JPanel overList = new JPanel(new BorderLayout(8, 0));
        overList.setOpaque(false);
        overList.add(tools, BorderLayout.WEST);
        overList.add(atListEdge, BorderLayout.EAST);
        JPanel left = new JPanel(new BorderLayout(0, 10));
        left.setOpaque(false);
        left.add(overList, BorderLayout.NORTH);
        left.add(list, BorderLayout.CENTER);
        JPanel overDetails = new JPanel(new BorderLayout(8, 0));
        overDetails.setOpaque(false);
        if (besideRefresh != null) overDetails.add(besideRefresh, BorderLayout.WEST);
        overDetails.add(atEdge, BorderLayout.EAST);
        // both rows are as tall as the taller one, so the list and the details start on one line
        int tall = Math.max(overList.getPreferredSize().height, overDetails.getPreferredSize().height);
        overList.setPreferredSize(new Dimension(overList.getPreferredSize().width, tall));
        overDetails.setPreferredSize(new Dimension(overDetails.getPreferredSize().width, tall));
        JPanel right = new JPanel(new BorderLayout(0, 10));
        right.setOpaque(false);
        right.add(overDetails, BorderLayout.NORTH);
        right.add(details, BorderLayout.CENTER);
        return split(left, right);
    }

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

    /** Names; a name starting with "~" is dimmed (a mod that can't run here). */
    static DefaultTableCellRenderer nameCells() {
        return new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                String name = v == null ? "" : v.toString();
                boolean dim = name.startsWith("~");
                if (dim) name = name.substring(1);
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, name, s, false, r, c);
                l.setToolTipText(name.isEmpty() ? null : name);
                l.setBorder(new EmptyBorder(0, 10, 0, 6));
                l.setFont(medium(13f));
                l.setForeground(dim ? MUTED : TEXT);
                return l;
            }
        };
    }

    /** The Type column of Mods and Devices: the same width in both, values in their category colours. */
    static void typeColumn(javax.swing.table.TableColumn column, Categories categories) {
        column.setMinWidth(118);
        column.setMaxWidth(130);
        column.setCellRenderer(categoryCells(categories));
    }

    /** A Type column in its category colour; a value starting with "~" is dimmed (a mod that can't run here). */
    private static DefaultTableCellRenderer categoryCells(Categories categories) {
        return new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                String category = v == null ? "" : v.toString();
                boolean dim = category.startsWith("~");
                if (dim) category = category.substring(1);
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, category, s, false, r, c);
                l.setToolTipText(category.isEmpty() ? null : category);
                l.setBorder(new EmptyBorder(0, 10, 0, 6));
                l.setFont(text(13f));
                l.setForeground(dim ? DIM : categories.colour(category));
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

    /** A column of details that scrolls when it is taller than its place, without a frame and following the width. */
    static JScrollPane scrolling(JComponent col) {
        Column holder = new Column();
        col.setAlignmentX(Component.LEFT_ALIGNMENT);
        holder.add(col);
        JScrollPane s = new JScrollPane(holder, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        s.setBorder(BorderFactory.createEmptyBorder());
        s.setOpaque(false);
        s.getViewport().setOpaque(false);
        s.getVerticalScrollBar().setUnitIncrement(16);
        return s;
    }

    /** A scroll pane with a hairline frame, for tables and lists. */
    static JScrollPane framed(JComponent inside) {
        JScrollPane s = new JScrollPane(inside);
        s.setBorder(BorderFactory.createLineBorder(EDGE));
        s.getViewport().setBackground(WELL);
        return s;
    }

    /** Key/value lines (Author, Available, Source…) between two hairlines. */
    /** A GitHub user's picture in a circle; their initial until the picture has loaded or when there is none. */
    static final class Avatar implements Icon {
        private static final int SIZE = 22;
        private final String owner;
        private Image picture;

        Avatar(String owner) { this.owner = owner; }

        void setPicture(Image picture) { this.picture = picture; }

        /** The owner's name beside their picture, as one line. */
        JLabel withName() {
            JLabel l = new JLabel(owner, this, SwingConstants.LEFT);
            l.setIconTextGap(8);
            l.setFont(text(13f));
            l.setForeground(TEXT);
            l.setToolTipText(owner);
            return l;
        }

        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            java.awt.geom.Ellipse2D circle = new java.awt.geom.Ellipse2D.Float(x, y, SIZE, SIZE);
            if (picture != null) {
                g2.clip(circle);
                g2.drawImage(picture, x, y, SIZE, SIZE, null);
            } else {
                g2.setColor(CONTROL);
                g2.fill(circle);
                g2.setColor(MUTED);
                g2.setFont(bold(11f));
                String initial = owner.isEmpty() ? "?" : owner.substring(0, 1).toUpperCase(Locale.ROOT);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(initial, x + (SIZE - fm.stringWidth(initial)) / 2f, y + (SIZE - fm.getHeight()) / 2f + fm.getAscent());
            }
            g2.dispose();
        }

        @Override public int getIconWidth() { return SIZE; }
        @Override public int getIconHeight() { return SIZE; }
    }

    /** A value may be text, or a component such as an author with their picture. */
    static JPanel facts(Object[][] rows) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, LINE), new EmptyBorder(10, 0, 10, 0)));
        GridBagConstraints g = new GridBagConstraints();
        g.anchor = GridBagConstraints.NORTHWEST;
        g.insets = new Insets(3, 0, 3, 12);
        for (int i = 0; i < rows.length; i++) {
            g.gridy = i;
            g.gridx = 0; g.weightx = 0;
            p.add(label(String.valueOf(rows[i][0]), text(13f), MUTED), g);
            g.gridx = 1; g.weightx = 1;
            boolean monoValue = rows[i].length > 2;
            String text = String.valueOf(rows[i][1]);
            // long values ("Bitwig 6.1.7 · Windows", "owner/repository") wrap instead of ending in "…"
            JComponent value = rows[i][1] instanceof JComponent given ? given
                : monoValue ? label(text, mono(12f), TEXT) : wrapped(text.replace("/", "/\u200B"), 104, TEXT, 13f);
            if (!(rows[i][1] instanceof JComponent)) value.setToolTipText(text);
            g.fill = GridBagConstraints.HORIZONTAL;
            p.add(value, g);
            g.fill = GridBagConstraints.NONE;
        }
        return p;
    }
}
