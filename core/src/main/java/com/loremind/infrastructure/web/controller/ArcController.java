package com.loremind.infrastructure.web.controller;

import com.loremind.application.campaigncontext.ArcService;
import com.loremind.domain.campaigncontext.Arc;
import com.loremind.infrastructure.web.dto.campaigncontext.ArcDTO;
import com.loremind.infrastructure.web.mapper.ArcMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller pour le contexte Arc.
 */
@RestController
@RequestMapping("/api/arcs")
public class ArcController {

    private final ArcService arcService;
    private final ArcMapper arcMapper;

    public ArcController(ArcService arcService, ArcMapper arcMapper) {
        this.arcService = arcService;
        this.arcMapper = arcMapper;
    }

    @PostMapping
    public ResponseEntity<ArcDTO> createArc(@RequestBody ArcDTO arcDTO) {
        Arc arc = arcMapper.toDomain(arcDTO);
        Arc createdArc = arcService.createArc(arc.getName(), arc.getDescription(), arc.getCampaignId(), arc.getOrder());
        return ResponseEntity.ok(arcMapper.toDTO(createdArc));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ArcDTO> getArcById(@PathVariable String id) {
        return arcService.getArcById(id)
                .map(arc -> ResponseEntity.ok(arcMapper.toDTO(arc)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<ArcDTO>> getAllArcs() {
        List<Arc> arcs = arcService.getAllArcs();
        List<ArcDTO> arcDTOs = arcs.stream()
                .map(arcMapper::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(arcDTOs);
    }

    @GetMapping("/campaign/{campaignId}")
    public ResponseEntity<List<ArcDTO>> getArcsByCampaignId(@PathVariable String campaignId) {
        List<Arc> arcs = arcService.getArcsByCampaignId(campaignId);
        List<ArcDTO> arcDTOs = arcs.stream()
                .map(arcMapper::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(arcDTOs);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ArcDTO> updateArc(@PathVariable String id, @RequestBody ArcDTO arcDTO) {
        Arc updatedArc = arcService.updateArc(id, arcMapper.toDomain(arcDTO));
        return ResponseEntity.ok(arcMapper.toDTO(updatedArc));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteArc(@PathVariable String id) {
        arcService.deleteArc(id);
        return ResponseEntity.noContent().build();
    }
}
