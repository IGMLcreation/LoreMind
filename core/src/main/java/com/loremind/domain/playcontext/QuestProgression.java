package com.loremind.domain.playcontext;

import com.loremind.domain.campaigncontext.quest.ProgressionStatus;
import lombok.Builder;
import lombok.Data;

/**
 * État de progression d'une quête (Chapter) pour un Playthrough donné.
 *
 * <p>Remplace l'ancien champ {@code Chapter.progressionStatus} qui mélangeait
 * le scénario et l'état de jeu : ici, la progression est exclusivement
 * propre à une instance jouée (Playthrough).</p>
 *
 * <p>Référence la Quest par weak reference (questId) pour respecter les
 * Bounded Contexts. Le type {@link ProgressionStatus} reste défini dans
 * Campaign Context (c'est un Value Object générique, partageable).</p>
 *
 * <p>Sémantique : l'absence de ligne dans le repo équivaut à NOT_STARTED.
 * On ne persiste donc que les transitions explicites IN_PROGRESS / COMPLETED.</p>
 */
@Data
@Builder
public class QuestProgression {

    private String id;
    private String playthroughId;
    private String questId;
    private ProgressionStatus status;
}
