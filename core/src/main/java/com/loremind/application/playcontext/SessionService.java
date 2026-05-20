package com.loremind.application.playcontext;

import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.playcontext.Session;
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
 * Fait partie de la couche Application de l'Architecture Hexagonale.
 *
 * <p>Règle métier : une seule Session peut être active (endedAt null) à la fois
 * dans l'application.</p>
 */
@Service
public class SessionService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final SessionRepository sessionRepository;
    private final SessionEntryRepository entryRepository;
    private final CampaignRepository campaignRepository;

    public SessionService(SessionRepository sessionRepository,
                          SessionEntryRepository entryRepository,
                          CampaignRepository campaignRepository) {
        this.sessionRepository = sessionRepository;
        this.entryRepository = entryRepository;
        this.campaignRepository = campaignRepository;
    }

    /**
     * Lance une nouvelle session sur la campagne donnée.
     * Échoue si une session est déjà active ou si la campagne n'existe pas.
     */
    public Session startSession(String campaignId) {
        if (campaignId == null || campaignId.isBlank()) {
            throw new IllegalArgumentException("campaignId est requis pour démarrer une session.");
        }
        if (!campaignRepository.existsById(campaignId)) {
            throw new IllegalArgumentException("Campagne introuvable : " + campaignId);
        }
        sessionRepository.findActive().ifPresent(s -> {
            throw new IllegalStateException("Une session est déjà en cours (id=" + s.getId() + "). Termine-la avant d'en lancer une nouvelle.");
        });

        LocalDateTime now = LocalDateTime.now();
        Session session = Session.builder()
                .name(generateDefaultName(now))
                .campaignId(campaignId)
                .startedAt(now)
                .build();
        return sessionRepository.save(session);
    }

    /** Termine la session active si elle correspond à l'id donné. */
    public Session endSession(String id) {
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Session introuvable : " + id));
        if (!session.isActive()) {
            throw new IllegalStateException("Cette session est déjà terminée.");
        }
        session.setEndedAt(LocalDateTime.now());
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

    public List<Session> getAll() {
        return sessionRepository.findAll();
    }

    public List<Session> getByCampaignId(String campaignId) {
        return sessionRepository.findByCampaignId(campaignId);
    }

    /**
     * Supprime une session et toutes ses entrées de journal en cascade.
     * Transactionnel : soit tout disparaît, soit rien.
     */
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
