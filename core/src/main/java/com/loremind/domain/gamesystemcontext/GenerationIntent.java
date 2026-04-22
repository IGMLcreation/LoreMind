package com.loremind.domain.gamesystemcontext;

import java.util.Set;

/**
 * Intent de génération utilisé pour sélectionner les sections d'un GameSystem
 * à injecter dans le prompt IA.
 * <p>
 * Chaque intent porte une liste d'alias (case-insensitive, comparaison par
 * {@code contains}) utilisée pour matcher les titres H2 du markdown de règles.
 * <p>
 * MVP : mapping codé en dur. Évoluera vers un mapping configurable par
 * l'utilisateur dans l'éditeur de GameSystem (futur marketplace).
 */
public enum GenerationIntent {

    /** Scène (combat / rencontre) : règles de résolution + format de stat block. */
    SCENE(Set.of("combat", "monstre", "monster")),

    /** Chapitre (segment narratif) : règles de combat + archétypes pour PNJ. */
    CHAPTER(Set.of("combat", "classe", "class")),

    /** Arc (structure narrative longue) : pas de règles spécifiques — toutes. */
    ARC(Set.of()),

    /** Fallback : toutes les sections (intent inconnu). */
    GENERIC(Set.of());

    private final Set<String> sectionAliases;

    GenerationIntent(Set<String> sectionAliases) {
        this.sectionAliases = sectionAliases;
    }

    public Set<String> getSectionAliases() {
        return sectionAliases;
    }

    /** True si l'intent veut toutes les sections (pas de filtre). */
    public boolean matchesAllSections() {
        return sectionAliases.isEmpty();
    }

    /**
     * Mappe un entityType de NarrativeEntityContext ("arc"/"chapter"/"scene")
     * vers l'intent correspondant. Tout le reste (null, inconnu) tombe sur GENERIC.
     */
    public static GenerationIntent fromNarrativeEntityType(String entityType) {
        if (entityType == null) return GENERIC;
        return switch (entityType.toLowerCase()) {
            case "scene" -> SCENE;
            case "chapter" -> CHAPTER;
            case "arc" -> ARC;
            default -> GENERIC;
        };
    }
}
