package com.loremind.domain.lorecontext;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Entité de domaine représentant un Univers de jeu (Lore).
 * Conteneur global pour organiser la connaissance d'un monde.
 * C'est une entité pure du domaine, sans dépendance technique (pas de JPA).
 */
@Data
@Builder
public class Lore {

    private String id;
    private String name;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int nodeCount;
    private int pageCount;
}
