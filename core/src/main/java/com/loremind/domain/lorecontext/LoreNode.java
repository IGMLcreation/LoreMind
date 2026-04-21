package com.loremind.domain.lorecontext;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Entité de domaine représentant un dossier dans l'arborescence d'un Lore.
 * Un dossier organise des Pages et peut contenir des sous-dossiers.
 * Structure hiérarchique via parentId (auto-référence).
 * Entité pure du domaine, sans dépendance technique.
 * <p>
 * Note : le nom interne reste "LoreNode" pour des raisons historiques et
 * techniques (compatibilité BDD, couplage avec le code existant). L'UI expose
 * le concept sous le terme "dossier".
 */
@Data
@Builder
public class LoreNode {

    private String id;
    private String name;
    /** Clé de l'icône lucide choisie par l'utilisateur (ex: "users", "map-pin"). */
    private String icon;
    private String parentId; // Auto-référence pour l'arborescence
    private String loreId;    // Référence vers le Lore parent
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
