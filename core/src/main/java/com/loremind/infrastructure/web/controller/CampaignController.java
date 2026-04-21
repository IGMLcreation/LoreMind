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
                new CampaignService.CampaignData(campaign.getName(), campaign.getDescription(), campaign.getLoreId())
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
                new CampaignService.CampaignData(campaignDTO.getName(), campaignDTO.getDescription(), campaignDTO.getLoreId())
        );
        return ResponseEntity.ok(campaignMapper.toDTO(updatedCampaign));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCampaign(@PathVariable String id) {
        campaignService.deleteCampaign(id);
        return ResponseEntity.noContent().build();
    }
}
