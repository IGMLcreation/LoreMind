package com.loremind.application.playcontext;

import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.campaigncontext.ports.CharacterRepository;
import com.loremind.domain.playcontext.Playthrough;
import com.loremind.domain.playcontext.Session;
import com.loremind.domain.playcontext.ports.PlaythroughFlagRepository;
import com.loremind.domain.playcontext.ports.PlaythroughRepository;
import com.loremind.domain.playcontext.ports.QuestProgressionRepository;
import com.loremind.domain.playcontext.ports.SessionRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service applicatif pour le cycle de vie d'un Playthrough (Partie).
 *
 * <p>Cascade de suppression : un Playthrough supprime ses flags, ses progressions,
 * ses PJ et ses sessions (avec leurs entrées de journal).</p>
 */
@Service
public class PlaythroughService {

    private final PlaythroughRepository playthroughRepository;
    private final CampaignRepository campaignRepository;
    private final PlaythroughFlagRepository flagRepository;
    private final QuestProgressionRepository progressionRepository;
    private final CharacterRepository characterRepository;
    private final SessionRepository sessionRepository;
    private final SessionService sessionService;

    public PlaythroughService(PlaythroughRepository playthroughRepository,
                              CampaignRepository campaignRepository,
                              PlaythroughFlagRepository flagRepository,
                              QuestProgressionRepository progressionRepository,
                              CharacterRepository characterRepository,
                              SessionRepository sessionRepository,
                              SessionService sessionService) {
        this.playthroughRepository = playthroughRepository;
        this.campaignRepository = campaignRepository;
        this.flagRepository = flagRepository;
        this.progressionRepository = progressionRepository;
        this.characterRepository = characterRepository;
        this.sessionRepository = sessionRepository;
        this.sessionService = sessionService;
    }

    /** Compte des entités qui seront supprimées en cascade avec la Partie. */
    public record DeletionImpact(int sessions, int characters, int flags, int progressions) {}

    public Playthrough create(String campaignId, String name, String description) {
        if (campaignId == null || campaignId.isBlank()) {
            throw new IllegalArgumentException("campaignId requis.");
        }
        if (!campaignRepository.existsById(campaignId)) {
            throw new IllegalArgumentException("Campagne introuvable : " + campaignId);
        }
        Playthrough p = Playthrough.builder()
                .campaignId(campaignId)
                .name((name == null || name.isBlank()) ? "Partie principale" : name.trim())
                .description(description)
                .build();
        return playthroughRepository.save(p);
    }

    public Playthrough update(String id, Playthrough updated) {
        Playthrough existing = playthroughRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Partie introuvable : " + id));
        BeanUtils.copyProperties(updated, existing, "id", "campaignId", "createdAt");
        return playthroughRepository.save(existing);
    }

    public Optional<Playthrough> getById(String id) {
        return playthroughRepository.findById(id);
    }

    public List<Playthrough> getByCampaignId(String campaignId) {
        return playthroughRepository.findByCampaignId(campaignId);
    }

    public DeletionImpact getDeletionImpact(String id) {
        int sessions = sessionRepository.findByPlaythroughId(id).size();
        int characters = characterRepository.findByPlaythroughId(id).size();
        int flags = flagRepository.findByPlaythroughId(id).size();
        int progressions = progressionRepository.findByPlaythroughId(id).size();
        return new DeletionImpact(sessions, characters, flags, progressions);
    }

    @Transactional
    public void delete(String id) {
        if (!playthroughRepository.existsById(id)) {
            throw new IllegalArgumentException("Partie introuvable : " + id);
        }
        // Cascade : sessions (et leurs entries), PJ, flags, progressions
        for (Session s : sessionRepository.findByPlaythroughId(id)) {
            sessionService.deleteSession(s.getId());
        }
        characterRepository.findByPlaythroughId(id).forEach(c -> characterRepository.deleteById(c.getId()));
        flagRepository.deleteAllByPlaythroughId(id);
        progressionRepository.deleteAllByPlaythroughId(id);
        playthroughRepository.deleteById(id);
    }

    public boolean exists(String id) {
        return playthroughRepository.existsById(id);
    }
}
