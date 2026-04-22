package com.loremind.infrastructure.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.lorecontext.Lore;
import com.loremind.domain.lorecontext.LoreNode;
import com.loremind.domain.lorecontext.ports.LoreNodeRepository;
import com.loremind.domain.lorecontext.ports.LoreRepository;
import com.loremind.infrastructure.web.dto.lorecontext.LoreNodeDTO;
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
class LoreNodeControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private LoreRepository loreRepository;
    @Autowired private LoreNodeRepository nodeRepository;

    private String loreId;

    @BeforeEach
    void setUp() {
        loreId = loreRepository.save(Lore.builder().name("Host").description("").build()).getId();
    }

    @Test
    void createNode_returns200() throws Exception {
        LoreNodeDTO dto = new LoreNodeDTO();
        dto.setName("Personnages");
        dto.setIcon("users");
        dto.setLoreId(loreId);

        mockMvc.perform(post("/api/lore-nodes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Personnages"))
                .andExpect(jsonPath("$.icon").value("users"));
    }

    @Test
    void getById_returns200_whenExists() throws Exception {
        LoreNode saved = nodeRepository.save(LoreNode.builder().name("X").loreId(loreId).build());

        mockMvc.perform(get("/api/lore-nodes/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("X"));
    }

    @Test
    void getById_returns404_whenMissing() throws Exception {
        mockMvc.perform(get("/api/lore-nodes/{id}", "999999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAll_withoutFilter_returnsAllNodes() throws Exception {
        nodeRepository.save(LoreNode.builder().name("A").loreId(loreId).build());
        nodeRepository.save(LoreNode.builder().name("B").loreId(loreId).build());

        mockMvc.perform(get("/api/lore-nodes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getAll_withLoreIdFilter_returnsFilteredNodes() throws Exception {
        Lore other = loreRepository.save(Lore.builder().name("Other").description("").build());
        nodeRepository.save(LoreNode.builder().name("mine").loreId(loreId).build());
        nodeRepository.save(LoreNode.builder().name("their").loreId(other.getId()).build());

        mockMvc.perform(get("/api/lore-nodes").param("loreId", loreId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'mine')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'their')]").doesNotExist());
    }

    @Test
    void getByLoreId_pathVariant_works() throws Exception {
        nodeRepository.save(LoreNode.builder().name("A").loreId(loreId).build());
        mockMvc.perform(get("/api/lore-nodes/lore/{loreId}", loreId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getByParentId_returnsChildren() throws Exception {
        LoreNode parent = nodeRepository.save(LoreNode.builder().name("P").loreId(loreId).build());
        nodeRepository.save(LoreNode.builder().name("C").parentId(parent.getId()).loreId(loreId).build());

        mockMvc.perform(get("/api/lore-nodes/parent/{id}", parent.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("C"));
    }

    @Test
    void search_findsByPartialName() throws Exception {
        nodeRepository.save(LoreNode.builder().name("Personnages").loreId(loreId).build());
        mockMvc.perform(get("/api/lore-nodes/search").param("q", "person"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Personnages')]").exists());
    }

    @Test
    void update_returns200() throws Exception {
        LoreNode saved = nodeRepository.save(LoreNode.builder().name("old").loreId(loreId).build());
        LoreNodeDTO dto = new LoreNodeDTO();
        dto.setName("new");
        dto.setLoreId(loreId);

        mockMvc.perform(put("/api/lore-nodes/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("new"));
    }

    @Test
    void delete_returns204() throws Exception {
        LoreNode saved = nodeRepository.save(LoreNode.builder().name("X").loreId(loreId).build());
        mockMvc.perform(delete("/api/lore-nodes/{id}", saved.getId()))
                .andExpect(status().isNoContent());
    }
}
