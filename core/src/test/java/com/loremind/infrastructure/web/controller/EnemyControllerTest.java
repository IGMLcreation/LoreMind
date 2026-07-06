package com.loremind.infrastructure.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.bestiary.Enemy;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.campaigncontext.ports.EnemyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tests d'intégration CRUD du EnemyController (bestiaire de campagne). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EnemyControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private EnemyRepository enemyRepository;

    private String campaignId;

    @BeforeEach
    void setUp() {
        campaignId = campaignRepository.save(Campaign.builder().name("C").description("").build()).getId();
    }

    /** Requête = record EnemyRequest(name, level, folder, ..., campaignId, order). */
    @Test
    void create_returns200() throws Exception {
        EnemyController.EnemyRequest req = new EnemyController.EnemyRequest(
                "Gobelin", "FP 1", "Humanoïdes", null, null,
                Map.of(), Map.of(), Map.of(), campaignId, null);
        mockMvc.perform(post("/api/enemies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Gobelin"));
    }

    @Test
    void getById_returns200() throws Exception {
        Enemy saved = enemyRepository.save(Enemy.builder().campaignId(campaignId).name("E").order(0).build());
        mockMvc.perform(get("/api/enemies/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("E"));
    }

    @Test
    void getById_returns404_whenMissing() throws Exception {
        mockMvc.perform(get("/api/enemies/{id}", "999999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByCampaign_returnsArray() throws Exception {
        enemyRepository.save(Enemy.builder().campaignId(campaignId).name("A").order(0).build());
        mockMvc.perform(get("/api/enemies/campaign/{campaignId}", campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void search_returnsArray() throws Exception {
        enemyRepository.save(Enemy.builder().campaignId(campaignId).name("Dragon").order(0).build());
        mockMvc.perform(get("/api/enemies/search").param("q", "Dragon"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void update_returns200() throws Exception {
        Enemy saved = enemyRepository.save(Enemy.builder().campaignId(campaignId).name("old").order(0).build());
        EnemyController.EnemyRequest req = new EnemyController.EnemyRequest(
                "new", null, null, null, null,
                Map.of(), Map.of(), Map.of(), campaignId, 0);
        mockMvc.perform(put("/api/enemies/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("new"));
    }

    @Test
    void update_returns400_whenMissing() throws Exception {
        EnemyController.EnemyRequest req = new EnemyController.EnemyRequest(
                "x", null, null, null, null,
                Map.of(), Map.of(), Map.of(), campaignId, 0);
        mockMvc.perform(put("/api/enemies/{id}", "999999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_returns204() throws Exception {
        Enemy saved = enemyRepository.save(Enemy.builder().campaignId(campaignId).name("X").order(0).build());
        mockMvc.perform(delete("/api/enemies/{id}", saved.getId()))
                .andExpect(status().isNoContent());
    }
}
