package com.loremind.application.playcontext;

import com.loremind.domain.playcontext.Session;
import com.loremind.domain.playcontext.ports.PlaythroughRepository;
import com.loremind.domain.playcontext.ports.SessionEntryRepository;
import com.loremind.domain.playcontext.ports.SessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Service d'application pour le Play Context.
 * Orchestre le cycle de vie d'une Session (lancement, fin, renommage).
 *
 * <p>Règle métier : une seule Session peut être active (endedAt null) à la fois.</p>
 * <p>Depuis Playthrough : une Session appartient à un Playthrough (pas directement à une Campaign).</p>
 */
@Service
public class SessionService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final SessionRepository sessionRepository;
    private final SessionEntryRepository entryRepository;
    private final PlaythroughRepository playthroughRepository;
    private final ClockService clockService;

    public SessionService(SessionRepository sessionRepository,
                          SessionEntryRepository entryRepository,
                          PlaythroughRepository playthroughRepository,
                          ClockService clockService) {
        this.sessionRepository = sessionRepository;
        this.entryRepository = entryRepository;
        this.playthroughRepository = playthroughRepository;
        this.clockService = clockService;
    }

    /**
     * Lance une nouvelle session sur le Playthrough donné.
     * Échoue si une session est déjà active ou si le Playthrough n'existe pas.
     */
    public Session startSession(String playthroughId) {
        if (playthroughId == null || playthroughId.isBlank()) {
            throw new IllegalArgumentException("playthroughId est requis pour démarrer une session.");
        }
        if (!playthroughRepository.existsById(playthroughId)) {
            throw new IllegalArgumentException("Partie introuvable : " + playthroughId);
        }
        // Règle métier : une seule session active par Partie (pas de verrou global cross-Partie).
        sessionRepository.findActiveByPlaythroughId(playthroughId).ifPresent(s -> {
            throw new IllegalStateException(
                    "Une session est déjà en cours pour cette Partie (id=" + s.getId() +
                    "). Termine-la avant d'en lancer une nouvelle.");
        });

        LocalDateTime now = LocalDateTime.now();
        Session session = Session.builder()
                .name(generateDefaultName(now))
                .playthroughId(playthroughId)
                .startedAt(now)
                .build();
        return sessionRepository.save(session);
    }

    public Session endSession(String id) {
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Session introuvable : " + id));
        if (!session.isActive()) {
            throw new IllegalStateException("Cette session est déjà terminée.");
        }
        session.setEndedAt(LocalDateTime.now());
        Session saved = sessionRepository.save(session);
        // Co-MJ : la séance se clôture -> avancer les horloges « fin de séance » de la Partie.
        clockService.onSessionEnded(saved.getPlaythroughId());
        return saved;
    }

    /**
     * Épingle (ou dés-épingle avec {@code null}) la scène courante de la session — mode
     * cockpit : « on en est là ». Weak ref : on ne valide pas l'existence de la scène
     * (l'UI ne propose que des scènes réelles ; une scène supprimée rend l'épingle caduque).
     */
    public Session setCurrentScene(String id, String sceneId) {
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Session introuvable : " + id));
        session.setCurrentSceneId(sceneId != null && !sceneId.isBlank() ? sceneId : null);
        return sessionRepository.save(session);
    }

    public Session renameSession(String id, String newName) {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("Le nom de la session ne peut pas être vide.");
        }
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Session introuvable : " + id));
        session.setName(newName.trim());
        return sessionRepository.save(session);
    }

    public Optional<Session> getById(String id) {
        return sessionRepository.findById(id);
    }

    public Optional<Session> getActive() {
        return sessionRepository.findActive();
    }

    public Optional<Session> getActiveByPlaythrough(String playthroughId) {
        return sessionRepository.findActiveByPlaythroughId(playthroughId);
    }

    public List<Session> getAll() {
        return sessionRepository.findAll();
    }

    public List<Session> getByPlaythroughId(String playthroughId) {
        return sessionRepository.findByPlaythroughId(playthroughId);
    }

    @Transactional
    public void deleteSession(String id) {
        if (!sessionRepository.existsById(id)) {
            throw new IllegalArgumentException("Session introuvable : " + id);
        }
        entryRepository.deleteBySessionId(id);
        sessionRepository.deleteById(id);
    }

    private String generateDefaultName(LocalDateTime startedAt) {
        return "Session du " + startedAt.format(DATE_FORMATTER);
    }
}
