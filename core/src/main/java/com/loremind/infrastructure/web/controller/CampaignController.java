package com.loremind.infrastructure.web.controller;

import com.loremind.application.campaigncontext.CampaignService;
import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.infrastructure.web.dto.campaigncontext.CampaignDTO;
import com.loremind.infrastructure.web.mapper.CampaignMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller pour le contexte Campaign.
 * Adaptateur d'infrastructure qui expose l'API REST.
 * Utilise le Service d'application et le Mapper selon l'Architecture Hexagonale.
 */
@RestController
@RequestMapping("/api/campaigns")
public class CampaignController {

    private final CampaignService campaignService;
    private final CampaignMapper campaignMapper;

    public CampaignController(CampaignService campaignService, CampaignMapper campaignMapper) {
        this.campaignService = campaignService;
        this.campaignMapper = campaignMapper;
    }

    @PostMapping
    public ResponseEntity<CampaignDTO> createCampaign(@RequestBody CampaignDTO campaignDTO) {
        Campaign campaign = campaignMapper.toDomain(campaignDTO);
        Campaign createdCampaign = campaignService.createCampaign(
                new CampaignService.CampaignData(campaign.getName(), campaign.getDescription(), campaign.getLoreId(), campaign.getGameSystemId())
        );
        return ResponseEntity.ok(campaignMapper.toDTO(createdCampaign));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CampaignDTO> getCampaignById(@PathVariable String id) {
        return campaignService.getCampaignById(id)
                .map(campaign -> ResponseEntity.ok(campaignMapper.toDTO(campaign)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<CampaignDTO>> getAllCampaigns() {
        List<Campaign> campaigns = campaignService.getAllCampaigns();
        List<CampaignDTO> campaignDTOs = campaigns.stream()
                .map(campaignMapper::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(campaignDTOs);
    }

    @GetMapping("/search")
    public ResponseEntity<List<CampaignDTO>> searchCampaigns(@RequestParam("q") String query) {
        List<CampaignDTO> dtos = campaignService.searchCampaigns(query).stream()
                .map(campaignMapper::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CampaignDTO> updateCampaign(@PathVariable String id, @RequestBody CampaignDTO campaignDTO) {
        Campaign updatedCampaign = campaignService.updateCampaign(
                id,
                new CampaignService.CampaignData(campaignDTO.getName(), campaignDTO.getDescription(), campaignDTO.getLoreId(), campaignDTO.getGameSystemId())
        );
        return ResponseEntity.ok(campaignMapper.toDTO(updatedCampaign));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCampaign(@PathVariable String id) {
        campaignService.deleteCampaign(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Récapitulatif des entités qui seront supprimées en cascade : utilisé par
     * l'UI pour afficher "X arcs, Y chapitres, Z scènes..." dans la confirmation.
     */
    @GetMapping("/{id}/deletion-impact")
    public ResponseEntity<CampaignService.DeletionImpact> getDeletionImpact(@PathVariable String id) {
        if (!campaignService.campaignExists(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(campaignService.getDeletionImpact(id));
    }
}
