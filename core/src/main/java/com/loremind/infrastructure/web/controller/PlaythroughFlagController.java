package com.loremind.infrastructure.web.controller;

import com.loremind.application.playcontext.ClockService;
import com.loremind.domain.playcontext.ports.PlaythroughFlagRepository;
import com.loremind.infrastructure.web.dto.playcontext.PlaythroughFlagDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Endpoint des flags narratifs d'une Partie.
 * Remplace l'ancien {@code /api/campaigns/{id}/flags} : les flags suivent la Partie.
 */
@RestController
@RequestMapping("/api/playthroughs/{playthroughId}/flags")
public class PlaythroughFlagController {

    private final PlaythroughFlagRepository repo;
    private final ClockService clockService;

    public PlaythroughFlagController(PlaythroughFlagRepository repo, ClockService clockService) {
        this.repo = repo;
        this.clockService = clockService;
    }

    @GetMapping
    public ResponseEntity<List<PlaythroughFlagDTO>> list(@PathVariable String playthroughId) {
        Map<String, Boolean> flags = repo.findByPlaythroughId(playthroughId);
        List<PlaythroughFlagDTO> dtos = new ArrayList<>(flags.size());
        flags.forEach((name, value) -> dtos.add(new PlaythroughFlagDTO(name, value)));
        return ResponseEntity.ok(dtos);
    }

    @PutMapping("/{name}")
    public ResponseEntity<PlaythroughFlagDTO> setFlag(@PathVariable String playthroughId,
                                                     @PathVariable String name,
                                                     @RequestBody PlaythroughFlagDTO body) {
        boolean wasRaised = Boolean.TRUE.equals(repo.findByPlaythroughId(playthroughId).get(name));
        repo.setFlag(playthroughId, name, body.isValue());
        // Co-MJ : le Fait vient de passer à vrai (transition) -> avancer les horloges liées.
        if (body.isValue() && !wasRaised) clockService.onFlagRaised(playthroughId, name);
        return ResponseEntity.ok(new PlaythroughFlagDTO(name, body.isValue()));
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> deleteFlag(@PathVariable String playthroughId, @PathVariable String name) {
        repo.deleteFlag(playthroughId, name);
        return ResponseEntity.noContent().build();
    }
}
