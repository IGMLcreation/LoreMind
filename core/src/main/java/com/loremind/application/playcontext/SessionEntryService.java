package com.loremind.application.playcontext;

import com.loremind.domain.playcontext.EntryType;
import com.loremind.domain.playcontext.SessionEntry;
import com.loremind.domain.playcontext.ports.SessionEntryRepository;
import com.loremind.domain.playcontext.ports.SessionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Service d'application pour le journal d'une Session.
 * Gère le cycle CRUD des entrées (note, évènement, jet, action joueur).
 */
@Service
public class SessionEntryService {

    private final SessionEntryRepository entryRepository;
    private final SessionRepository sessionRepository;

    public SessionEntryService(SessionEntryRepository entryRepository,
                               SessionRepository sessionRepository) {
        this.entryRepository = entryRepository;
        this.sessionRepository = sessionRepository;
    }

    /** Données fournies par l'API pour créer ou éditer une entrée. */
    public record EntryData(EntryType type, String content, LocalDateTime occurredAt) {}

    public SessionEntry createEntry(String sessionId, EntryData data) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId est requis.");
        }
        if (!sessionRepository.existsById(sessionId)) {
            throw new IllegalArgumentException("Session introuvable : " + sessionId);
        }
        validateContent(data.content());

        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        SessionEntry entry = SessionEntry.builder()
                .sessionId(sessionId)
                .type(data.type() != null ? data.type() : EntryType.NOTE)
                .content(data.content().trim())
                .occurredAt(data.occurredAt() != null ? data.occurredAt() : now)
                .build();
        return entryRepository.save(entry);
    }

    public SessionEntry updateEntry(String id, EntryData data) {
        validateContent(data.content());
        SessionEntry existing = entryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Entrée introuvable : " + id));
        if (data.type() != null) existing.setType(data.type());
        existing.setContent(data.content().trim());
        if (data.occurredAt() != null) existing.setOccurredAt(data.occurredAt());
        return entryRepository.save(existing);
    }

    public Optional<SessionEntry> getById(String id) {
        return entryRepository.findById(id);
    }

    public List<SessionEntry> getBySessionId(String sessionId) {
        return entryRepository.findBySessionId(sessionId);
    }

    public void deleteEntry(String id) {
        if (!entryRepository.existsById(id)) {
            throw new IllegalArgumentException("Entrée introuvable : " + id);
        }
        entryRepository.deleteById(id);
    }

    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Le contenu d'une entrée ne peut pas être vide.");
        }
    }
}
