package com.loremind.domain.generationcontext;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Contexte structurel d'une Session de jeu — injecté dans le system prompt
 * de l'IA pour qu'elle ait conscience de la partie en cours et de son journal.
 *
 * <p>Pendant qu'une session se joue, l'IA reçoit en plus du Lore/Campagne/GameSystem :
 * le nom de la session, son statut (en cours / terminée) et un résumé chronologique
 * des entrées du journal (notes, évènements, jets, actions joueurs).</p>
 *
 * <p>Value Object du Generation Context — record Java immutable.</p>
 *
 * @param sessionName       Nom de la session telle qu'affichée au MJ.
 * @param active            True si la session est en cours, false si terminée.
 * @param startedAt         Horodatage de démarrage.
 * @param entries           Entrées du journal triées chronologiquement (anciennes → récentes).
 *                          Limité côté builder pour éviter de saturer le contexte LLM.
 */
public record SessionContext(
        String sessionName,
        boolean active,
        LocalDateTime startedAt,
        List<JournalEntrySummary> entries) {

    /** Résumé d'une entrée de journal — type + contenu + horodatage. */
    public record JournalEntrySummary(
            String type,
            String content,
            LocalDateTime occurredAt) {}
}
