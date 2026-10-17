package com.loremind.infrastructure.web.controller;

import com.loremind.application.campaigncontext.GuidedSkeleton;
import com.loremind.application.campaigncontext.GuidedSkeletonService;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller des méthodes guidées (Mentor d'écriture, phase M3) : persiste le
 * squelette (arc → chapitre → scènes) construit question par question par le MJ.
 *
 * <p>201 + ids créés ; 400 si le squelette est invalide ({@code GlobalExceptionHandler}) ;
 * 404 si la campagne est inconnue.</p>
 */
@RestController
@RequestMapping("/api/campaigns/{campaignId}/guided-skeleton")
public class GuidedSkeletonController {

    private final GuidedSkeletonService guidedSkeletonService;
    private final CampaignRepository campaignRepository;

    public GuidedSkeletonController(GuidedSkeletonService guidedSkeletonService,
                                    CampaignRepository campaignRepository) {
        this.guidedSkeletonService = guidedSkeletonService;
        this.campaignRepository = campaignRepository;
    }

    @PostMapping
    public ResponseEntity<GuidedSkeleton.Result> create(@PathVariable String campaignId,
                                                        @RequestBody GuidedSkeleton skeleton) {
        if (!campaignRepository.existsById(campaignId)) {
            return ResponseEntity.notFound().build();
        }
        // Squelette invalide : IllegalArgumentException → 400 via GlobalExceptionHandler.
        return ResponseEntity.status(HttpStatus.CREATED).body(guidedSkeletonService.create(campaignId, skeleton));
    }
}
