package com.loremind.infrastructure.web.controller;

import com.loremind.application.playcontext.PlaythroughService;
import com.loremind.domain.playcontext.Playthrough;
import com.loremind.infrastructure.web.dto.playcontext.PlaythroughDTO;
import com.loremind.infrastructure.web.mapper.PlaythroughMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/playthroughs")
public class PlaythroughController {

    private final PlaythroughService service;
    private final PlaythroughMapper mapper;

    public PlaythroughController(PlaythroughService service, PlaythroughMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<PlaythroughDTO> create(@RequestBody PlaythroughDTO body) {
        Playthrough created = service.create(body.getCampaignId(), body.getName(), body.getDescription());
        return ResponseEntity.ok(mapper.toDTO(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlaythroughDTO> getById(@PathVariable String id) {
        return service.getById(id)
                .map(p -> ResponseEntity.ok(mapper.toDTO(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<PlaythroughDTO>> list(
            @RequestParam(value = "campaignId", required = false) String campaignId) {
        List<Playthrough> list = (campaignId != null && !campaignId.isBlank())
                ? service.getByCampaignId(campaignId)
                : List.of();
        return ResponseEntity.ok(list.stream().map(mapper::toDTO).toList());
    }

    @PutMapping("/{id}")
    public ResponseEntity<PlaythroughDTO> update(@PathVariable String id, @RequestBody PlaythroughDTO body) {
        Playthrough updated = service.update(id, mapper.toDomain(body));
        return ResponseEntity.ok(mapper.toDTO(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/deletion-impact")
    public ResponseEntity<PlaythroughService.DeletionImpact> deletionImpact(@PathVariable String id) {
        if (!service.exists(id)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(service.getDeletionImpact(id));
    }
}
