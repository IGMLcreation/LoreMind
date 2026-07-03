package com.loremind.application.generationcontext;

import com.loremind.domain.campaigncontext.PrerequisiteEvaluator;
import com.loremind.domain.campaigncontext.ProgressionStatus;
import com.loremind.domain.campaigncontext.Quest;
import com.loremind.domain.campaigncontext.QuestStatus;
import com.loremind.domain.campaigncontext.ports.QuestRepository;
import com.loremind.domain.generationcontext.SessionContext;
import com.loremind.domain.generationcontext.SessionContext.JournalEntrySummary;
import com.loremind.domain.generationcontext.SessionContext.QuestSummary;
import com.loremind.domain.playcontext.EntryType;
import com.loremind.domain.playcontext.Playthrough;
import com.loremind.domain.playcontext.QuestProgression;
import com.loremind.domain.playcontext.Session;
import com.loremind.domain.playcontext.SessionEntry;
import com.loremind.domain.playcontext.ports.PlaythroughFlagRepository;
import com.loremind.domain.playcontext.ports.PlaythroughRepository;
import com.loremind.domain.playcontext.ports.QuestProgressionRepository;
import com.loremind.domain.playcontext.ports.SessionEntryRepository;
import com.loremind.domain.playcontext.ports.SessionRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Construit le SessionContext injecté dans le prompt IA pendant une partie.
 *
 * <p>Depuis la refonte Playthrough : la session connaît son playthroughId ; la progression
 * et les flags viennent du Playthrough (pas plus de la Campagne ni du Chapter).</p>
 */
@Component
public class SessionStructuralContextBuilder {

    private static final int MAX_CURRENT_ENTRIES = 80;
    private static final int MAX_PREVIOUS_EVENTS = 60;

    private final SessionRepository sessionRepository;
    private final SessionEntryRepository entryRepository;
    private final PlaythroughRepository playthroughRepository;
    private final QuestRepository questRepository;
    private final PlaythroughFlagRepository playthroughFlagRepository;
    private final QuestProgressionRepository questProgressionRepository;
    private final PrerequisiteEvaluator prerequisiteEvaluator = new PrerequisiteEvaluator();

    public SessionStructuralContextBuilder(SessionRepository sessionRepository,
                                           SessionEntryRepository entryRepository,
                                           PlaythroughRepository playthroughRepository,
                                           QuestRepository questRepository,
                                           PlaythroughFlagRepository playthroughFlagRepository,
                                           QuestProgressionRepository questProgressionRepository) {
        this.sessionRepository = sessionRepository;
        this.entryRepository = entryRepository;
        this.playthroughRepository = playthroughRepository;
        this.questRepository = questRepository;
        this.playthroughFlagRepository = playthroughFlagRepository;
        this.questProgressionRepository = questProgressionRepository;
    }

    public Optional<SessionContext> buildOptional(String sessionId) {
        return sessionRepository.findById(sessionId).map(this::toContext);
    }

    public SessionContext build(String sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session introuvable : " + sessionId));
        return toContext(session);
    }

    private SessionContext toContext(Session session) {
        List<JournalEntrySummary> currentEntries = loadCurrentEntries(session);
        List<JournalEntrySummary> previousEvents = loadPreviousEvents(session);
        HubStatus hub = computeHubStatus(session.getPlaythroughId());

        return new SessionContext(
                session.getName(),
                session.isActive(),
                session.getStartedAt(),
                currentEntries,
                previousEvents,
                hub.available(),
                hub.inProgress(),
                hub.lockedTitles(),
                hub.activeFlags());
    }

    private List<JournalEntrySummary> loadCurrentEntries(Session session) {
        List<SessionEntry> allEntries = entryRepository.findBySessionId(session.getId());
        List<SessionEntry> kept = allEntries.size() <= MAX_CURRENT_ENTRIES
                ? allEntries
                : allEntries.subList(allEntries.size() - MAX_CURRENT_ENTRIES, allEntries.size());

        return kept.stream()
                .map(e -> toSummary(e, null))
                .collect(Collectors.toList());
    }

    /**
     * EVENTs des sessions précédentes du MÊME Playthrough (même table).
     * On ne mélange jamais les EVENTs de tables différentes.
     */
    private List<JournalEntrySummary> loadPreviousEvents(Session current) {
        if (current.getPlaythroughId() == null) return List.of();
        List<Session> siblingSessions = sessionRepository.findByPlaythroughId(current.getPlaythroughId());
        List<JournalEntrySummary> events = new ArrayList<>();

        for (Session past : siblingSessions) {
            if (past.getId().equals(current.getId())) continue;
            for (SessionEntry entry : entryRepository.findBySessionId(past.getId())) {
                if (entry.getType() == EntryType.EVENT) {
                    events.add(toSummary(entry, past.getName()));
                }
            }
        }

        events.sort(Comparator.comparing(
                JournalEntrySummary::occurredAt,
                Comparator.nullsLast(Comparator.naturalOrder())));

        if (events.size() > MAX_PREVIOUS_EVENTS) {
            return events.subList(events.size() - MAX_PREVIOUS_EVENTS, events.size());
        }
        return events;
    }

    private JournalEntrySummary toSummary(SessionEntry entry, String sourceSessionName) {
        return new JournalEntrySummary(
                entry.getType() != null ? entry.getType().name() : "NOTE",
                entry.getContent(),
                entry.getOccurredAt(),
                sourceSessionName);
    }

    /** Agrégat interne des données Hub à injecter dans le SessionContext. */
    private record HubStatus(
            List<QuestSummary> available,
            List<QuestSummary> inProgress,
            List<String> lockedTitles,
            List<String> activeFlags
    ) {}

    /**
     * Calcule l'état des quêtes Hub du Playthrough courant :
     *   - AVAILABLE / IN_PROGRESS → résumé complet
     *   - LOCKED                  → titre uniquement (anti-spoiler)
     *   - COMPLETED               → omis (déjà raconté par les EVENTs)
     */
    private HubStatus computeHubStatus(String playthroughId) {
        if (playthroughId == null) {
            return new HubStatus(List.of(), List.of(), List.of(), List.of());
        }
        Optional<Playthrough> maybePlaythrough = playthroughRepository.findById(playthroughId);
        if (maybePlaythrough.isEmpty()) {
            return new HubStatus(List.of(), List.of(), List.of(), List.of());
        }
        String campaignId = maybePlaythrough.get().getCampaignId();

        Map<String, Boolean> flags = playthroughFlagRepository.findByPlaythroughId(playthroughId);
        List<String> activeFlags = buildActiveFlags(flags);

        // Map questId -> ProgressionStatus pour ce Playthrough
        Map<String, ProgressionStatus> progressionByQuest = new HashMap<>();
        for (QuestProgression qp : questProgressionRepository.findByPlaythroughId(playthroughId)) {
            progressionByQuest.put(qp.getQuestId(), qp.getStatus());
        }

        // IDs des quêtes COMPLETED dans la campagne (pour les prérequis QuestCompleted)
        var completedIds = questProgressionRepository.findCompletedQuestIdsByPlaythroughId(playthroughId);

        int sessionCount = sessionRepository.findByPlaythroughId(playthroughId).size();
        PrerequisiteEvaluator.EvaluationContext ctx =
                new PrerequisiteEvaluator.EvaluationContext(completedIds, sessionCount, flags);

        List<QuestSummary> available = new ArrayList<>();
        List<QuestSummary> inProgress = new ArrayList<>();
        List<String> lockedTitles = new ArrayList<>();

        // Niveau 1 : les quêtes sont des entités orthogonales rattachées à la campagne
        // (plus des chapitres HUB). arcName n'a plus de sens => null.
        for (Quest q : questRepository.findByCampaignId(campaignId)) {
            ProgressionStatus prog = progressionByQuest.getOrDefault(q.getId(), ProgressionStatus.NOT_STARTED);
            QuestStatus status = prerequisiteEvaluator.computeStatus(prog, q.getPrerequisites(), ctx);
            switch (status) {
                case AVAILABLE:
                    available.add(new QuestSummary(q.getName(), null, q.getDescription()));
                    break;
                case IN_PROGRESS:
                    inProgress.add(new QuestSummary(q.getName(), null, q.getDescription()));
                    break;
                case LOCKED:
                    lockedTitles.add(q.getName());
                    break;
                case COMPLETED:
                    // Omis (déjà dans le journal des EVENTs).
                    break;
            }
        }

        return new HubStatus(available, inProgress, lockedTitles, activeFlags);
    }

    private List<String> buildActiveFlags(Map<String, Boolean> flags) {
        return flags.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());
    }
}
