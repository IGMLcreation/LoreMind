package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.Arc;
import com.loremind.domain.campaigncontext.Chapter;
import com.loremind.domain.campaigncontext.Prerequisite;
import com.loremind.domain.campaigncontext.ports.ArcRepository;
import com.loremind.domain.campaigncontext.ports.ChapterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test unitaire pour CampaignReferencedFlagsService.
 * Mocks des ports ArcRepository / ChapterRepository.
 * Vérifie l'énumération dédupliquée + triée des FlagSet référencés par les chapitres.
 */
@ExtendWith(MockitoExtension.class)
public class CampaignReferencedFlagsServiceTest {

    @Mock
    private ArcRepository arcRepository;
    @Mock
    private ChapterRepository chapterRepository;

    @InjectMocks
    private CampaignReferencedFlagsService service;

    private static Chapter chapterWithPrereqs(String id, String arcId, Prerequisite... prereqs) {
        return Chapter.builder()
                .id(id)
                .arcId(arcId)
                .name("C-" + id)
                .prerequisites(List.of(prereqs))
                .build();
    }

    @Test
    void testListForCampaign_DeduplicatedAndSorted() {
        // Arrange : deux arcs, plusieurs chapitres, flags dont certains en doublon.
        Arc arc1 = Arc.builder().id("arc-1").campaignId("camp-1").build();
        Arc arc2 = Arc.builder().id("arc-2").campaignId("camp-1").build();
        when(arcRepository.findByCampaignId("camp-1")).thenReturn(List.of(arc1, arc2));

        Chapter c1 = chapterWithPrereqs("c1", "arc-1",
                new Prerequisite.FlagSet("zeta"),
                new Prerequisite.FlagSet("alpha"));
        Chapter c2 = chapterWithPrereqs("c2", "arc-2",
                new Prerequisite.FlagSet("alpha"), // doublon -> dédupliqué
                new Prerequisite.FlagSet("beta"));
        when(chapterRepository.findByArcId("arc-1")).thenReturn(List.of(c1));
        when(chapterRepository.findByArcId("arc-2")).thenReturn(List.of(c2));

        // Act
        List<String> result = service.listForCampaign("camp-1");

        // Assert : tri alphabétique + déduplication.
        assertEquals(List.of("alpha", "beta", "zeta"), result);
    }

    @Test
    void testListForCampaign_IgnoresNonFlagSetAndBlankNames() {
        Arc arc = Arc.builder().id("arc-1").campaignId("camp-1").build();
        when(arcRepository.findByCampaignId("camp-1")).thenReturn(List.of(arc));

        Chapter chapter = chapterWithPrereqs("c1", "arc-1",
                new Prerequisite.QuestCompleted("quest-x"),  // pas un FlagSet -> ignoré
                new Prerequisite.SessionReached(3),          // pas un FlagSet -> ignoré
                new Prerequisite.FlagSet(""),                // blanc -> ignoré
                new Prerequisite.FlagSet("   "),             // blanc -> ignoré
                new Prerequisite.FlagSet("real"));
        when(chapterRepository.findByArcId("arc-1")).thenReturn(List.of(chapter));

        List<String> result = service.listForCampaign("camp-1");

        assertEquals(List.of("real"), result);
    }

    @Test
    void testListForCampaign_NullPrerequisitesSkipped() {
        Arc arc = Arc.builder().id("arc-1").campaignId("camp-1").build();
        when(arcRepository.findByCampaignId("camp-1")).thenReturn(List.of(arc));

        Chapter chapter = Chapter.builder().id("c1").arcId("arc-1").name("C").prerequisites(null).build();
        when(chapterRepository.findByArcId("arc-1")).thenReturn(List.of(chapter));

        List<String> result = service.listForCampaign("camp-1");

        assertTrue(result.isEmpty());
    }

    @Test
    void testListForCampaign_NoArcs() {
        when(arcRepository.findByCampaignId("camp-1")).thenReturn(List.of());

        List<String> result = service.listForCampaign("camp-1");

        assertTrue(result.isEmpty());
        verify(chapterRepository, never()).findByArcId(anyString());
    }
}
