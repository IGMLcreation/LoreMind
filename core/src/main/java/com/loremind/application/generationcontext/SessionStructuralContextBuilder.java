package com.loremind.application.generationcontext;

import com.loremind.domain.generationcontext.SessionContext;
import com.loremind.domain.generationcontext.SessionContext.JournalEntrySummary;
import com.loremind.domain.playcontext.Session;
import com.loremind.domain.playcontext.SessionEntry;
import com.loremind.domain.playcontext.ports.SessionEntryRepository;
import com.loremind.domain.playcontext.ports.SessionRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Construit le SessionContext injecté dans le prompt IA pendant une partie.
 *
 * <p>Charge la Session + les N dernières entrées du journal et les mappe vers
 * le Value Object {@link SessionContext}. La limite d'entrées évite de saturer
 * la fenêtre de contexte du LLM sur des sessions très longues.</p>
 */
@Component
public class SessionStructuralContextBuilder {

    /**
     * Plafond du nombre d'entrées remontées au LLM.
     * Choisi pour rester dans des limites raisonnables (≈ 5-10k tokens max
     * pour des entrées moyennes de 200 chars). Si la session déborde,
     * on garde les entrées les plus récentes (fin de chronologie).
     */
    private static final int MAX_ENTRIES = 80;

    private final SessionRepository sessionRepository;
    private final SessionEntryRepository entryRepository;

    public SessionStructuralContextBuilder(SessionRepository sessionRepository,
                                           SessionEntryRepository entryRepository) {
        this.sessionRepository = sessionRepository;
        this.entryRepository = entryRepository;
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
        List<SessionEntry> allEntries = entryRepository.findBySessionId(session.getId());
        // findBySessionId renvoie en ASC. On garde la fin si la liste dépasse le plafond
        // — c'est l'info récente qui aide le plus l'IA pendant la partie.
        List<SessionEntry> kept = allEntries.size() <= MAX_ENTRIES
                ? allEntries
                : allEntries.subList(allEntries.size() - MAX_ENTRIES, allEntries.size());

        List<JournalEntrySummary> summaries = kept.stream()
                .map(e -> new JournalEntrySummary(
                        e.getType() != null ? e.getType().name() : "NOTE",
                        e.getContent(),
                        e.getOccurredAt()))
                .collect(Collectors.toList());

        return new SessionContext(
                session.getName(),
                session.isActive(),
                session.getStartedAt(),
                summaries);
    }
}
