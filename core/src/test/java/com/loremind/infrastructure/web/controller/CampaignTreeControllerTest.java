package com.loremind.infrastructure.web.controller;

import com.loremind.domain.campaigncontext.Arc;
import com.loremind.domain.campaigncontext.ArcType;
import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.Chapter;
import com.loremind.domain.campaigncontext.Quest;
import com.loremind.domain.campaigncontext.Scene;
import com.loremind.domain.campaigncontext.ports.ArcRepository;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.campaigncontext.ports.ChapterRepository;
import com.loremind.domain.campaigncontext.ports.QuestRepository;
import com.loremind.domain.campaigncontext.ports.SceneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test d'intégration de l'endpoint AGRÉGÉ de l'arbre de campagne : structure complète
 * (arcs → chapitres → scènes, quêtes, readiness) en une seule requête.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CampaignTreeControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private ArcRepository arcRepository;
    @Autowired private ChapterRepository chapterRepository;
    @Autowired private SceneRepository sceneRepository;
    @Autowired private QuestRepository questRepository;

    private String campaignId;
    private String arcId;
    private String chapterId;

    @BeforeEach
    void setUp() {
        campaignId = campaignRepository.save(Campaign.builder().name("C").description("").build()).getId();
        arcId = arcRepository.save(Arc.builder().campaignId(campaignId).name("Arc")
                .type(ArcType.HUB).order(0).build()).getId();
        chapterId = chapterRepository.save(Chapter.builder().arcId(arcId).name("Chap").order(0).build()).getId();
        sceneRepository.save(Scene.builder().chapterId(chapterId).name("Scène").order(0).build());
        questRepository.save(Quest.builder().campaignId(campaignId).arcId(arcId).name("Quête").order(0).build());
    }

    @Test
    void tree_returnsFullAggregate_inOneCall() throws Exception {
        mockMvc.perform(get("/api/campaigns/{id}/tree", campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arcs", hasSize(1)))
                .andExpect(jsonPath("$.arcs[0].type").value("HUB"))
                .andExpect(jsonPath("$.chaptersByArc." + arcId, hasSize(1)))
                .andExpect(jsonPath("$.scenesByChapter." + chapterId, hasSize(1)))
                .andExpect(jsonPath("$.scenesByChapter." + chapterId + "[0].name").value("Scène"))
                .andExpect(jsonPath("$.quests", hasSize(1)))
                .andExpect(jsonPath("$.quests[0].arcId").value(arcId))
                .andExpect(jsonPath("$.readiness.campaignId").value(campaignId))
                .andExpect(jsonPath("$.readiness.gaps").isArray());
    }

    @Test
    void tree_unknownCampaign_returns404() throws Exception {
        mockMvc.perform(get("/api/campaigns/999999/tree"))
                .andExpect(status().isNotFound());
    }
}
