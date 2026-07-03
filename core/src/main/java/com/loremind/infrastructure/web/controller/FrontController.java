package com.loremind.infrastructure.web.controller;

import com.loremind.application.playcontext.FrontService;
import com.loremind.domain.playcontext.Front;
import com.loremind.infrastructure.web.dto.playcontext.FrontDTO;
import com.loremind.infrastructure.web.mapper.FrontMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * API REST des Fronts (menaces regroupant des horloges) d'une Partie (Play Context).
 * Ressource imbriquée sous le Playthrough, comme {@code clocks}.
 */
@RestController
@RequestMapping("/api/playthroughs/{playthroughId}/fronts")
public class FrontController {

    private final FrontService frontService;
    private final FrontMapper frontMapper;

    public FrontController(FrontService frontService, FrontMapper frontMapper) {
        this.frontService = frontService;
        this.frontMapper = frontMapper;
    }

    public record FrontRequest(String name, String description) {}

    @GetMapping
    public ResponseEntity<List<FrontDTO>> list(@PathVariable String playthroughId) {
        List<FrontDTO> dtos = frontService.getByPlaythrough(playthroughId).stream()
                .map(frontMapper::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    public ResponseEntity<FrontDTO> create(@PathVariable String playthroughId, @RequestBody FrontRequest req) {
        Front created = frontService.create(playthroughId, req.name(), req.description());
        return ResponseEntity.ok(frontMapper.toDTO(created));
    }

    @PutMapping("/{frontId}")
    public ResponseEntity<FrontDTO> update(@PathVariable String playthroughId, @PathVariable String frontId,
                                           @RequestBody FrontRequest req) {
        Front updated = frontService.update(frontId, req.name(), req.description());
        return ResponseEntity.ok(frontMapper.toDTO(updated));
    }

    @DeleteMapping("/{frontId}")
    public ResponseEntity<Void> delete(@PathVariable String playthroughId, @PathVariable String frontId) {
        frontService.delete(frontId);
        return ResponseEntity.noContent().build();
    }
}
