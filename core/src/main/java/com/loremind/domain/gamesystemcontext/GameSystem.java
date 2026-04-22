package com.loremind.domain.gamesystemcontext;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Entité de domaine représentant un GameSystem (système de JDR).
 * <p>
 * Porte les règles d'un système (D&D, Nimble, Pathfinder, homebrew...) sous forme
 * d'un markdown monolithique structuré par titres H2. Les sections sont extraites
 * à la volée lors de l'injection dans les prompts IA (cf. GameSystemContextSelector).
 * <p>
 * {@code author} et {@code isPublic} sont des champs pensés pour un futur marketplace
 * de rulesets partagés — non exploités au MVP mais persistés dès maintenant pour
 * éviter une migration ultérieure.
 */
@Data
@Builder
public class GameSystem {

    private String id;
    private String name;
    private String description;

    /** Markdown monolithique. Sections découpées par titres H2 (## Combat, ## Classes, etc.). */
    private String rulesMarkdown;

    /** Auteur déclaré — futur marketplace. Nullable. */
    private String author;

    /** Flag de partage — futur marketplace. False par défaut. */
    private boolean isPublic;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
