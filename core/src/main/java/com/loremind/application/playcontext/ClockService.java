package com.loremind.application.playcontext;

import com.loremind.domain.playcontext.Clock;
import com.loremind.domain.playcontext.ClockTrigger;
import com.loremind.domain.playcontext.ports.ClockRepository;
import com.loremind.domain.playcontext.ports.PlaythroughRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service applicatif des Horloges de progression (Clocks) d'une Partie.
 * CRUD + <b>avancer / reculer</b> d'un segment, borné à [0, segments].
 */
@Service
public class ClockService {

    /** Garde-fou : taille maximale d'une horloge. */
    private static final int MAX_SEGMENTS = 60;

    private final ClockRepository clockRepository;
    private final PlaythroughRepository playthroughRepository;

    public ClockService(ClockRepository clockRepository, PlaythroughRepository playthroughRepository) {
        this.clockRepository = clockRepository;
        this.playthroughRepository = playthroughRepository;
    }

    public List<Clock> getByPlaythrough(String playthroughId) {
        return clockRepository.findByPlaythroughId(playthroughId);
    }

    public Clock create(String playthroughId, String name, String description, int segments,
                        ClockTrigger triggerType, String triggerRef, String frontId) {
        if (playthroughId == null || !playthroughRepository.existsById(playthroughId)) {
            throw new IllegalArgumentException("Partie introuvable : " + playthroughId);
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Le nom de l'horloge est requis.");
        }
        int order = clockRepository.findByPlaythroughId(playthroughId).size();
        ClockTrigger type = triggerType != null ? triggerType : ClockTrigger.NONE;
        Clock clock = Clock.builder()
                .playthroughId(playthroughId)
                .name(name.trim())
                .description(description)
                .segments(clampSegments(segments))
                .filled(0)
                .order(order)
                .triggerType(type)
                .triggerRef(normalizeRef(type, triggerRef))
                .frontId(blankToNull(frontId))
                .build();
        return clockRepository.save(clock);
    }

    public Clock update(String id, String name, String description, int segments,
                        ClockTrigger triggerType, String triggerRef, String frontId) {
        Clock clock = require(id);
        if (name != null && !name.isBlank()) clock.setName(name.trim());
        clock.setDescription(description);
        int seg = clampSegments(segments);
        clock.setSegments(seg);
        if (clock.getFilled() > seg) clock.setFilled(seg); // ré-borne si on réduit la taille
        ClockTrigger type = triggerType != null ? triggerType : ClockTrigger.NONE;
        clock.setTriggerType(type);
        clock.setTriggerRef(normalizeRef(type, triggerRef));
        clock.setFrontId(blankToNull(frontId));
        return clockRepository.save(clock);
    }

    // ----- Avancement automatique (co-MJ) : le monde qui réagit -----

    /** Un Fait vient de passer à vrai → avance les horloges liées (FLAG_SET + ce fait). */
    public void onFlagRaised(String playthroughId, String flagName) {
        advanceMatching(playthroughId, ClockTrigger.FLAG_SET, flagName);
    }

    /** Une quête vient de passer à COMPLETED → avance les horloges liées à cette quête. */
    public void onQuestCompleted(String playthroughId, String questId) {
        advanceMatching(playthroughId, ClockTrigger.QUEST_COMPLETED, questId);
    }

    /** Une séance vient de se clôturer → avance les horloges « fin de séance » de la Partie. */
    public void onSessionEnded(String playthroughId) {
        advanceMatching(playthroughId, ClockTrigger.SESSION_ENDED, null);
    }

    private void advanceMatching(String playthroughId, ClockTrigger type, String ref) {
        if (playthroughId == null) return;
        for (Clock c : clockRepository.findByPlaythroughId(playthroughId)) {
            boolean match = c.getTriggerType() == type
                    && (type == ClockTrigger.SESSION_ENDED || java.util.Objects.equals(c.getTriggerRef(), ref));
            if (match && c.getFilled() < c.getSegments()) {
                c.setFilled(c.getFilled() + 1);
                clockRepository.save(c);
            }
        }
    }

    private String normalizeRef(ClockTrigger type, String ref) {
        boolean needsRef = type == ClockTrigger.FLAG_SET || type == ClockTrigger.QUEST_COMPLETED;
        if (!needsRef || ref == null || ref.isBlank()) return null;
        return ref.trim();
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /** +1 segment (sans dépasser {@code segments}). */
    public Clock advance(String id) {
        Clock clock = require(id);
        if (clock.getFilled() < clock.getSegments()) clock.setFilled(clock.getFilled() + 1);
        return clockRepository.save(clock);
    }

    /** −1 segment (sans descendre sous 0). */
    public Clock regress(String id) {
        Clock clock = require(id);
        if (clock.getFilled() > 0) clock.setFilled(clock.getFilled() - 1);
        return clockRepository.save(clock);
    }

    public void delete(String id) {
        if (!clockRepository.existsById(id)) {
            throw new IllegalArgumentException("Horloge introuvable : " + id);
        }
        clockRepository.deleteById(id);
    }

    private Clock require(String id) {
        return clockRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Horloge introuvable : " + id));
    }

    private int clampSegments(int segments) {
        if (segments < 1) return 1;
        return Math.min(segments, MAX_SEGMENTS);
    }
}
