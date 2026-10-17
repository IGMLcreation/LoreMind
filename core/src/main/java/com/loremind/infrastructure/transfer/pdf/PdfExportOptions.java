package com.loremind.infrastructure.transfer.pdf;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Options de l'export PDF choisies par l'utilisateur.
 *
 * @param gmContent     vrai = version MJ complète ; faux = version joueurs (aucun secret :
 *                      notes MJ, conséquences, sorties, combats, contexte caché…)
 * @param format        format de page
 * @param printFriendly vrai = sans aplats de couleur (économie d'encre)
 * @param sections      parties incluses
 * @param loreScope     pages de lore incluses (si la partie Lore est demandée)
 */
public record PdfExportOptions(boolean gmContent, PageFormat format, boolean printFriendly,
                               Set<Section> sections, LoreScope loreScope) {

    /**
     * Parties du livret qu'on peut inclure ou non. APPENDIX = annexes MJ (revelations et
     * indices, index PNJ et adversaires ; jamais en version joueurs) ; MAPS = cartes en
     * pleine page paysage (les scenes y renvoient au lieu de les afficher en petit).
     */
    public enum Section { NARRATIVE, QUESTS, NPCS, ENEMIES, TABLES, LORE, APPENDIX, MAPS }

    /** Pages de lore exportées : toutes, ou seulement celles liées au contenu de la campagne. */
    public enum LoreScope { ALL, LINKED }

    /**
     * Format de page : dimensions (cm), marges (haut, côtés, bas) et taille du texte courant.
     * A5 : marges et corps réduits pour garder une ligne lisible sur une page étroite.
     */
    public enum PageFormat {
        A4("A4", 21.0, 29.7, 2.2, 1.7, 2.0, 10.0, 12.5),
        A5("A5", 14.8, 21.0, 1.7, 1.3, 1.6, 9.0, 7.0),
        LETTER("letter", 21.59, 27.94, 2.2, 1.8, 2.0, 10.0, 12.0);

        final String cssSize;
        final double widthCm;
        final double heightCm;
        final double marginTopCm;
        final double marginSideCm;
        final double marginBottomCm;
        final double bodyPt;
        /** Hauteur max de l'illustration de couverture : la couverture tient sur une page. */
        final double coverArtMaxCm;

        PageFormat(String cssSize, double widthCm, double heightCm, double marginTopCm, double marginSideCm,
                   double marginBottomCm, double bodyPt, double coverArtMaxCm) {
            this.cssSize = cssSize;
            this.widthCm = widthCm;
            this.heightCm = heightCm;
            this.marginTopCm = marginTopCm;
            this.marginSideCm = marginSideCm;
            this.marginBottomCm = marginBottomCm;
            this.bodyPt = bodyPt;
            this.coverArtMaxCm = coverArtMaxCm;
        }

        /** Largeur d'une page tournée en paysage (= hauteur en portrait). */
        double landscapeWidthCm() {
            return heightCm;
        }

        /** Largeur utile (page moins les deux marges latérales). */
        double contentWidthCm() {
            return widthCm - 2 * marginSideCm;
        }
    }

    public PdfExportOptions {
        format = format != null ? format : PageFormat.A4;
        sections = sections == null || sections.isEmpty()
                ? EnumSet.allOf(Section.class) : EnumSet.copyOf(sections);
        loreScope = loreScope != null ? loreScope : LoreScope.ALL;
    }

    /** Livret MJ complet en A4 : le comportement historique de l'export. */
    public static PdfExportOptions defaults() {
        return new PdfExportOptions(true, PageFormat.A4, false, EnumSet.allOf(Section.class), LoreScope.ALL);
    }

    public boolean includes(Section section) {
        return sections.contains(section);
    }

    /**
     * Construit les options depuis les paramètres de requête (valeurs inconnues ignorées,
     * absentes = défaut). {@code sections} : liste séparée par des virgules.
     */
    public static PdfExportOptions parse(String edition, String format, Boolean print,
                                         String sections, String lore) {
        boolean gm = !"player".equalsIgnoreCase(trim(edition));
        PageFormat pf = enumOrNull(PageFormat.class, format);
        LoreScope scope = enumOrNull(LoreScope.class, lore);
        Set<Section> set = EnumSet.noneOf(Section.class);
        if (sections != null) {
            for (String s : sections.split(",")) {
                Section sec = enumOrNull(Section.class, s);
                if (sec != null) set.add(sec);
            }
        }
        return new PdfExportOptions(gm, pf, Boolean.TRUE.equals(print), set, scope);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static <E extends Enum<E>> E enumOrNull(Class<E> type, String raw) {
        String v = trim(raw).toUpperCase(Locale.ROOT);
        if (v.isEmpty()) return null;
        try {
            return Enum.valueOf(type, v);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
