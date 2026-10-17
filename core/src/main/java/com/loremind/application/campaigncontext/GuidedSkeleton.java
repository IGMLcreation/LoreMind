package com.loremind.application.campaigncontext;

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
 */
public record GuidedSkeleton(
        String arcId,
        String newArcName,
        ChapterSpec chapter,
        List<SceneSpec> scenes,
        boolean linkSequentially,
        String linkLabel
) {

    public record ChapterSpec(String name, String description, String playerObjectives, String narrativeStakes) {
    }

    public record SceneSpec(
            String name, String description, SceneType type, String location,
            String choicesConsequences, String gmSecretNotes, String enemies) {
    }

    /** Ce qui a été créé, pour que le front ouvre directement le chapitre. */
    public record Result(String arcId, String chapterId, List<String> sceneIds) {
    }
}
