package com.loremind.infrastructure.web.controller;

import com.loremind.domain.campaigncontext.Arc;
import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.Chapter;
import com.loremind.domain.campaigncontext.Prerequisite;
import com.loremind.domain.campaigncontext.ports.ArcRepository;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.campaigncontext.ports.ChapterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'intégration de {@link CampaignFlagController}.
 * Unique endpoint : list (GET) qui déduplique les noms de faits (Prerequisite.FlagSet)
 * référencés dans les prérequis des chapitres de la campagne.
 * Fixtures : campaign -> arc -> chapters avec prérequis FlagSet.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CampaignFlagControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private ArcRepository arcRepository;
    @Autowired private ChapterRepository chapterRepository;

    private String campaignId;
    private String arcId;

    @BeforeEach
    void setUp() {
        // Chaîne de fixtures : campaign -> arc
        campaignId = campaignRepository.save(Campaign.builder().name("C").description("").build()).getId();
        arcId = arcRepository.save(Arc.builder().campaignId(campaignId).name("A").order(0).build()).getId();
    }

    @Test
    void list_returnsEmptyArray_whenNoFlagPrerequisites() throws Exception {
        // Chapitre sans prérequis FlagSet => aucun fait référencé
        chapterRepository.save(Chapter.builder().arcId(arcId).name("Ch").order(0).build());
        mockMvc.perform(get("/api/campaigns/{cid}/flags", campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void list_returnsDeduplicatedSortedFlagNames() throws Exception {
        // Deux chapitres référençant des FlagSet, avec un doublon "alpha"
        chapterRepository.save(Chapter.builder().arcId(arcId).name("Ch1").order(0)
                .prerequisites(List.of(
                        new Prerequisite.FlagSet("zeta"),
                        new Prerequisite.FlagSet("alpha")))
                .build());
        chapterRepository.save(Chapter.builder().arcId(arcId).name("Ch2").order(1)
                .prerequisites(List.of(
                        new Prerequisite.FlagSet("alpha"),       // doublon
                        new Prerequisite.QuestCompleted("q-1"))) // ignoré (pas un FlagSet)
                .build());

        mockMvc.perform(get("/api/campaigns/{cid}/flags", campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                // TreeSet => tri alphabétique : alpha avant zeta
                .andExpect(jsonPath("$[0]").value("alpha"))
                .andExpect(jsonPath("$[1]").value("zeta"));
    }

    @Test
    void list_returnsEmptyArray_forUnknownCampaign() throws Exception {
        // Campagne sans arcs => liste vide (pas d'erreur)
        mockMvc.perform(get("/api/campaigns/{cid}/flags", "999999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
