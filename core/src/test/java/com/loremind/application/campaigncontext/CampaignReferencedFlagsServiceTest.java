package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.quest.Prerequisite;
import com.loremind.domain.campaigncontext.quest.Quest;
import com.loremind.domain.campaigncontext.ports.QuestRepository;
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
 * Mock du port QuestRepository.
 * Vérifie l'énumération dédupliquée + triée des FlagSet référencés par les quêtes.
 */
@ExtendWith(MockitoExtension.class)
public class CampaignReferencedFlagsServiceTest {

    @Mock
    private QuestRepository questRepository;

    @InjectMocks
    private CampaignReferencedFlagsService service;

    private static Quest questWithPrereqs(String id, Prerequisite... prereqs) {
        return Quest.builder()
                .id(id)
                .campaignId("camp-1")
                .name("Q-" + id)
                .prerequisites(List.of(prereqs))
                .build();
    }

    @Test
    void testListForCampaign_DeduplicatedAndSorted() {
        Quest q1 = questWithPrereqs("q1",
                new Prerequisite.FlagSet("zeta"),
                new Prerequisite.FlagSet("alpha"));
        Quest q2 = questWithPrereqs("q2",
                new Prerequisite.FlagSet("alpha"), // doublon -> dédupliqué
                new Prerequisite.FlagSet("beta"));
        when(questRepository.findByCampaignId("camp-1")).thenReturn(List.of(q1, q2));

        List<String> result = service.listForCampaign("camp-1");

        assertEquals(List.of("alpha", "beta", "zeta"), result);
    }

    @Test
    void testListForCampaign_IgnoresNonFlagSetAndBlankNames() {
        Quest quest = questWithPrereqs("q1",
                new Prerequisite.QuestCompleted("quest-x"),  // pas un FlagSet -> ignoré
                new Prerequisite.SessionReached(3),          // pas un FlagSet -> ignoré
                new Prerequisite.FlagSet(""),                // blanc -> ignoré
                new Prerequisite.FlagSet("   "),             // blanc -> ignoré
                new Prerequisite.FlagSet("real"));
        when(questRepository.findByCampaignId("camp-1")).thenReturn(List.of(quest));

        List<String> result = service.listForCampaign("camp-1");

        assertEquals(List.of("real"), result);
    }

    @Test
    void testListForCampaign_NullPrerequisitesSkipped() {
        Quest quest = Quest.builder().id("q1").campaignId("camp-1").name("Q").prerequisites(null).build();
        when(questRepository.findByCampaignId("camp-1")).thenReturn(List.of(quest));

        List<String> result = service.listForCampaign("camp-1");

        assertTrue(result.isEmpty());
    }

    @Test
    void testListForCampaign_NoQuests() {
        when(questRepository.findByCampaignId("camp-1")).thenReturn(List.of());

        List<String> result = service.listForCampaign("camp-1");

        assertTrue(result.isEmpty());
    }
}
