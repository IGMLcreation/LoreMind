package com.loremind.infrastructure.web.controller;

import com.loremind.application.campaigncontext.CampaignReferencedFlagsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller des faits d'une Campagne.
 *
 * <p>Sémantique « déclaration implicite » : il n'y a pas de table de déclarations
 * globales. La liste retournée est la déduplication des noms de faits référencés
 * dans les prérequis des chapitres de la campagne.</p>
 */
@RestController
@RequestMapping("/api/campaigns/{campaignId}/flags")
public class CampaignFlagController {

    private final CampaignReferencedFlagsService service;

    public CampaignFlagController(CampaignReferencedFlagsService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<String>> list(@PathVariable String campaignId) {
        return ResponseEntity.ok(service.listForCampaign(campaignId));
    }
}
