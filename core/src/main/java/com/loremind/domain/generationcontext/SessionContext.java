package com.loremind.domain.generationcontext;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Contexte structurel d'une Session de jeu — injecté dans le system prompt
 * de l'IA pour qu'elle ait conscience de la partie en cours et de son journal.
 *
 * <p>Pendant qu'une session se joue, l'IA reçoit en plus du Lore/Campagne/GameSystem :
 * le nom de la session, son statut, un résumé chronologique du journal,
 * et — depuis l'ajout du mode Hub — l'état des quêtes ouvertes de la campagne et
 * les flags actifs.</p>
 *
 * <p>Value Object du Generation Context — record Java immutable.</p>
 *
 * @param sessionName        Nom de la session courante telle qu'affichée au MJ.
 * @param active             True si la session est en cours, false si terminée.
 * @param startedAt          Horodatage de démarrage de la session courante.
 * @param entries            Entrées du journal de la session courante (cap côté builder).
 * @param previousEvents     Évènements marquants des sessions précédentes (continuité narrative).
 * @param availableQuests    Quêtes Hub actuellement débloquées et non démarrées.
 * @param inProgressQuests   Quêtes Hub en cours.
 * @param lockedQuestTitles  Titres des quêtes Hub verrouillées — uniquement le titre
 *                           pour signaler leur existence sans spoiler.
 * @param activeFlags        Noms des flags de campagne à true.
 */
public record SessionContext(
        String sessionName,
        boolean active,
        LocalDateTime startedAt,
        List<JournalEntrySummary> entries,
        List<JournalEntrySummary> previousEvents,
        List<QuestSummary> availableQuests,
        List<QuestSummary> inProgressQuests,
        List<String> lockedQuestTitles,
        List<String> activeFlags) {

    /**
     * Résumé d'une entrée de journal — type + contenu + horodatage + (optionnel) session source.
     * {@code sourceSessionName} renseigné uniquement pour les évènements issus de sessions
     * précédentes, pour aider l'IA à les ancrer temporellement.
     */
    public record JournalEntrySummary(
            String type,
            String content,
            LocalDateTime occurredAt,
            String sourceSessionName) {}

    /**
     * Résumé d'une quête (= Chapter dans un Arc HUB) telle qu'exposée à l'IA.
     * On omet volontairement les notes MJ : pas de fuite côté prompt.
     */
    public record QuestSummary(
            String name,
            String arcName,
            String description) {}
}
