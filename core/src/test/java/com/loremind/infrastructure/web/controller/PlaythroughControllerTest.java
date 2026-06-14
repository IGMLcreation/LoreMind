package com.loremind.infrastructure.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.playcontext.Playthrough;
import com.loremind.domain.playcontext.Session;
import com.loremind.domain.playcontext.ports.PlaythroughRepository;
import com.loremind.domain.playcontext.ports.SessionRepository;
import com.loremind.infrastructure.web.dto.playcontext.PlaythroughDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'intégration du PlaythroughController.
 * Fixture parente : Campaign (un Playthrough référence une campagne).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlaythroughControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private PlaythroughRepository playthroughRepository;
    @Autowired private SessionRepository sessionRepository;

    private String campaignId;

    @BeforeEach
    void setUp() {
        campaignId = campaignRepository.save(Campaign.builder().name("C").description("").build()).getId();
    }

    private Playthrough savePlaythrough() {
        return playthroughRepository.save(
                Playthrough.builder().campaignId(campaignId).name("Partie").description("d").build());
    }

    @Test
    void create_returns200() throws Exception {
        PlaythroughDTO dto = new PlaythroughDTO();
        dto.setCampaignId(campaignId);
        dto.setName("Table du vendredi");
        dto.setDescription("desc");
        mockMvc.perform(post("/api/playthroughs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Table du vendredi"));
    }

    @Test
    void create_returns400_whenCampaignMissing() throws Exception {
        PlaythroughDTO dto = new PlaythroughDTO();
        dto.setCampaignId("999999999");
        dto.setName("X");
        mockMvc.perform(post("/api/playthroughs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getById_returns200() throws Exception {
        Playthrough p = savePlaythrough();
        mockMvc.perform(get("/api/playthroughs/{id}", p.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Partie"));
    }

    @Test
    void getById_returns404_whenMissing() throws Exception {
        mockMvc.perform(get("/api/playthroughs/{id}", "999999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_byCampaign_returnsArray() throws Exception {
        savePlaythrough();
        mockMvc.perform(get("/api/playthroughs").param("campaignId", campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void list_withoutCampaign_returnsEmptyArray() throws Exception {
        savePlaythrough();
        mockMvc.perform(get("/api/playthroughs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void update_returns200() throws Exception {
        Playthrough p = savePlaythrough();
        PlaythroughDTO dto = new PlaythroughDTO();
        dto.setName("Renommée");
        dto.setDescription("nouvelle desc");
        mockMvc.perform(put("/api/playthroughs/{id}", p.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renommée"));
    }

    @Test
    void update_returns400_whenMissing() throws Exception {
        PlaythroughDTO dto = new PlaythroughDTO();
        dto.setName("X");
        mockMvc.perform(put("/api/playthroughs/{id}", "999999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_returns204() throws Exception {
        Playthrough p = savePlaythrough();
        mockMvc.perform(delete("/api/playthroughs/{id}", p.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_returns400_whenMissing() throws Exception {
        mockMvc.perform(delete("/api/playthroughs/{id}", "999999999"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletionImpact_returns200() throws Exception {
        Playthrough p = savePlaythrough();
        sessionRepository.save(Session.builder()
                .name("S").playthroughId(p.getId())
                .startedAt(java.time.LocalDateTime.now()).build());
        mockMvc.perform(get("/api/playthroughs/{id}/deletion-impact", p.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions").value(1));
    }

    @Test
    void deletionImpact_returns404_whenMissing() throws Exception {
        mockMvc.perform(get("/api/playthroughs/{id}/deletion-impact", "999999999"))
                .andExpect(status().isNotFound());
    }
}
