package com.loremind.domain.lorecontext;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Entité de domaine représentant une page de contenu rattachée à un LoreNode.
 * <p>
 * Une page :
 *  - appartient à un Lore (loreId — dénormalisé pour queries rapides)
 *  - est rangée sous un LoreNode (nodeId)
 *  - est générée depuis un Template (templateId) dont elle suit les `fields`
 *  - stocke les valeurs de ses champs dynamiques dans `values` (fieldName → value)
 *  - porte des métadonnées éditoriales : notes privées MJ, tags, liens inter-pages.
 * <p>
 * Entité pure du domaine : aucune dépendance technique.
 */
@Data
@Builder
public class Page {

    private String id;
    private String loreId;
    private String nodeId;
    private String templateId;
    private String title;

    /** Valeurs des champs dynamiques TEXT définis par le Template. */
    private Map<String, String> values;

    /**
     * Valeurs des champs dynamiques IMAGE : pour chaque nom de champ IMAGE du
     * template, la liste ordonnee des IDs d'images uploadees (Shared Kernel images).
     * Structure separee de `values` pour garder des types homogenes par map.
     */
    private Map<String, List<String>> imageValues;

    /** Notes privées du MJ (non exportées vers FoundryVTT). */
    private String notes;

    /** Étiquettes libres pour regroupement/recherche. */
    private List<String> tags;

    /** IDs d'autres Pages liées (mêmes Lore ou cross-lore). */
    private List<String> relatedPageIds;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean hasTemplate() {
        return templateId != null && !templateId.isBlank();
    }
}
