package com.loremind.application.playcontext;

import com.loremind.domain.playcontext.Clock;
import com.loremind.domain.playcontext.Front;
import com.loremind.domain.playcontext.ports.ClockRepository;
import com.loremind.domain.playcontext.ports.FrontRepository;
import com.loremind.domain.playcontext.ports.PlaythroughRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service applicatif des Fronts (menaces regroupant des horloges) d'une Partie.
 */
@Service
public class FrontService {

    private final FrontRepository frontRepository;
    private final ClockRepository clockRepository;
    private final PlaythroughRepository playthroughRepository;

    public FrontService(FrontRepository frontRepository, ClockRepository clockRepository,
                        PlaythroughRepository playthroughRepository) {
        this.frontRepository = frontRepository;
        this.clockRepository = clockRepository;
        this.playthroughRepository = playthroughRepository;
    }

    public List<Front> getByPlaythrough(String playthroughId) {
        return frontRepository.findByPlaythroughId(playthroughId);
    }

    public Front create(String playthroughId, String name, String description) {
        if (playthroughId == null || !playthroughRepository.existsById(playthroughId)) {
            throw new IllegalArgumentException("Partie introuvable : " + playthroughId);
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Le nom du front est requis.");
        }
        int order = frontRepository.findByPlaythroughId(playthroughId).size();
        Front front = Front.builder()
                .playthroughId(playthroughId)
                .name(name.trim())
                .description(description)
                .order(order)
                .build();
        return frontRepository.save(front);
    }

    public Front update(String id, String name, String description) {
        Front front = require(id);
        if (name != null && !name.isBlank()) front.setName(name.trim());
        front.setDescription(description);
        return frontRepository.save(front);
    }

    /** Supprime un front et ORPHELINE ses horloges (frontId -> null : on ne perd pas d'horloges). */
    @Transactional
    public void delete(String id) {
        Front front = require(id);
        for (Clock c : clockRepository.findByPlaythroughId(front.getPlaythroughId())) {
            if (id.equals(c.getFrontId())) {
                c.setFrontId(null);
                clockRepository.save(c);
            }
        }
        frontRepository.deleteById(id);
    }

    private Front require(String id) {
        return frontRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Front introuvable : " + id));
    }
}
