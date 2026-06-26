package com.loremind.infrastructure.transfer;

/**
 * Périmètre d'un export de contenu.
 * <p>
 * - {@code campaignId == null} → <b>Sauvegarde complète</b> : tout le contenu de la base.<br>
 * - {@code campaignId != null} → <b>export ciblé</b> : la campagne et sa clôture (arcs →
 *   chapitres → scènes, PNJ, ennemis, catalogues, tables, son système de jeu), plus
 *   éventuellement son univers (lore) et son espace de jeu (parties/sessions/feuilles de perso),
 *   selon les options.
 * <p>
 * Les options ne s'appliquent qu'à l'export ciblé ; une sauvegarde complète prend tout.
 *
 * @param campaignId    campagne à exporter, ou {@code null} pour tout exporter
 * @param includeLore   inclure l'univers lié (lore, dossiers, templates, pages)
 * @param includePlay   inclure l'espace de jeu (parties, sessions, journal, flags,
 *                      progression de quêtes, feuilles de personnages)
 * @param includeImages inclure les binaires d'images/fichiers référencés (sinon : métadonnées seules)
 */
public record ExportRequest(Long campaignId, boolean includeLore, boolean includePlay, boolean includeImages) {

    /** Sauvegarde complète : toute la base, jeu et images compris. */
    public static ExportRequest full() {
        return new ExportRequest(null, true, true, true);
    }

    /** Vrai si l'export couvre toute la base (pas de campagne ciblée). */
    public boolean isFull() {
        return campaignId == null;
    }
}
