package com.loremind.application.campaigncontext;

import com.loremind.application.generationcontext.CampaignStructuralContextBuilder;
import com.loremind.application.generationcontext.LoreStructuralContextBuilder;
import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.ports.CampaignPdfAdvisor;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.generationcontext.CampaignStructuralContext;
import com.loremind.domain.generationcontext.CampaignStructuralContext.ArcSummary;
import com.loremind.domain.generationcontext.CampaignStructuralContext.ChapterSummary;
import com.loremind.domain.generationcontext.CampaignStructuralContext.NpcSummary;
import com.loremind.domain.generationcontext.CampaignStructuralContext.SceneSummary;
import com.loremind.domain.generationcontext.LoreStructuralContext;
import com.loremind.domain.generationcontext.LoreStructuralContext.PageSummary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Service applicatif : conseils d'adaptation d'un PDF à une campagne existante.
 *
 * <p>Assemble un « brief » de la campagne (structure + PNJ + univers/lore) — la même
 * matière que le chat de campagne — et délègue la génération streamée au Brain via
 * {@link CampaignPdfAdvisor}. Ne persiste rien : la sortie est du conseil libre.</p>
 */
@Service
public class CampaignAdaptService {

    private final CampaignRepository campaignRepository;
    private final CampaignStructuralContextBuilder campaignContextBuilder;
    private final LoreStructuralContextBuilder loreContextBuilder;
    private final CampaignPdfAdvisor advisor;

    public CampaignAdaptService(
            CampaignRepository campaignRepository,
            CampaignStructuralContextBuilder campaignContextBuilder,
            LoreStructuralContextBuilder loreContextBuilder,
            CampaignPdfAdvisor advisor) {
        this.campaignRepository = campaignRepository;
        this.campaignContextBuilder = campaignContextBuilder;
        this.loreContextBuilder = loreContextBuilder;
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
                pdfBytes, filename, buildBrief(campaign), messagesJson, onToken, onComplete, onError);
    }

    /** Construit un résumé markdown de la campagne (structure + PNJ + lore). */
    private String buildBrief(Campaign campaign) {
        CampaignStructuralContext cc = campaignContextBuilder.build(campaign.getId());
        StringBuilder sb = new StringBuilder();

        sb.append("# Campagne : ").append(cc.campaignName()).append("\n");
        if (notBlank(cc.campaignDescription())) sb.append(cc.campaignDescription()).append("\n");

        sb.append("\n## Structure (arcs → chapitres → scènes)\n");
        if (cc.arcs().isEmpty()) {
            sb.append("_(aucun arc pour le moment)_\n");
        }
        for (ArcSummary arc : cc.arcs()) {
            sb.append("### Arc : ").append(arc.name());
            if (notBlank(arc.description())) sb.append(" — ").append(arc.description());
            sb.append("\n");
            for (ChapterSummary ch : arc.chapters()) {
                sb.append("- Chapitre : ").append(ch.name());
                if (notBlank(ch.description())) sb.append(" — ").append(ch.description());
                sb.append("\n");
                for (SceneSummary sc : ch.scenes()) {
                    sb.append("  - Scène : ").append(sc.name());
                    if (notBlank(sc.description())) sb.append(" — ").append(sc.description());
                    sb.append("\n");
                }
            }
        }

        if (!cc.npcs().isEmpty()) {
            sb.append("\n## PNJ existants\n");
            for (NpcSummary n : cc.npcs()) {
                sb.append("- ").append(n.name());
                if (notBlank(n.snippet())) sb.append(" : ").append(n.snippet());
                sb.append("\n");
            }
        }

        if (campaign.isLinkedToLore()) {
            loreContextBuilder.buildOptional(campaign.getLoreId()).ifPresent(lore -> appendLore(sb, lore));
        }
        return sb.toString();
    }

    private void appendLore(StringBuilder sb, LoreStructuralContext lore) {
        sb.append("\n## Univers (Lore) : ").append(lore.loreName()).append("\n");
        if (notBlank(lore.loreDescription())) sb.append(lore.loreDescription()).append("\n");
        for (Map.Entry<String, List<PageSummary>> entry : lore.folders().entrySet()) {
            sb.append("### ").append(entry.getKey()).append("\n");
            for (PageSummary page : entry.getValue()) {
                sb.append("- ").append(page.title()).append("\n");
            }
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
