import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The guide (Instrukcja): how to use this version, built into the launcher so it works offline and fits the
 * version in hand. Polish or English by the launcher's language; other languages read the English one.
 */
final class Guide {
    private Guide() { }

    /** The guide's text (a small Markdown: ## headings, paragraphs, - and 1. lists, | tables |, **bold**, `code`, [links](url)). */
    static String markdown() {
        String lang = "pl".equals(I18n.lang()) ? "pl" : "en";
        try (InputStream in = Guide.class.getResourceAsStream("/guide/guide-" + lang + ".md")) {
            return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            return "";
        }
    }

    /** The guide's page for the main window: the text, scrolling, with links that open in the browser. */
    static JComponent page() {
        JEditorPane text = new JEditorPane("text/html", "<html><body style='font-family:" + Theme.text(14f).getFamily() + "; font-size:14pt; color:"
            + Theme.hex(Theme.SOFT) + "; margin:0 4px 24px 0'>" + html(markdown()) + "</body></html>");
        text.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        text.setFont(Theme.text(14f));
        text.setEditable(false);
        text.setOpaque(false);
        text.addHyperlinkListener(e -> { if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && e.getURL() != null) Platform.open(e.getURL().toString()); });
        text.setCaretPosition(0);
        JScrollPane scroll = Theme.scrolling(text); // follows the window's width, so the lines wrap
        SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(0));
        return Theme.page(PatchbayLauncher.t("Instrukcja", "Guide"), Theme.label("Patchbay Launcher " + PatchbayLauncher.VERSION, Theme.mono(12f), Theme.MUTED),
            null, scroll, null, null);
    }

    /** The guide's Markdown as HTML for a JEditorPane. */
    static String html(String markdown) {
        StringBuilder out = new StringBuilder();
        String[] lines = markdown.replace("\r", "").split("\n");
        String list = null; // "ul" or "ol" while one is open
        List<String[]> table = new ArrayList<>();
        for (int i = 0; i <= lines.length; i++) {
            String line = i < lines.length ? lines[i].strip() : "";
            boolean row = line.startsWith("|");
            if (!row && !table.isEmpty()) { out.append(tableHtml(table)); table.clear(); }
            boolean bullet = line.startsWith("- "), numbered = line.matches("\\d+\\.\\s.*");
            if (list != null && !(list.equals("ul") ? bullet : numbered)) { out.append("</").append(list).append('>'); list = null; }
            if (line.isEmpty()) continue;
            if (row) {
                if (!line.matches("\\|[\\s:|-]+\\|?")) table.add(cells(line));
            } else if (line.startsWith("## ")) {
                out.append("<h2 style='font-size:18pt; color:").append(Theme.hex(Theme.TEXT)).append("; margin:22px 0 6px 0'>")
                    .append(inline(line.substring(3))).append("</h2>");
            } else if (bullet || numbered) {
                String kind = bullet ? "ul" : "ol";
                if (list == null) { out.append('<').append(kind).append(" style='margin-left:22px; margin-top:4px'>"); list = kind; }
                out.append("<li style='margin-bottom:5px'>").append(inline(bullet ? line.substring(2) : line.replaceFirst("^\\d+\\.\\s+", ""))).append("</li>");
            } else {
                out.append("<p style='margin:0 0 10px 0'>").append(inline(line)).append("</p>");
            }
        }
        return out.toString();
    }

    private static String[] cells(String row) {
        String inner = row.replaceAll("^\\|", "").replaceAll("\\|$", "");
        String[] parts = inner.split("\\|");
        for (int k = 0; k < parts.length; k++) parts[k] = parts[k].strip();
        return parts;
    }

    private static String tableHtml(List<String[]> rows) {
        String line = Theme.hex(Theme.LINE);
        StringBuilder t = new StringBuilder("<table cellspacing='0' cellpadding='6' style='margin:6px 0 14px 0; border-collapse:collapse'>");
        for (int r = 0; r < rows.size(); r++) {
            t.append("<tr>");
            for (String c : rows.get(r)) {
                String cell = inline(c);
                t.append(r == 0 ? "<th align='left' style='border-bottom:1px solid " + line + "; color:" + Theme.hex(Theme.TEXT) + "'>" + cell + "</th>"
                    : "<td valign='top' style='border-bottom:1px solid " + line + "'>" + cell + "</td>");
            }
            t.append("</tr>");
        }
        return t.append("</table>").toString();
    }

    private static String inline(String text) {
        String accent = Theme.hex(Theme.ACCENT), strong = Theme.hex(Theme.TEXT);
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replaceAll("\\*\\*(.+?)\\*\\*", "<b><font color='" + strong + "'>$1</font></b>")
            .replaceAll("`(.+?)`", "<code>$1</code>")
            .replaceAll("\\[([^\\]]+)\\]\\((https://[^)]+)\\)", "<a href='$2' style='color:" + accent + "'>$1</a>");
    }
}
