package com.loremind.domain.campaigncontext.structure;

/**
 * Value Object représentant UNE battlemap d'une Scene destinée à l'export Foundry :
 * paire { media + sidecar Universal VTT } plus un libellé libre. Une scène peut en
 * porter plusieurs (variantes « Jour » / « Nuit », étages, états avant/après…).
 * <p>
 * Record Java : immuable, sans dépendance technique (même pattern que
 * {@link SceneBranch}) — Jackson le (dé)sérialise nativement via le constructeur
 * canonique pour le stockage JSON en base.
 *
 * @param label       Libellé de la variante (ex : "Jour", "Nuit"). Jamais null (normalisé "").
 * @param mediaFileId ID du fichier media image/video ({@link com.loremind.domain.files.StoredFile}).
 *                    Null = carte sans fond (sidecar seul).
 * @param dataFileId  ID du fichier sidecar Universal VTT (.json/.dd2vtt). Null si absent.
 */
public record SceneBattlemap(String label, String mediaFileId, String dataFileId) {

    /** Normalise un libellé absent en chaîne vide (une seule représentation du « sans nom »). */
    public SceneBattlemap {
        if (label == null) label = "";
    }
}
