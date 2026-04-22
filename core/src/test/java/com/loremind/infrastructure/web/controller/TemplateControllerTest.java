package com.loremind.infrastructure.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.lorecontext.Lore;
import com.loremind.domain.lorecontext.Template;
import com.loremind.domain.lorecontext.ports.LoreRepository;
import com.loremind.domain.lorecontext.ports.TemplateRepository;
import com.loremind.infrastructure.web.dto.lorecontext.TemplateDTO;
import com.loremind.infrastructure.web.dto.lorecontext.TemplateFieldDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TemplateControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private LoreRepository loreRepository;
    @Autowired private TemplateRepository templateRepository;

    private String loreId;

    @BeforeEach
    void setUp() {
        loreId = loreRepository.save(Lore.builder().name("Host").description("").build()).getId();
    }

    @Test
    void create_withMixedFields_returnsAllSerialized() throws Exception {
        TemplateDTO dto = new TemplateDTO();
        dto.setLoreId(loreId);
        dto.setName("Fiche PNJ");
        dto.setDescription("Template PNJ");
        dto.setFields(List.of(
                new TemplateFieldDTO("histoire", "TEXT", null),
                new TemplateFieldDTO("portraits", "IMAGE", "HERO")
        ));

        mockMvc.perform(post("/api/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.fieldCount").value(2))
                .andExpect(jsonPath("$.fields[1].layout").value("HERO"));
    }

    @Test
    void getById_returns200() throws Exception {
        Template saved = templateRepository.save(Template.builder()
                .loreId(loreId).name("X").fields(List.of()).build());
        mockMvc.perform(get("/api/templates/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("X"));
    }

    @Test
    void getById_returns404_whenMissing() throws Exception {
        mockMvc.perform(get("/api/templates/{id}", "999999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAll_withLoreIdFilter_filtersCorrectly() throws Exception {
        Lore other = loreRepository.save(Lore.builder().name("Other").description("").build());
        templateRepository.save(Template.builder().loreId(loreId).name("mine").fields(List.of()).build());
        templateRepository.save(Template.builder().loreId(other.getId()).name("their").fields(List.of()).build());

        mockMvc.perform(get("/api/templates").param("loreId", loreId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'mine')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'their')]").doesNotExist());
    }

    @Test
    void search_returnsMatches() throws Exception {
        templateRepository.save(Template.builder().loreId(loreId).name("Fiche PNJ").fields(List.of()).build());
        mockMvc.perform(get("/api/templates/search").param("q", "fiche"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void update_returns200() throws Exception {
        Template saved = templateRepository.save(Template.builder()
                .loreId(loreId).name("old").fields(List.of()).build());
        TemplateDTO dto = new TemplateDTO();
        dto.setLoreId(loreId);
        dto.setName("new");

        mockMvc.perform(put("/api/templates/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("new"));
    }

    @Test
    void delete_returns204() throws Exception {
        Template saved = templateRepository.save(Template.builder()
                .loreId(loreId).name("X").fields(List.of()).build());
        mockMvc.perform(delete("/api/templates/{id}", saved.getId()))
                .andExpect(status().isNoContent());
    }
}
