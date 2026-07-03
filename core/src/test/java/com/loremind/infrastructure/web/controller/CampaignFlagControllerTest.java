package com.loremind.infrastructure.web.controller;

import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.Prerequisite;
import com.loremind.domain.campaigncontext.Quest;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.campaigncontext.ports.QuestRepository;
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
 * référencés dans les prérequis des QUÊTES de la campagne (Niveau 1).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CampaignFlagControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private QuestRepository questRepository;

    private String campaignId;

    @BeforeEach
    void setUp() {
        campaignId = campaignRepository.save(Campaign.builder().name("C").description("").build()).getId();
    }

    @Test
    void list_returnsEmptyArray_whenNoFlagPrerequisites() throws Exception {
        // Quête sans prérequis FlagSet => aucun fait référencé
        questRepository.save(Quest.builder().campaignId(campaignId).name("Q").order(0).build());
        mockMvc.perform(get("/api/campaigns/{cid}/flags", campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void list_returnsDeduplicatedSortedFlagNames() throws Exception {
        // Deux quêtes référençant des FlagSet, avec un doublon "alpha"
        questRepository.save(Quest.builder().campaignId(campaignId).name("Q1").order(0)
                .prerequisites(List.of(
                        new Prerequisite.FlagSet("zeta"),
                        new Prerequisite.FlagSet("alpha")))
                .build());
        questRepository.save(Quest.builder().campaignId(campaignId).name("Q2").order(1)
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
        // Campagne sans quêtes => liste vide (pas d'erreur)
        mockMvc.perform(get("/api/campaigns/{cid}/flags", "999999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
