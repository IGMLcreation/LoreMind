package com.loremind.domain.playcontext;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Entité de domaine représentant une Session de jeu (une soirée).
 *
 * <p>Une Session appartient à un {@link Playthrough} (une instance jouée d'une
 * campagne par une table). Un Playthrough a typiquement plusieurs sessions
 * dans le temps ; la progression et les flags persistent entre elles via le
 * Playthrough parent.</p>
 *
 * <p>{@code endedAt == null} signifie que la session est en cours.
 * Une seule session peut être en cours dans l'application à la fois.</p>
 */
@Data
@Builder
public class Session {

    private String id;
    private String name;

    /** Weak reference vers le Playthrough parent. */
    private String playthroughId;

    private LocalDateTime startedAt;

    /** Null = session en cours ; renseigné = session terminée. */
    private LocalDateTime endedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isActive() {
        return this.endedAt == null;
    }
}
