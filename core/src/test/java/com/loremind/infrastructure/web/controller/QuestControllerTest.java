package com.loremind.infrastructure.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.quest.Quest;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.campaigncontext.ports.QuestRepository;
import com.loremind.domain.playcontext.Playthrough;
import com.loremind.domain.playcontext.ports.PlaythroughRepository;
import com.loremind.infrastructure.web.dto.campaigncontext.QuestDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class QuestControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private QuestRepository questRepository;
    @Autowired private PlaythroughRepository playthroughRepository;

    private String campaignId;
    private String playthroughId;

    @BeforeEach
    void setUp() {
        campaignId = campaignRepository.save(Campaign.builder().name("C").description("").build()).getId();
        playthroughId = playthroughRepository.save(
                Playthrough.builder().campaignId(campaignId).name("Table").description("").build()).getId();
    }

    private Quest seedQuest(String name, int order) {
        return questRepository.save(Quest.builder().campaignId(campaignId).name(name).order(order).build());
    }

    @Test
    void create_returns200_andUsesPathCampaignId() throws Exception {
        QuestDTO dto = new QuestDTO();
        dto.setName("Q1");
        dto.setOrder(0);
        mockMvc.perform(post("/api/campaigns/{campaignId}/quests", campaignId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Q1"))
                .andExpect(jsonPath("$.campaignId").value(campaignId));
    }

    @Test
    void getById_returns200() throws Exception {
        Quest saved = seedQuest("Q", 0);
        mockMvc.perform(get("/api/campaigns/{campaignId}/quests/{questId}", campaignId, saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Q"));
    }

    @Test
    void getById_returns404_whenMissing() throws Exception {
        mockMvc.perform(get("/api/campaigns/{campaignId}/quests/{questId}", campaignId, "999999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listByCampaign_returnsArray() throws Exception {
        seedQuest("Q", 0);
        mockMvc.perform(get("/api/campaigns/{campaignId}/quests", campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("Q"));
    }

    @Test
    void update_returns200() throws Exception {
        Quest saved = seedQuest("old", 0);
        QuestDTO dto = new QuestDTO();
        dto.setName("new");
        dto.setOrder(0);
        mockMvc.perform(put("/api/campaigns/{campaignId}/quests/{questId}", campaignId, saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("new"));
    }

    @Test
    void update_returns400_whenMissing() throws Exception {
        QuestDTO dto = new QuestDTO();
        dto.setName("new");
        dto.setOrder(0);
        mockMvc.perform(put("/api/campaigns/{campaignId}/quests/{questId}", campaignId, "999999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_returns204() throws Exception {
        Quest saved = seedQuest("X", 0);
        mockMvc.perform(delete("/api/campaigns/{campaignId}/quests/{questId}", campaignId, saved.getId()))
                .andExpect(status().isNoContent());
    }

    // ?playthroughId= : branche d'enrichissement du statut (playthrough réel, snapshot vide -> NOT_STARTED).
    @Test
    void getById_withPlaythroughId_enrichesStatus() throws Exception {
        Quest saved = seedQuest("Q", 0);
        mockMvc.perform(get("/api/campaigns/{campaignId}/quests/{questId}", campaignId, saved.getId())
                        .param("playthroughId", playthroughId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progressionStatus").value("NOT_STARTED"))
                .andExpect(jsonPath("$.effectiveStatus").value("AVAILABLE"));
    }

    @Test
    void listByCampaign_withPlaythroughId_enrichesStatus() throws Exception {
        seedQuest("Q", 0);
        mockMvc.perform(get("/api/campaigns/{campaignId}/quests", campaignId)
                        .param("playthroughId", playthroughId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].progressionStatus").value("NOT_STARTED"));
    }

    @Test
    void reorder_returns204() throws Exception {
        Quest q1 = seedQuest("Q1", 0);
        Quest q2 = seedQuest("Q2", 1);
        String body = objectMapper.writeValueAsString(Map.of("orderedIds", List.of(q2.getId(), q1.getId())));
        mockMvc.perform(put("/api/campaigns/{campaignId}/quests/reorder", campaignId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());
    }
}
