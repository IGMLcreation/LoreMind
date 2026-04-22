package com.loremind.infrastructure.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.lorecontext.Lore;
import com.loremind.domain.lorecontext.ports.LoreRepository;
import com.loremind.infrastructure.web.dto.lorecontext.LoreDTO;
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
 * Tests d'integration HTTP pour LoreController.
 * Pattern : @SpringBootTest + MockMvc + @Transactional.
 * Couvre du bout en bout : serialisation DTO -> mapper -> service ->
 * repository -> base -> reponse JSON.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LoreControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private LoreRepository loreRepository;

    @Test
    void createLore_returns200_withGeneratedId() throws Exception {
        LoreDTO dto = new LoreDTO();
        dto.setName("Ithoril");
        dto.setDescription("Royaume sombre");

        mockMvc.perform(post("/api/lores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Ithoril"))
                .andExpect(jsonPath("$.description").value("Royaume sombre"));
    }

    @Test
    void getLoreById_returns200_whenExists() throws Exception {
        Lore saved = loreRepository.save(Lore.builder().name("Ithoril").description("").build());

        mockMvc.perform(get("/api/lores/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.name").value("Ithoril"));
    }

    @Test
    void getLoreById_returns404_whenNotFound() throws Exception {
        // ID numerique inexistant (les ids cote DB sont BIGSERIAL parses via Long).
        mockMvc.perform(get("/api/lores/{id}", "999999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllLores_returnsJsonArray() throws Exception {
        loreRepository.save(Lore.builder().name("L1").description("").build());
        loreRepository.save(Lore.builder().name("L2").description("").build());

        mockMvc.perform(get("/api/lores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    void searchLores_returnsMatchesByName() throws Exception {
        loreRepository.save(Lore.builder().name("Ithoril").description("").build());
        loreRepository.save(Lore.builder().name("Autre").description("").build());

        mockMvc.perform(get("/api/lores/search").param("q", "ithor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Ithoril')]").exists());
    }

    @Test
    void updateLore_returns200_withNewValues() throws Exception {
        Lore saved = loreRepository.save(Lore.builder().name("Old").description("").build());
        LoreDTO dto = new LoreDTO();
        dto.setName("New");
        dto.setDescription("New desc");

        mockMvc.perform(put("/api/lores/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New"));
    }

    @Test
    void deleteLore_returns204() throws Exception {
        Lore saved = loreRepository.save(Lore.builder().name("X").description("").build());

        mockMvc.perform(delete("/api/lores/{id}", saved.getId()))
                .andExpect(status().isNoContent());
    }
}
