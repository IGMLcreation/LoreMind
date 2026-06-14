package com.loremind.infrastructure.web.controller;

import com.loremind.domain.campaigncontext.ProgressionStatus;
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

    public QuestProgressionController(QuestProgressionRepository repo) {
        this.repo = repo;
    }

    public record SetStatusRequest(String status) {}

    /**
     * GET : renvoie une map chapterId -> ProgressionStatus pour le Playthrough.
     * Pratique pour le front qui peut indexer puissamment.
     */
    @GetMapping
    public ResponseEntity<Map<String, String>> list(@PathVariable String playthroughId) {
        Map<String, String> out = new HashMap<>();
        repo.findByPlaythroughId(playthroughId)
                .forEach(qp -> out.put(qp.getChapterId(), qp.getStatus().name()));
        return ResponseEntity.ok(out);
    }

    @PutMapping("/{chapterId}")
    public ResponseEntity<Void> setStatus(@PathVariable String playthroughId,
                                          @PathVariable String chapterId,
                                          @RequestBody SetStatusRequest body) {
        ProgressionStatus parsed = ProgressionStatus.NOT_STARTED;
        if (body.status() != null && !body.status().isBlank()) {
            try {
                parsed = ProgressionStatus.valueOf(body.status());
            } catch (IllegalArgumentException ex) {
                return ResponseEntity.badRequest().build();
            }
        }
        repo.setStatus(playthroughId, chapterId, parsed);
        return ResponseEntity.noContent().build();
    }
}
