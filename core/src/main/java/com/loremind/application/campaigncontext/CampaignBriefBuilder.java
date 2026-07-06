package com.loremind.application.campaigncontext;

import com.loremind.application.generationcontext.CampaignStructuralContextBuilder;
import com.loremind.application.generationcontext.LoreStructuralContextBuilder;
import com.loremind.domain.campaigncontext.Campaign;
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

/**
 * Construit un résumé markdown d'une campagne (structure arcs→chapitres→scènes +
 * PNJ + univers/lore). Partagé par les fonctions IA qui doivent « voir » la
 * campagne : conseils d'adaptation PDF (CampaignAdaptService) et ateliers RAG
 * (NotebookService). Centralisé ici pour une seule source de vérité.
 */
@Service
public class CampaignBriefBuilder {

    private final CampaignStructuralContextBuilder campaignContextBuilder;
    private final LoreStructuralContextBuilder loreContextBuilder;

    public CampaignBriefBuilder(
            CampaignStructuralContextBuilder campaignContextBuilder,
            LoreStructuralContextBuilder loreContextBuilder) {
        this.campaignContextBuilder = campaignContextBuilder;
        this.loreContextBuilder = loreContextBuilder;
    }

    public String build(Campaign campaign) {
        CampaignStructuralContext cc = campaignContextBuilder.build(campaign.getId());
        StringBuilder sb = new StringBuilder();

        sb.append("# Campagne : ").append(cc.campaignName()).append("\n");
        if (notBlank(cc.campaignDescription())) sb.append(cc.campaignDescription()).append("\n");

        appendStructure(sb, cc);
        appendNpcs(sb, cc);

        if (campaign.isLinkedToLore()) {
            loreContextBuilder.buildOptional(campaign.getLoreId()).ifPresent(lore -> appendLore(sb, lore));
        }
        return sb.toString();
    }

    private void appendStructure(StringBuilder sb, CampaignStructuralContext cc) {
        sb.append("\n## Structure (arcs → chapitres → scènes)\n");
        sb.append("_Un arc HUB contient des chapitres parallèles appelés « quêtes » ; ")
                .append("un arc LINEAR contient des chapitres en séquence._\n");
        if (cc.arcs().isEmpty()) {
            sb.append("_(aucun arc pour le moment)_\n");
        }
        for (ArcSummary arc : cc.arcs()) {
            appendArc(sb, arc);
        }
    }

    private void appendArc(StringBuilder sb, ArcSummary arc) {
        sb.append(arc.hub() ? "### Arc HUB (à quêtes) : " : "### Arc : ").append(arc.name());
        if (notBlank(arc.description())) sb.append(" — ").append(arc.description());
        sb.append("\n");
        for (ChapterSummary ch : arc.chapters()) {
            appendChapter(sb, arc.hub(), ch);
        }
    }

    private void appendChapter(StringBuilder sb, boolean hub, ChapterSummary ch) {
        sb.append(hub ? "- Quête : " : "- Chapitre : ").append(ch.name());
        if (notBlank(ch.description())) sb.append(" — ").append(ch.description());
        sb.append("\n");
        for (SceneSummary sc : ch.scenes()) {
            sb.append("  - Scène : ").append(sc.name());
            if (notBlank(sc.description())) sb.append(" — ").append(sc.description());
            sb.append("\n");
        }
    }

    private void appendNpcs(StringBuilder sb, CampaignStructuralContext cc) {
        if (cc.npcs().isEmpty()) return;
        sb.append("\n## PNJ existants\n");
        for (NpcSummary n : cc.npcs()) {
            sb.append("- ").append(n.name());
            if (notBlank(n.snippet())) sb.append(" : ").append(n.snippet());
            sb.append("\n");
        }
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
