package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.ports.CampaignPdfAdvisor;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * Service applicatif : conseils d'adaptation d'un PDF à une campagne existante.
 *
 * <p>Assemble un « brief » de la campagne (structure + PNJ + univers/lore) via
 * {@link CampaignBriefBuilder} et délègue la génération streamée au Brain via
 * {@link CampaignPdfAdvisor}. Ne persiste rien : la sortie est du conseil libre.</p>
 */
@Service
public class CampaignAdaptService {

    private final CampaignRepository campaignRepository;
    private final CampaignBriefBuilder briefBuilder;
    private final CampaignPdfAdvisor advisor;

    public CampaignAdaptService(
            CampaignRepository campaignRepository,
            CampaignBriefBuilder briefBuilder,
            CampaignPdfAdvisor advisor) {
        this.campaignRepository = campaignRepository;
        this.briefBuilder = briefBuilder;
        this.advisor = advisor;
    }

    public void adviseStreaming(
            String campaignId,
            byte[] pdfBytes,
            String filename,
            String messagesJson,
            Consumer<String> onToken,
            Runnable onComplete,
            Consumer<Throwable> onError) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campagne introuvable : " + campaignId));
        advisor.adviseStreaming(
                pdfBytes, filename, briefBuilder.build(campaign), messagesJson, onToken, onComplete, onError);
    }
}
