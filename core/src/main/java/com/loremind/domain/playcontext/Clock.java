package com.loremind.domain.playcontext;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Horloge de progression (Clock, façon <i>Blades in the Dark</i>) — état dynamique de partie.
 *
 * <p>Appartient à un {@link Playthrough}. Compteur à {@code segments} segments dont
 * {@code filled} sont remplis ; pleine ({@code filled == segments}) → un effet narratif
 * se déclenche. Orthogonale à l'arbre Arc/Chapitre/Scène, comme {@code Session} /
 * {@code QuestProgression} : c'est de l'état de Partie, pas du scénario.</p>
 */
@Data
@Builder
public class Clock {

    private String id;

    /** Weak reference vers le Playthrough parent. */
    private String playthroughId;

    private String name;

    /** Ce qui se passe quand l'horloge est pleine (optionnel). */
    private String description;

    /** Nombre total de segments (≥ 1). */
    private int segments;

    /** Segments remplis (borné à [0, segments]). */
    private int filled;

    /** Ordre d'affichage parmi les horloges de la Partie. */
    private int order;

    /** Déclencheur d'avancement automatique (co-MJ). Défaut {@link ClockTrigger#NONE}. */
    @Builder.Default
    private ClockTrigger triggerType = ClockTrigger.NONE;

    /** Cible du déclencheur : nom du fait (FLAG_SET) ou id de quête (QUEST_COMPLETED) ; sinon null. */
    private String triggerRef;

    /** Front (menace) auquel l'horloge appartient, ou null (horloge libre). Weak reference. */
    private String frontId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isComplete() {
        return filled >= segments;
    }
}
