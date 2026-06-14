package com.loremind.domain.playcontext;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Entrée du journal d'une Session.
 * Représente un évènement horodaté capturé pendant ou après une partie :
 * note libre du MJ, évènement marquant, jet de dés, action de joueur.
 *
 * <p>Fait partie du Play Context. Référence la Session par weak reference
 * (sessionId) — l'orchestration en cascade est gérée par le service applicatif.</p>
 */
@Data
@Builder
public class SessionEntry {

    private String id;

    /** Weak reference vers Session (intra-contexte mais reste découplée). */
    private String sessionId;

    private EntryType type;

    /** Contenu texte brut saisi par le MJ. */
    private String content;

    /**
     * Horodatage métier de l'évènement.
     * Distinct de {@code createdAt} : utile si le MJ rédige a posteriori
     * une note rétroactive sur quelque chose qui s'est passé plus tôt.
     */
    private LocalDateTime occurredAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
