package com.loremind.infrastructure.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.Npc;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.campaigncontext.ports.NpcRepository;
import com.loremind.infrastructure.web.dto.campaigncontext.NpcDTO;
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

/** Tests d'intégration CRUD du NpcController. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NpcControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private NpcRepository npcRepository;

    private String campaignId;

    @BeforeEach
    void setUp() {
        campaignId = campaignRepository.save(Campaign.builder().name("C").description("").build()).getId();
    }

    @Test
    void create_returns200() throws Exception {
        NpcDTO dto = new NpcDTO();
        dto.setName("Gandalf");
        dto.setCampaignId(campaignId);
        mockMvc.perform(post("/api/npcs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Gandalf"));
    }

    @Test
    void getById_returns200() throws Exception {
        Npc saved = npcRepository.save(Npc.builder().campaignId(campaignId).name("N").order(0).build());
        mockMvc.perform(get("/api/npcs/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("N"));
    }

    @Test
    void getById_returns404_whenMissing() throws Exception {
        mockMvc.perform(get("/api/npcs/{id}", "999999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByCampaign_returnsArray() throws Exception {
        npcRepository.save(Npc.builder().campaignId(campaignId).name("A").order(0).build());
        mockMvc.perform(get("/api/npcs/campaign/{campaignId}", campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void search_returnsArray() throws Exception {
        npcRepository.save(Npc.builder().campaignId(campaignId).name("Frodon").order(0).build());
        mockMvc.perform(get("/api/npcs/search").param("q", "Frodon"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void update_returns200() throws Exception {
        Npc saved = npcRepository.save(Npc.builder().campaignId(campaignId).name("old").order(0).build());
        NpcDTO dto = new NpcDTO();
        dto.setName("new");
        dto.setCampaignId(campaignId);
        dto.setOrder(0);
        mockMvc.perform(put("/api/npcs/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("new"));
    }

    @Test
    void update_returns400_whenMissing() throws Exception {
        NpcDTO dto = new NpcDTO();
        dto.setName("x");
        dto.setCampaignId(campaignId);
        dto.setOrder(0);
        mockMvc.perform(put("/api/npcs/{id}", "999999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_returns204() throws Exception {
        Npc saved = npcRepository.save(Npc.builder().campaignId(campaignId).name("X").order(0).build());
        mockMvc.perform(delete("/api/npcs/{id}", saved.getId()))
                .andExpect(status().isNoContent());
    }
}
