package com.loremind.infrastructure.web.controller;

import com.loremind.application.campaigncontext.CampaignReadinessAssessment;
import com.loremind.application.campaigncontext.CampaignReadinessService;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller du Pilier B (« guidage / readiness »). Expose le bilan de
 * préparation d'une campagne : statut agrégé + liste des manques cliquables.
 *
 * <p>Read-model pur, toujours 200 quand la campagne existe (même DRAFT / gaps
 * critiques : le guidage informe, il ne bloque rien). 404 si la campagne est inconnue.</p>
 */
@RestController
@RequestMapping("/api/campaigns/{campaignId}/readiness")
public class CampaignReadinessController {

    private final CampaignReadinessService readinessService;
    private final CampaignRepository campaignRepository;

    public CampaignReadinessController(CampaignReadinessService readinessService,
                                       CampaignRepository campaignRepository) {
        this.readinessService = readinessService;
        this.campaignRepository = campaignRepository;
    }

    @GetMapping
    public ResponseEntity<CampaignReadinessAssessment> getReadiness(@PathVariable String campaignId) {
        if (!campaignRepository.existsById(campaignId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(readinessService.assess(campaignId));
    }
}
