package com.loremind.infrastructure.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.campaigncontext.Arc;
import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.ports.ArcRepository;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.infrastructure.web.dto.campaigncontext.ArcDTO;
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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ArcControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private ArcRepository arcRepository;

    private String campaignId;

    @BeforeEach
    void setUp() {
        campaignId = campaignRepository.save(Campaign.builder().name("C").description("").build()).getId();
    }

    @Test
    void create_returns200() throws Exception {
        ArcDTO dto = new ArcDTO();
        dto.setName("Acte I");
        dto.setDescription("Mise en place");
        dto.setCampaignId(campaignId);
        dto.setOrder(0);

        mockMvc.perform(post("/api/arcs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acte I"));
    }

    @Test
    void getById_returns200() throws Exception {
        Arc saved = arcRepository.save(Arc.builder().campaignId(campaignId).name("A").order(0).build());
        mockMvc.perform(get("/api/arcs/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("A"));
    }

    @Test
    void getById_returns404_whenMissing() throws Exception {
        mockMvc.perform(get("/api/arcs/{id}", "999999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAll_returnsArray() throws Exception {
        arcRepository.save(Arc.builder().campaignId(campaignId).name("A").order(0).build());
        mockMvc.perform(get("/api/arcs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getByCampaign_pathVariant() throws Exception {
        arcRepository.save(Arc.builder().campaignId(campaignId).name("A").order(0).build());
        mockMvc.perform(get("/api/arcs").param("campaignId", campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void update_returns200() throws Exception {
        Arc saved = arcRepository.save(Arc.builder().campaignId(campaignId).name("old").order(0).build());
        ArcDTO dto = new ArcDTO();
        dto.setName("new");
        dto.setCampaignId(campaignId);
        dto.setOrder(0);
        mockMvc.perform(put("/api/arcs/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("new"));
    }

    @Test
    void delete_returns204() throws Exception {
        Arc saved = arcRepository.save(Arc.builder().campaignId(campaignId).name("X").order(0).build());
        mockMvc.perform(delete("/api/arcs/{id}", saved.getId()))
                .andExpect(status().isNoContent());
    }
}
