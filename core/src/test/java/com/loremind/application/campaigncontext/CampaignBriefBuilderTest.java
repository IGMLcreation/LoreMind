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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Test unitaire pour CampaignBriefBuilder.
 * Mocks des deux builders de contexte structurel (campagne + lore).
 * Vérifie l'assemblage du markdown : titres, vocabulaire HUB/LINEAR, PNJ, lore conditionnel.
 */
@ExtendWith(MockitoExtension.class)
class CampaignBriefBuilderTest {

    @Mock
    private CampaignStructuralContextBuilder campaignContextBuilder;
    @Mock
    private LoreStructuralContextBuilder loreContextBuilder;

    @InjectMocks
    private CampaignBriefBuilder builder;

    private static CampaignStructuralContext emptyCtx(String name, String description) {
        return new CampaignStructuralContext(name, description, List.of(), List.of(), List.of());
    }

    @Test
    void testBuild_EmptyCampaign_HeaderAndNoArcsPlaceholder() {
        Campaign campaign = Campaign.builder().id("camp-1").build();
        when(campaignContextBuilder.build("camp-1")).thenReturn(emptyCtx("Ma Campagne", "Un synopsis"));

        String result = builder.build(campaign);

        assertTrue(result.contains("# Campagne : Ma Campagne"));
        assertTrue(result.contains("Un synopsis"));
        assertTrue(result.contains("## Structure (arcs → chapitres → scènes)"));
        assertTrue(result.contains("_(aucun arc pour le moment)_"));
        // Pas de PNJ ni de lore.
        assertFalse(result.contains("## PNJ existants"));
        assertFalse(result.contains("## Univers"));
    }

    @Test
    void testBuild_BlankDescriptionOmitted() {
        Campaign campaign = Campaign.builder().id("camp-1").build();
        when(campaignContextBuilder.build("camp-1")).thenReturn(emptyCtx("Camp", "   "));

        String result = builder.build(campaign);

        assertTrue(result.contains("# Campagne : Camp"));
        // La description blanche ne doit pas générer de ligne propre.
        assertFalse(result.contains("   \n"));
    }

    @Test
    void testBuild_HubArcUsesQuestVocabulary() {
        Campaign campaign = Campaign.builder().id("camp-1").build();
        SceneSummary scene = new SceneSummary("Scène A", "desc scène", 0, List.of(), List.of());
        ChapterSummary chapter = new ChapterSummary("Quête 1", "desc quête", 0, List.of(scene));
        ArcSummary hubArc = new ArcSummary("Arc Hub", "desc arc", true, 0, List.of(chapter));
        CampaignStructuralContext ctx = new CampaignStructuralContext(
                "Camp", null, List.of(hubArc), List.of(), List.of());
        when(campaignContextBuilder.build("camp-1")).thenReturn(ctx);

        String result = builder.build(campaign);

        assertTrue(result.contains("### Arc HUB (à quêtes) : Arc Hub — desc arc"));
        assertTrue(result.contains("- Quête : Quête 1 — desc quête"));
        assertTrue(result.contains("  - Scène : Scène A — desc scène"));
    }

    @Test
    void testBuild_LinearArcUsesChapterVocabulary() {
        Campaign campaign = Campaign.builder().id("camp-1").build();
        ChapterSummary chapter = new ChapterSummary("Chapitre 1", null, 0, List.of());
        ArcSummary linearArc = new ArcSummary("Arc Linéaire", null, false, 0, List.of(chapter));
        CampaignStructuralContext ctx = new CampaignStructuralContext(
                "Camp", null, List.of(linearArc), List.of(), List.of());
        when(campaignContextBuilder.build("camp-1")).thenReturn(ctx);

        String result = builder.build(campaign);

        assertTrue(result.contains("### Arc : Arc Linéaire"));
        assertTrue(result.contains("- Chapitre : Chapitre 1"));
        // La légende mentionne toujours « HUB » ; ce qui compte est que l'arc LINEAR
        // n'emploie PAS le vocabulaire HUB (en-tête « ### Arc HUB » ni « Quête »).
        assertFalse(result.contains("### Arc HUB"));
        assertFalse(result.contains("- Quête :"));
    }

    @Test
    void testBuild_IncludesNpcsWhenPresent() {
        Campaign campaign = Campaign.builder().id("camp-1").build();
        CampaignStructuralContext ctx = new CampaignStructuralContext(
                "Camp", null, List.of(),
                List.of(),
                List.of(new NpcSummary("Gandalf", "magicien gris"),
                        new NpcSummary("Sauron", null)));
        when(campaignContextBuilder.build("camp-1")).thenReturn(ctx);

        String result = builder.build(campaign);

        assertTrue(result.contains("## PNJ existants"));
        assertTrue(result.contains("- Gandalf : magicien gris"));
        assertTrue(result.contains("- Sauron"));
    }

    @Test
    void testBuild_AppendsLoreWhenLinked() {
        Campaign campaign = Campaign.builder().id("camp-1").loreId("lore-1").build();
        when(campaignContextBuilder.build("camp-1")).thenReturn(emptyCtx("Camp", null));

        Map<String, List<PageSummary>> folders = new LinkedHashMap<>();
        folders.put("Lieux", List.of(
                new PageSummary("La Comté", "tpl", Map.of(), List.of(), List.of())));
        LoreStructuralContext lore = new LoreStructuralContext(
                "Terre du Milieu", "Un vaste monde", folders, List.of());
        when(loreContextBuilder.buildOptional("lore-1")).thenReturn(Optional.of(lore));

        String result = builder.build(campaign);

        assertTrue(result.contains("## Univers (Lore) : Terre du Milieu"));
        assertTrue(result.contains("Un vaste monde"));
        assertTrue(result.contains("### Lieux"));
        assertTrue(result.contains("- La Comté"));
    }

    @Test
    void testBuild_NotLinkedToLore_SkipsLoreBuilder() {
        Campaign campaign = Campaign.builder().id("camp-1").build(); // loreId null
        when(campaignContextBuilder.build("camp-1")).thenReturn(emptyCtx("Camp", null));

        String result = builder.build(campaign);

        assertFalse(result.contains("## Univers"));
        verify(loreContextBuilder, never()).buildOptional(anyString());
    }

    @Test
    void testBuild_LinkedButLoreAbsent_NoLoreSection() {
        Campaign campaign = Campaign.builder().id("camp-1").loreId("lore-1").build();
        when(campaignContextBuilder.build("camp-1")).thenReturn(emptyCtx("Camp", null));
        when(loreContextBuilder.buildOptional("lore-1")).thenReturn(Optional.empty());

        String result = builder.build(campaign);

        assertFalse(result.contains("## Univers"));
    }
}
