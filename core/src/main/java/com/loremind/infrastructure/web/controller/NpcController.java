package com.loremind.infrastructure.web.controller;

import com.loremind.application.campaigncontext.NpcService;
import com.loremind.domain.campaigncontext.Npc;
import com.loremind.infrastructure.web.dto.campaigncontext.NpcDTO;
import com.loremind.infrastructure.web.mapper.NpcMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/npcs")
public class NpcController {

    private final NpcService npcService;
    private final NpcMapper npcMapper;

    public NpcController(NpcService npcService, NpcMapper npcMapper) {
        this.npcService = npcService;
        this.npcMapper = npcMapper;
    }

    @PostMapping
    public ResponseEntity<NpcDTO> createNpc(@RequestBody NpcDTO dto) {
        Npc created = npcService.createNpc(toData(dto, null));
        return ResponseEntity.ok(npcMapper.toDTO(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NpcDTO> getNpcById(@PathVariable String id) {
        return npcService.getNpcById(id)
                .map(n -> ResponseEntity.ok(npcMapper.toDTO(n)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/campaign/{campaignId}")
    public ResponseEntity<List<NpcDTO>> getNpcsByCampaign(@PathVariable String campaignId) {
        List<NpcDTO> dtos = npcService.getNpcsByCampaignId(campaignId).stream()
                .map(npcMapper::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PutMapping("/{id}")
    public ResponseEntity<NpcDTO> updateNpc(@PathVariable String id, @RequestBody NpcDTO dto) {
        Npc updated = npcService.updateNpc(id, toData(dto, dto.getOrder()));
        return ResponseEntity.ok(npcMapper.toDTO(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNpc(@PathVariable String id) {
        npcService.deleteNpc(id);
        return ResponseEntity.noContent().build();
    }

    private NpcService.NpcData toData(NpcDTO dto, Integer order) {
        return new NpcService.NpcData(
                dto.getName(),
                dto.getPortraitImageId(),
                dto.getHeaderImageId(),
                dto.getValues(),
                dto.getImageValues(),
                dto.getCampaignId(),
                order
        );
    }
}
