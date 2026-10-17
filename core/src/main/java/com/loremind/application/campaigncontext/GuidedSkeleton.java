package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.structure.LinkType;
import com.loremind.domain.campaigncontext.structure.SceneType;

import java.util.List;

/**
 * Squelette d'aventure produit par une méthode guidée (Mentor d'écriture, phase M3 —
 * voir docs/conception-mentor-ecriture.md) : un chapitre et ses scènes, rédigés par le MJ
 * question par question. Générique : chaque méthode (Donjon en 5 salles, puis M5) construit
 * ce même squelette côté front ; le Core se contente de le persister d'un bloc.
 *
 * <p>Placement : soit {@code arcId} (arc LINÉAIRE existant de la campagne, le chapitre
 * est ajouté à la suite), soit {@code newArcName} (nouvel arc linéaire créé à la suite).
 * Exactement l'un des deux.</p>
 *
 * @param arcId            arc existant où ajouter le chapitre, ou {@code null}
 * @param newArcName       nom du nouvel arc à créer, ou {@code null}
 * @param chapter          le chapitre (nom obligatoire)
 * @param scenes           les scènes, dans l'ordre (au moins une avec un nom)
 * @param linkSequentially relier chaque scène à la suivante par une sortie
 * @param linkLabel        libellé de ces sorties (rédigé dans la langue du MJ par le front)
 * @param links            liens explicites entre scènes (indices dans {@code scenes}), ou {@code null} :
 *                         une enquête en nœuds relie ses pistes en réseau, pas en chaîne
 * @param clock            horloge à créer dans chaque partie de la campagne (plan du méchant), ou {@code null}
 */
public record GuidedSkeleton(
        String arcId,
        String newArcName,
        ChapterSpec chapter,
        List<SceneSpec> scenes,
        boolean linkSequentially,
        String linkLabel,
        List<LinkSpec> links,
        ClockSpec clock
) {

    /** Squelette sans liens explicites ni horloge (Donjon en 5 salles). */
    public GuidedSkeleton(String arcId, String newArcName, ChapterSpec chapter, List<SceneSpec> scenes,
                          boolean linkSequentially, String linkLabel) {
        this(arcId, newArcName, chapter, scenes, linkSequentially, linkLabel, null, null);
    }

    /** @param gmNotes notes réservées au MJ (vérité cachée d'une enquête, plan complet du méchant) */
    public record ChapterSpec(String name, String description, String playerObjectives, String narrativeStakes,
                              String gmNotes) {
        public ChapterSpec(String name, String description, String playerObjectives, String narrativeStakes) {
            this(name, description, playerObjectives, narrativeStakes, null);
        }
    }

    /**
     * Lien d'une scène vers une autre, par position dans {@code scenes}.
     *
     * @param from  index de la scène de départ
     * @param to    index de la scène d'arrivée
     * @param label libellé du lien (langue du MJ)
     * @param kind  nature du lien (sortie, piste, indice) ; {@code null} = sortie
     */
    public record LinkSpec(int from, int to, String label, LinkType kind) {
    }

    /**
     * Horloge de progression à créer dans chaque partie (Playthrough) de la campagne.
     *
     * @param name        nom de l'horloge
     * @param description ce qui arrive quand elle est pleine
     * @param segments    nombre de segments (une étape du plan = un segment)
     */
    public record ClockSpec(String name, String description, int segments) {
    }

    public record SceneSpec(
            String name, String description, SceneType type, String location,
            String choicesConsequences, String gmSecretNotes, String enemies) {
    }

    /**
     * Ce qui a été créé, pour que le front ouvre directement le chapitre.
     *
     * @param clocks nombre d'horloges créées (une par partie de la campagne)
     */
    public record Result(String arcId, String chapterId, List<String> sceneIds, int clocks) {
        public Result(String arcId, String chapterId, List<String> sceneIds) {
            this(arcId, chapterId, sceneIds, 0);
        }
    }
}
