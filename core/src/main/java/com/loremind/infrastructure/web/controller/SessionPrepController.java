package com.loremind.infrastructure.web.controller;

import com.loremind.application.playcontext.SessionPrepReport;
import com.loremind.application.playcontext.SessionPrepService;
import com.loremind.domain.playcontext.ports.PlaythroughRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller « Préparer la prochaine séance » (Phase 3 co-MJ). Read-model pur :
 * position des joueurs + contenu probable + manques ciblés + horloges en mouvement.
 * 404 si la Partie est inconnue (calqué sur {@code CampaignReadinessController}).
 */
@RestController
@RequestMapping("/api/playthroughs/{playthroughId}/session-prep")
public class SessionPrepController {

    private final SessionPrepService sessionPrepService;
    private final PlaythroughRepository playthroughRepository;

    public SessionPrepController(SessionPrepService sessionPrepService,
                                 PlaythroughRepository playthroughRepository) {
        this.sessionPrepService = sessionPrepService;
        this.playthroughRepository = playthroughRepository;
    }

    @GetMapping
    public ResponseEntity<SessionPrepReport> getPrep(@PathVariable String playthroughId) {
        if (playthroughRepository.findById(playthroughId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(sessionPrepService.prepare(playthroughId));
    }
}
