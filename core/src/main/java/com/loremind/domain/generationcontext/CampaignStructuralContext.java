package com.loremind.domain.generationcontext;

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
 * <p>
 * Record Java : pur domaine, aucune dépendance technique.
 *
 * @param characters Personnages joueurs (PJ) de la campagne. Vide si aucun.
 * @param npcs       Personnages non-joueurs (PNJ) de la campagne. Vide si aucun.
 */
public record CampaignStructuralContext(
        String campaignName,
        String campaignDescription,
        List<ArcSummary> arcs,
        List<CharacterSummary> characters,
        List<NpcSummary> npcs) {

    /**
     * Résumé d'un PJ : nom + snippet court du markdown.
     * Pas le markdown complet pour maîtriser le coût token (chaque campagne
     * peut avoir 4-6 PJ × potentiellement 1-2k tokens/fiche = trop lourd).
     * La fiche complète n'est injectée que si le PJ est l'entité focus
     * (via NarrativeEntityContext, entity_type="character").
     */
    public record CharacterSummary(String name, String snippet) {
    }

    /**
     * Résumé d'un PNJ : symétrique à {@link CharacterSummary}.
     * Snippet court extrait du markdown — la fiche complète est réservée
     * à un usage focus (à venir, entity_type="npc").
     */
    public record NpcSummary(String name, String snippet) {
    }

    /**
     * Résumé d'un arc : nom + description courte + ses chapitres.
     *
     * @param illustrationCount Nombre d'illustrations attachees a cet arc (pour hint dans le prompt IA).
     */
    public record ArcSummary(
            String name,
            String description,
            int illustrationCount,
            List<ChapterSummary> chapters) {
    }

    /** Résumé d'un chapitre : nom + description courte + ses scènes. */
    public record ChapterSummary(
            String name,
            String description,
            int illustrationCount,
            List<SceneSummary> scenes) {
    }

    /** Résumé d'une scène : nom + description courte + branches + pièces explorables. */
    public record SceneSummary(
            String name,
            String description,
            int illustrationCount,
            List<BranchHint> branches,
            List<RoomSummary> rooms) {
    }

    /**
     * Indice d'une branche narrative vers une autre scène du même chapitre.
     *
     * @param label           Libellé du choix joueur (ex: "Si les joueurs attaquent le garde").
     * @param targetSceneName Nom de la scène cible (résolu depuis targetSceneId côté builder).
     * @param condition       Condition MJ privée (optionnel).
     */
    public record BranchHint(String label, String targetSceneName, String condition) {
    }

    /**
     * Pièce d'un lieu explorable (donjon, crypte). Projection volontairement plate
     * pour le prompt IA : pas de notes MJ (jamais leakées dans le contexte campagne),
     * la narration et les ennemis suffisent à camper la pièce.
     *
     * @param name        Nom de la pièce.
     * @param floor       Étage (nullable).
     * @param description Narration courte.
     * @param enemies     Ennemis (texte libre).
     * @param branches    Sorties vers d'autres pièces (noms résolus).
     */
    public record RoomSummary(
            String name,
            Integer floor,
            String description,
            String enemies,
            List<RoomBranchHint> branches) {
    }

    /** Indice d'une sortie entre pièces ; {@code targetRoomName} déjà résolu. */
    public record RoomBranchHint(String label, String targetRoomName, String condition) {
    }
}
