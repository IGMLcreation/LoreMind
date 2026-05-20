package com.loremind.domain.playcontext;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Entité de domaine représentant une Session de jeu en cours ou passée.
 *
 * <p>Une Session est une instance jouée d'une Campaign. La Campaign reste
 * un scénario générique réutilisable ; la Session capture une partie réelle
 * (date, journal, etc.) sans polluer le scénario d'origine.</p>
 *
 * <p>Fait partie du Play Context. Référence la Campaign par weak reference
 * (campaignId) pour respecter la séparation des Bounded Contexts.</p>
 *
 * <p>{@code endedAt == null} signifie que la session est en cours.
 * Une seule session peut être en cours dans l'application à la fois.</p>
 */
@Data
@Builder
public class Session {

    private String id;
    private String name;

    /** Weak reference vers Campaign — pas de dépendance directe inter-contexte. */
    private String campaignId;

    private LocalDateTime startedAt;

    /** Null = session en cours ; renseigné = session terminée. */
    private LocalDateTime endedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isActive() {
        return this.endedAt == null;
    }
}
