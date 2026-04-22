package com.loremind.infrastructure.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.lorecontext.Lore;
import com.loremind.domain.lorecontext.LoreNode;
import com.loremind.domain.lorecontext.Page;
import com.loremind.domain.lorecontext.Template;
import com.loremind.domain.lorecontext.ports.LoreNodeRepository;
import com.loremind.domain.lorecontext.ports.LoreRepository;
import com.loremind.domain.lorecontext.ports.PageRepository;
import com.loremind.domain.lorecontext.ports.TemplateRepository;
import com.loremind.infrastructure.web.dto.lorecontext.PageDTO;
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
class PageControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private LoreRepository loreRepository;
    @Autowired private LoreNodeRepository nodeRepository;
    @Autowired private TemplateRepository templateRepository;
    @Autowired private PageRepository pageRepository;

    private String loreId;
    private String nodeId;
    private String templateId;

    @BeforeEach
    void setUp() {
        loreId = loreRepository.save(Lore.builder().name("L").description("").build()).getId();
        nodeId = nodeRepository.save(LoreNode.builder().name("N").loreId(loreId).build()).getId();
        templateId = templateRepository.save(Template.builder()
                .loreId(loreId).name("T").fields(List.of()).build()).getId();
    }

    @Test
    void create_returns200() throws Exception {
        PageDTO dto = new PageDTO();
        dto.setLoreId(loreId);
        dto.setNodeId(nodeId);
        dto.setTemplateId(templateId);
        dto.setTitle("Thorin");

        mockMvc.perform(post("/api/pages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Thorin"));
    }

    @Test
    void getById_returns200() throws Exception {
        Page saved = pageRepository.save(buildMinimal("Thorin"));
        mockMvc.perform(get("/api/pages/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Thorin"));
    }

    @Test
    void getById_returns404_whenMissing() throws Exception {
        mockMvc.perform(get("/api/pages/{id}", "999999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAll_withLoreIdFilter_filters() throws Exception {
        pageRepository.save(buildMinimal("A"));
        mockMvc.perform(get("/api/pages").param("loreId", loreId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getAll_withNodeIdFilter_filters() throws Exception {
        pageRepository.save(buildMinimal("A"));
        mockMvc.perform(get("/api/pages").param("nodeId", nodeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void search_returnsMatches() throws Exception {
        pageRepository.save(buildMinimal("Thorin"));
        mockMvc.perform(get("/api/pages/search").param("q", "tho"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void update_returns200() throws Exception {
        Page saved = pageRepository.save(buildMinimal("old"));
        PageDTO dto = new PageDTO();
        dto.setTitle("new");
        dto.setNodeId(nodeId);

        mockMvc.perform(put("/api/pages/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("new"));
    }

    @Test
    void delete_returns204() throws Exception {
        Page saved = pageRepository.save(buildMinimal("X"));
        mockMvc.perform(delete("/api/pages/{id}", saved.getId()))
                .andExpect(status().isNoContent());
    }

    private Page buildMinimal(String title) {
        return Page.builder()
                .loreId(loreId).nodeId(nodeId).templateId(templateId).title(title)
                .values(Map.of()).imageValues(Map.of()).tags(List.of()).relatedPageIds(List.of())
                .build();
    }
}
