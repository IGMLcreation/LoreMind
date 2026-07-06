package com.loremind.domain.campaigncontext.generation;

import java.util.List;

/**
 * Proposition d'arborescence narrative extraite d'un PDF de campagne.
 * <p>
 * PROPOSITION non persistée : l'UI laisse l'utilisateur réviser/éditer l'arbre
 * avant la création effective des arcs/chapitres/scènes. Records purs (domaine).
 */
public record CampaignImportProposal(List<ArcProposal> arcs, List<NpcProposal> npcs) {

    /**
     * {@code type} = "LINEAR" ou "HUB" (mappé sur {@link ArcType} à l'apply).
     * <p>
     * {@code existingId} (nullable, porté aussi par Chapter/SceneProposal) : si présent,
     * le nœud existe DÉJÀ dans la campagne (rempli côté UI lors de la revue pré-chargée)
     * → l'apply ne le recrée pas, il l'utilise comme parent des nouveaux enfants.
     * Null = à créer.
     */
    public record ArcProposal(
            String name, String description, String type,
            List<ChapterProposal> chapters, String existingId) {
    }

    public record ChapterProposal(
            String name, String description, List<SceneProposal> scenes, String existingId) {
    }

    /**
     * {@code rooms} non vide => lieu explorable (donjon). {@code playerNarration}
     * = encadré « à lire aux joueurs », {@code gmNotes} = secrets/développement MJ.
     */
    public record SceneProposal(
            String name, String description, String playerNarration, String gmNotes,
            List<RoomProposal> rooms, String existingId) {
    }

    public record RoomProposal(String name, String description, String enemies, String loot) {
    }

    /**
     * PNJ/creature notable detecte dans le PDF (PNJ nommes, boss). Propose a la
     * revue (coche par defaut) ; cree comme Npc de la campagne a l''apply avec
     * sa description dans values["Description"] (meme convention que les cartes
     * d''action des ateliers).
     */
    public record NpcProposal(String name, String description) {
    }
}

