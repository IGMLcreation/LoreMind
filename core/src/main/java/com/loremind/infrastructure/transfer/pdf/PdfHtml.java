package com.loremind.infrastructure.transfer.pdf;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Primitives de rendu HTML/XHTML du livret PDF, sans etat : echappement XML sur mesure,
 * fragments repetes, formatage, et le rendu « libelle + texte riche » (paragraphes et
 * listes a puces) partage par toutes les sections. Importe en statique par PdfExportService.
 */
final class PdfHtml {

    private PdfHtml() {}

    // ----- Fragments HTML repetes (S1192) -----
    static final String DIV_CLOSE = "</div>";
    static final String SPAN_CLOSE = "</span>";
    static final String TBODY_TABLE_CLOSE = "</tbody></table>";
    static final String TD_TR_CLOSE = "</td></tr>";
    static final String H3_FOLDER_OPEN = "<h3 class=\"folder\" id=\"";

    static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Balise d'ouverture de ligne avec alternance de fond (zebra striping) : appeler en {@code trTag(row++)}. */
    static String trTag(int row) {
        return row % 2 == 1 ? "<tr class=\"alt\">" : "<tr>";
    }

    /** Un signet PDF (childrenHtml : signets enfants deja rendus, ou chaine vide). */
    static String bookmark(String name, String anchor, String childrenHtml) {
        return "<bookmark name=\"" + esc(name == null ? "" : name) + "\" href=\"#" + anchor + "\">"
                + childrenHtml + "</bookmark>";
    }

    static String cm(double v) {
        return String.format(Locale.ROOT, "%.1fcm", v);
    }

    /** Un lieu/moment assez court pour la ligne de contexte de scene (sinon champ normal). */
    static boolean isMetaShort(String s) {
        return s == null || (!s.contains("\n") && !s.contains("\r") && s.trim().length() <= 90);
    }

    /**
     * Echappe le texte pour XHTML et retire les caracteres interdits en XML 1.0.
     * Les glyphes exotiques (fleches, ≈, cyrillique...) sont couverts par les polices
     * DejaVu embarquees ; seuls les emojis (hors plan de base, absents de DejaVu)
     * sont retires pour ne pas sortir en "#".
     */
    static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 && c != '\n' && c != '\t') continue; // \r normalise en amont
            switch (c) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                case '\u202F', '\u2007' -> b.append('\u00A0'); // espaces fines (U+202F, U+2007) -> insecable
                default -> {
                    if (!Character.isSurrogate(c)) b.append(c); // emojis : sans glyphe, on retire
                }
            }
        }
        return b.toString();
    }

    /** Comme esc, mais les sauts de ligne deviennent des &lt;br/&gt;. */
    static String multiline(String s) {
        if (s == null) return "";
        return esc(s.replace("\r\n", "\n").replace('\r', '\n')).replace("\n", "<br/>");
    }

    // ----- Bloc "libelle + texte" -----

    /** Un paragraphe (lignes jointes par &lt;br/&gt;) ou une liste a puces. */
    record TextBlock(boolean list, List<String> lines) {}

    /**
     * ENCADRE special (codes visuels des livres de JdR) : "readaloud" = texte a lire aux
     * joueurs (parchemin, filets or), "secret" = reserve au MJ (violet tirete), "combat" =
     * rencontre (accent rouge). Libelle sur sa propre ligne, contenu en blocs.
     */
    static void box(StringBuilder b, String cssClass, String label, String value) {
        if (!notBlank(value)) return;
        b.append("<div class=\"box ").append(cssClass).append("\">");
        b.append("<div class=\"box-label\">").append(esc(label)).append(DIV_CLOSE);
        List<TextBlock> blocks = parseBlocks(value);
        for (int i = 0; i < blocks.size(); i++) {
            TextBlock t = blocks.get(i);
            if (t.list()) {
                b.append("<ul>");
                for (String line : t.lines()) b.append("<li>").append(esc(line)).append("</li>");
                b.append("</ul>");
            } else if (i == 0) {
                b.append(paragraphHtml(t));
            } else {
                b.append("<div class=\"para\">").append(paragraphHtml(t)).append(DIV_CLOSE);
            }
        }
        b.append(DIV_CLOSE);
    }

    /**
     * Bloc "libelle en tete de ligne + valeur" si la valeur est non vide. Le libelle est
     * rendu EN LIGNE devant le premier paragraphe (style stat-block : compact et balayable),
     * les paragraphes suivants et les listes a puces (lignes "- ...") en dessous.
     */
    static void block(StringBuilder b, String label, String value) {
        block(b, label, value, null);
    }

    /** Variante avec classe CSS additionnelle sur le champ (ex : "ambiance" -> italique). */
    static void block(StringBuilder b, String label, String value, String extraClass) {
        if (!notBlank(value)) return;
        List<TextBlock> blocks = parseBlocks(value);
        b.append("<div class=\"field").append(extraClass != null ? " " + extraClass : "")
                .append("\"><span class=\"field-label\">").append(esc(label)).append(SPAN_CLOSE);
        int i = 0;
        if (!blocks.isEmpty() && !blocks.get(0).list()) {
            b.append(paragraphHtml(blocks.get(0)));
            i = 1;
        }
        for (; i < blocks.size(); i++) {
            TextBlock t = blocks.get(i);
            if (t.list()) {
                b.append("<ul>");
                for (String line : t.lines()) b.append("<li>").append(esc(line)).append("</li>");
                b.append("</ul>");
            } else {
                b.append("<div class=\"para\">").append(paragraphHtml(t)).append(DIV_CLOSE);
            }
        }
        b.append(DIV_CLOSE);
    }

    private static String paragraphHtml(TextBlock t) {
        StringBuilder p = new StringBuilder();
        for (int i = 0; i < t.lines().size(); i++) {
            if (i > 0) p.append("<br/>");
            p.append(esc(t.lines().get(i)));
        }
        return p.toString();
    }

    /**
     * Decoupe un texte brut en blocs : les lignes vides separent les paragraphes, les
     * lignes commencant par "- ", "• " ou "* " deviennent de vraies listes a puces.
     */
    private static List<TextBlock> parseBlocks(String value) {
        String norm = value.replace("\r\n", "\n").replace('\r', '\n');
        List<TextBlock> blocks = new ArrayList<>();
        List<String> cur = new ArrayList<>();
        boolean curList = false;
        for (String line : norm.split("\n", -1)) {
            String t = line.trim();
            if (t.isEmpty()) {
                if (!cur.isEmpty()) blocks.add(new TextBlock(curList, cur));
                cur = new ArrayList<>();
                continue;
            }
            boolean bullet = t.startsWith("- ") || t.startsWith("• ") || t.startsWith("* ");
            if (!cur.isEmpty() && bullet != curList) {
                blocks.add(new TextBlock(curList, cur));
                cur = new ArrayList<>();
            }
            curList = bullet;
            cur.add(bullet ? t.substring(2).trim() : t);
        }
        if (!cur.isEmpty()) blocks.add(new TextBlock(curList, cur));
        return blocks;
    }
}
