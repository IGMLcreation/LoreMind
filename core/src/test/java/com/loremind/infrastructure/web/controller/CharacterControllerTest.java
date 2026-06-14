package com.loremind.infrastructure.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.Character;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.campaigncontext.ports.CharacterRepository;
import com.loremind.domain.playcontext.Playthrough;
import com.loremind.domain.playcontext.ports.PlaythroughRepository;
import com.loremind.infrastructure.web.dto.campaigncontext.CharacterDTO;
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

/** Tests d'intégration CRUD du CharacterController (PJ liés à un Playthrough). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CharacterControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private PlaythroughRepository playthroughRepository;
    @Autowired private CharacterRepository characterRepository;

    private String playthroughId;

    @BeforeEach
    void setUp() {
        String campId = campaignRepository.save(Campaign.builder().name("C").description("").build()).getId();
        playthroughId = playthroughRepository.save(
                Playthrough.builder().campaignId(campId).name("Table").build()).getId();
    }

    @Test
    void create_returns200() throws Exception {
        CharacterDTO dto = new CharacterDTO();
        dto.setName("Aragorn");
        dto.setPlaythroughId(playthroughId);
        mockMvc.perform(post("/api/characters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Aragorn"));
    }

    @Test
    void getById_returns200() throws Exception {
        Character saved = characterRepository.save(
                Character.builder().playthroughId(playthroughId).name("PJ").order(0).build());
        mockMvc.perform(get("/api/characters/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("PJ"));
    }

    @Test
    void getById_returns404_whenMissing() throws Exception {
        mockMvc.perform(get("/api/characters/{id}", "999999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByPlaythrough_returnsArray() throws Exception {
        characterRepository.save(Character.builder().playthroughId(playthroughId).name("A").order(0).build());
        mockMvc.perform(get("/api/characters/playthrough/{playthroughId}", playthroughId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    /** Recherche enrichie : le campaignId est résolu via le Playthrough. */
    @Test
    void search_returnsEnrichedResult() throws Exception {
        characterRepository.save(Character.builder().playthroughId(playthroughId).name("Legolas").order(0).build());
        mockMvc.perform(get("/api/characters/search").param("q", "Legolas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("Legolas"))
                .andExpect(jsonPath("$[0].campaignId").exists());
    }

    @Test
    void update_returns200() throws Exception {
        Character saved = characterRepository.save(
                Character.builder().playthroughId(playthroughId).name("old").order(0).build());
        CharacterDTO dto = new CharacterDTO();
        dto.setName("new");
        dto.setPlaythroughId(playthroughId);
        dto.setOrder(0);
        mockMvc.perform(put("/api/characters/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("new"));
    }

    @Test
    void update_returns400_whenMissing() throws Exception {
        CharacterDTO dto = new CharacterDTO();
        dto.setName("x");
        dto.setPlaythroughId(playthroughId);
        dto.setOrder(0);
        mockMvc.perform(put("/api/characters/{id}", "999999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_returns204() throws Exception {
        Character saved = characterRepository.save(
                Character.builder().playthroughId(playthroughId).name("X").order(0).build());
        mockMvc.perform(delete("/api/characters/{id}", saved.getId()))
                .andExpect(status().isNoContent());
    }
}
