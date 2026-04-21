package com.loremind.domain.generationcontext;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

/**
 * Carte narrative enrichie d'une Campagne pour nourrir l'IA.
 * <p>
 * Ceci est un Value Object du Generation Context (Bounded Context IA).
 * Jumeau de LoreStructuralContext côté Campaign : on décrit l'arbre
 * arcs → chapitres → scènes avec le NOM + une DESCRIPTION courte à chaque
 * niveau. Les champs longs (notes MJ, narration joueur, combat) restent
 * exclus : l'IA les obtient uniquement via {@link NarrativeEntityContext}
 * pour l'entité focus.
 * <p>
 * Objectif : permettre à l'IA de répondre "c'est quoi la scène X ?" même
 * quand X n'est pas l'entité en cours d'édition, sans exploser le prompt.
 * Budget typique : ~30 tokens/scène × 100 scènes = 3k tokens (confortable).
 * <p>
 * La liste `arcs` préserve l'ordre narratif (tri sur `order` ascendant
 * fait par le use case côté application layer).
 */
@Value
@Builder
public class CampaignStructuralContext {

    String campaignName;
    String campaignDescription;
    @Singular List<ArcSummary> arcs;

    /** Résumé d'un arc : nom + description courte + ses chapitres. */
    @Value
    @Builder
    public static class ArcSummary {
        String name;
        String description;
        /** Nombre d'illustrations attachees a cet arc (pour hint dans le prompt IA). */
        int illustrationCount;
        @Singular List<ChapterSummary> chapters;
    }

    /** Résumé d'un chapitre : nom + description courte + ses scènes. */
    @Value
    @Builder
    public static class ChapterSummary {
        String name;
        String description;
        int illustrationCount;
        @Singular List<SceneSummary> scenes;
    }

    /** Résumé d'une scène : nom + description courte + branches narratives. */
    @Value
    @Builder
    public static class SceneSummary {
        String name;
        String description;
        int illustrationCount;
        @Singular List<BranchHint> branches;
    }

    /** Indice d'une branche narrative vers une autre scène du même chapitre. */
    @Value
    @Builder
    public static class BranchHint {
        /** Libellé du choix joueur (ex: "Si les joueurs attaquent le garde"). */
        String label;
        /** Nom de la scène cible (résolu depuis targetSceneId côté builder). */
        String targetSceneName;
        /** Condition MJ privée (optionnel). */
        String condition;
    }
}
