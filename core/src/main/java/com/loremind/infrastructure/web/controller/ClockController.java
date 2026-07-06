package com.loremind.infrastructure.web.controller;

import com.loremind.application.playcontext.ClockService;
import com.loremind.domain.playcontext.Clock;
import com.loremind.domain.playcontext.ClockTrigger;
import com.loremind.infrastructure.web.dto.playcontext.ClockDTO;
import com.loremind.infrastructure.web.mapper.ClockMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API REST des Horloges de progression (Clocks) d'une Partie (Play Context).
 * Ressource imbriquée sous le Playthrough, comme {@code quest-progressions}.
 */
@RestController
@RequestMapping("/api/playthroughs/{playthroughId}/clocks")
public class ClockController {

    /** Taille par défaut d'une horloge si non précisée. */
    private static final int DEFAULT_SEGMENTS = 4;

    private final ClockService clockService;
    private final ClockMapper clockMapper;

    public ClockController(ClockService clockService, ClockMapper clockMapper) {
        this.clockService = clockService;
        this.clockMapper = clockMapper;
    }

    public record ClockRequest(String name, String description, Integer segments,
                               String triggerType, String triggerRef, String frontId) {}

    @GetMapping
    public ResponseEntity<List<ClockDTO>> list(@PathVariable String playthroughId) {
        List<ClockDTO> dtos = clockService.getByPlaythrough(playthroughId).stream()
                .map(clockMapper::toDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    public ResponseEntity<ClockDTO> create(@PathVariable String playthroughId, @RequestBody ClockRequest req) {
        Clock created = clockService.create(playthroughId, req.name(), req.description(), segmentsOr(req),
                parseTrigger(req.triggerType()), req.triggerRef(), req.frontId());
        return ResponseEntity.ok(clockMapper.toDTO(created));
    }

    @PutMapping("/{clockId}")
    public ResponseEntity<ClockDTO> update(@PathVariable String playthroughId, @PathVariable String clockId,
                                           @RequestBody ClockRequest req) {
        Clock updated = clockService.update(clockId, req.name(), req.description(), segmentsOr(req),
                parseTrigger(req.triggerType()), req.triggerRef(), req.frontId());
        return ResponseEntity.ok(clockMapper.toDTO(updated));
    }

    @PutMapping("/{clockId}/advance")
    public ResponseEntity<ClockDTO> advance(@PathVariable String playthroughId, @PathVariable String clockId) {
        return ResponseEntity.ok(clockMapper.toDTO(clockService.advance(clockId)));
    }

    @PutMapping("/{clockId}/regress")
    public ResponseEntity<ClockDTO> regress(@PathVariable String playthroughId, @PathVariable String clockId) {
        return ResponseEntity.ok(clockMapper.toDTO(clockService.regress(clockId)));
    }

    @DeleteMapping("/{clockId}")
    public ResponseEntity<Void> delete(@PathVariable String playthroughId, @PathVariable String clockId) {
        clockService.delete(clockId);
        return ResponseEntity.noContent().build();
    }

    private int segmentsOr(ClockRequest req) {
        return req.segments() != null ? req.segments() : DEFAULT_SEGMENTS;
    }

    /** Parse tolérant du type de déclencheur : null / valeur inconnue -> NONE. */
    private ClockTrigger parseTrigger(String value) {
        if (value == null || value.isBlank()) return ClockTrigger.NONE;
        try {
            return ClockTrigger.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return ClockTrigger.NONE;
        }
    }
}
