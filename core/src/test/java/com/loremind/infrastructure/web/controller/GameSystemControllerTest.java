package com.loremind.infrastructure.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.infrastructure.web.dto.gamesystemcontext.GameSystemDTO;
import com.loremind.infrastructure.web.dto.shared.TemplateFieldDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'integration du GameSystemController centres sur la persistance
 * des templates PJ/PNJ via l'API REST. Le CRUD de base est suppose stable.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GameSystemControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void create_persistsCharacterAndNpcTemplates() throws Exception {
        GameSystemDTO dto = new GameSystemDTO();
        dto.setName("Nimble Test");
        dto.setRulesMarkdown("## Combat\n- d20");
        dto.setCharacterTemplate(List.of(
                new TemplateFieldDTO("Histoire", "TEXT", null),
                new TemplateFieldDTO("PV", "NUMBER", null),
                new TemplateFieldDTO("Portrait", "IMAGE", "HERO")));
        dto.setNpcTemplate(List.of(
                new TemplateFieldDTO("Motivation", "TEXT", null)));

        mockMvc.perform(post("/api/game-systems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.characterTemplate.length()").value(3))
                .andExpect(jsonPath("$.characterTemplate[1].type").value("NUMBER"))
                .andExpect(jsonPath("$.characterTemplate[2].layout").value("HERO"))
                .andExpect(jsonPath("$.npcTemplate.length()").value(1))
                .andExpect(jsonPath("$.npcTemplate[0].name").value("Motivation"));
    }

    @Test
    void update_replacesTemplates() throws Exception {
        // Creation initiale avec un seul champ.
        GameSystemDTO dto = new GameSystemDTO();
        dto.setName("RuleSet");
        dto.setCharacterTemplate(List.of(new TemplateFieldDTO("Old", "TEXT", null)));

        MvcResult posted = mockMvc.perform(post("/api/game-systems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andReturn();

        GameSystemDTO created = objectMapper.readValue(
                posted.getResponse().getContentAsString(), GameSystemDTO.class);

        // Replace template integralement.
        created.setCharacterTemplate(List.of(
                new TemplateFieldDTO("Histoire", "TEXT", null),
                new TemplateFieldDTO("Niveau", "NUMBER", null)));

        mockMvc.perform(put("/api/game-systems/{id}", created.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(created)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.characterTemplate.length()").value(2))
                .andExpect(jsonPath("$.characterTemplate[0].name").value("Histoire"))
                .andExpect(jsonPath("$.characterTemplate[1].type").value("NUMBER"));

        // Verification que le GET independant retourne bien les nouveaux champs (pas de cache stale).
        mockMvc.perform(get("/api/game-systems/{id}", created.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.characterTemplate[?(@.name == 'Old')]").doesNotExist())
                .andExpect(jsonPath("$.characterTemplate[?(@.name == 'Histoire')]").exists());
    }

    @Test
    void create_rejectsDuplicateFieldNames() throws Exception {
        GameSystemDTO dto = new GameSystemDTO();
        dto.setName("BadRules");
        dto.setCharacterTemplate(List.of(
                new TemplateFieldDTO("Nom", "TEXT", null),
                new TemplateFieldDTO("nom", "NUMBER", null)));

        mockMvc.perform(post("/api/game-systems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().is4xxClientError());
    }
}
