package com.loremind.application.playcontext;

import com.loremind.domain.playcontext.Session;
import com.loremind.domain.playcontext.SessionEntry;
import com.loremind.domain.playcontext.ports.SessionEntryRepository;
import com.loremind.domain.playcontext.ports.SessionRecapAssistant;
import com.loremind.domain.playcontext.ports.SessionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Génère le récap « précédemment dans… » à lire à l'OUVERTURE d'une séance : résume le
 * journal de la SÉANCE PRÉCÉDENTE (même Partie, la plus récente commencée avant celle-ci ;
 * ou la dernière terminée si la courante est la plus récente). Zéro persistance : le MJ
 * lit le récap, et peut choisir côté front de le consigner au journal.
 */
@Service
public class SessionRecapService {

    /** Plafond du transcript envoyé au LLM (garde le budget de contexte local ~16k). */
    private static final int MAX_TRANSCRIPT_CHARS = 12_000;

    private final SessionRepository sessionRepository;
    private final SessionEntryRepository entryRepository;
    private final SessionRecapAssistant recapAssistant;

    public SessionRecapService(SessionRepository sessionRepository,
                               SessionEntryRepository entryRepository,
                               SessionRecapAssistant recapAssistant) {
        this.sessionRepository = sessionRepository;
        this.entryRepository = entryRepository;
        this.recapAssistant = recapAssistant;
    }

    /** Récap généré + nom de la séance résumée (pour l'afficher au MJ). */
    public record RecapResult(String previousSessionName, String recap) {}

    public RecapResult recapPreviousSession(String sessionId) {
        Session current = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session introuvable : " + sessionId));

        Session previous = findPreviousSession(current)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Aucune séance précédente à résumer pour cette Partie."));

        List<SessionEntry> entries = entryRepository.findBySessionId(previous.getId());
        if (entries.isEmpty()) {
            throw new IllegalArgumentException(
                    "La séance précédente (« " + previous.getName() + " ») n'a aucune entrée de journal à résumer.");
        }

        String transcript = buildTranscript(entries);
        String context = "Séance : " + previous.getName();
        return new RecapResult(previous.getName(), recapAssistant.generateRecap(transcript, context));
    }

    /** Séance la plus récente de la même Partie commencée AVANT la courante. */
    private Optional<Session> findPreviousSession(Session current) {
        LocalDateTime pivot = current.getStartedAt();
        return sessionRepository.findByPlaythroughId(current.getPlaythroughId()).stream()
                .filter(s -> !s.getId().equals(current.getId()))
                .filter(s -> s.getStartedAt() != null
                        && (pivot == null || s.getStartedAt().isBefore(pivot)))
                .max(Comparator.comparing(Session::getStartedAt));
    }

    /** Journal chronologique, une ligne par entrée, plafonné (on garde la FIN — le dénouement). */
    private static String buildTranscript(List<SessionEntry> entries) {
        StringBuilder sb = new StringBuilder();
        for (SessionEntry e : entries) {
            if (e.getContent() == null || e.getContent().isBlank()) continue;
            sb.append("[").append(e.getType() != null ? e.getType().name() : "NOTE").append("] ")
              .append(e.getContent().trim()).append("\n");
        }
        String transcript = sb.toString();
        if (transcript.length() > MAX_TRANSCRIPT_CHARS) {
            transcript = "…\n" + transcript.substring(transcript.length() - MAX_TRANSCRIPT_CHARS);
        }
        return transcript;
    }
}
