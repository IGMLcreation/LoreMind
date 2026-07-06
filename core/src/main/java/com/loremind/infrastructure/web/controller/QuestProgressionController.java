package com.loremind.infrastructure.web.controller;

import com.loremind.application.playcontext.ClockService;
import com.loremind.domain.campaigncontext.quest.ProgressionStatus;
import com.loremind.domain.playcontext.ports.QuestProgressionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Endpoint pour piloter la progression d'une quête (Chapter) au sein d'un Playthrough.
 *
 * <p>Modèle "absence = NOT_STARTED" — envoyer NOT_STARTED supprime la ligne.</p>
 */
@RestController
@RequestMapping("/api/playthroughs/{playthroughId}/quest-progressions")
public class QuestProgressionController {

    private final QuestProgressionRepository repo;
    private final ClockService clockService;

    public QuestProgressionController(QuestProgressionRepository repo, ClockService clockService) {
        this.repo = repo;
        this.clockService = clockService;
    }

    public record SetStatusRequest(String status) {}

    /**
     * GET : renvoie une map questId -> ProgressionStatus pour le Playthrough.
     * Pratique pour le front qui peut indexer puissamment.
     */
    @GetMapping
    public ResponseEntity<Map<String, String>> list(@PathVariable String playthroughId) {
        Map<String, String> out = new HashMap<>();
        repo.findByPlaythroughId(playthroughId)
                .forEach(qp -> out.put(qp.getQuestId(), qp.getStatus().name()));
        return ResponseEntity.ok(out);
    }

    @PutMapping("/{questId}")
    public ResponseEntity<Void> setStatus(@PathVariable String playthroughId,
                                          @PathVariable String questId,
                                          @RequestBody SetStatusRequest body) {
        ProgressionStatus parsed = ProgressionStatus.NOT_STARTED;
        if (body.status() != null && !body.status().isBlank()) {
            try {
                parsed = ProgressionStatus.valueOf(body.status());
            } catch (IllegalArgumentException ex) {
                return ResponseEntity.badRequest().build();
            }
        }
        boolean wasCompleted = repo.findCompletedQuestIdsByPlaythroughId(playthroughId).contains(questId);
        repo.setStatus(playthroughId, questId, parsed);
        // Co-MJ : la quête vient de passer à COMPLETED (transition) -> avancer les horloges liées.
        if (parsed == ProgressionStatus.COMPLETED && !wasCompleted) {
            clockService.onQuestCompleted(playthroughId, questId);
        }
        return ResponseEntity.noContent().build();
    }
}
