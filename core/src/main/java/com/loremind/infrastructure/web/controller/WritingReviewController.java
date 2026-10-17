package com.loremind.infrastructure.web.controller;

import com.loremind.application.campaigncontext.WritingReviewReport;
import com.loremind.application.campaigncontext.WritingReviewService;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller de la relecture d'éditeur (Mentor d'écriture, phase M2) : conseils
 * d'auteur sur la qualité de l'écriture d'une campagne.
 *
 * <p>Read-model pur, toujours 200 quand la campagne existe (les remarques conseillent,
 * elles ne bloquent rien). 404 si la campagne est inconnue.</p>
 */
@RestController
@RequestMapping("/api/campaigns/{campaignId}/writing-review")
public class WritingReviewController {

    private final WritingReviewService writingReviewService;
    private final CampaignRepository campaignRepository;

    public WritingReviewController(WritingReviewService writingReviewService,
                                   CampaignRepository campaignRepository) {
        this.writingReviewService = writingReviewService;
        this.campaignRepository = campaignRepository;
    }

    @GetMapping
    public ResponseEntity<WritingReviewReport> getWritingReview(@PathVariable String campaignId) {
        if (!campaignRepository.existsById(campaignId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(writingReviewService.review(campaignId));
    }
}
