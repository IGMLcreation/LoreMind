package com.loremind.infrastructure.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.campaigncontext.Arc;
import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.Chapter;
import com.loremind.domain.campaigncontext.Scene;
import com.loremind.domain.campaigncontext.ports.ArcRepository;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.campaigncontext.ports.ChapterRepository;
import com.loremind.domain.campaigncontext.ports.SceneRepository;
import com.loremind.infrastructure.web.dto.campaigncontext.SceneDTO;
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
class SceneControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private ArcRepository arcRepository;
    @Autowired private ChapterRepository chapterRepository;
    @Autowired private SceneRepository sceneRepository;

    private String chapterId;

    @BeforeEach
    void setUp() {
        String campId = campaignRepository.save(Campaign.builder().name("C").description("").build()).getId();
        String arcId = arcRepository.save(Arc.builder().campaignId(campId).name("A").order(0).build()).getId();
        chapterId = chapterRepository.save(Chapter.builder().arcId(arcId).name("Ch").order(0).build()).getId();
    }

    @Test
    void create_returns200() throws Exception {
        SceneDTO dto = new SceneDTO();
        dto.setName("L'auberge");
        dto.setChapterId(chapterId);
        dto.setOrder(0);
        mockMvc.perform(post("/api/scenes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("L'auberge"));
    }

    @Test
    void getById_returns200() throws Exception {
        Scene saved = sceneRepository.save(Scene.builder().chapterId(chapterId).name("S").order(0).build());
        mockMvc.perform(get("/api/scenes/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("S"));
    }

    @Test
    void getById_returns404_whenMissing() throws Exception {
        mockMvc.perform(get("/api/scenes/{id}", "999999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAll_returnsArray() throws Exception {
        sceneRepository.save(Scene.builder().chapterId(chapterId).name("A").order(0).build());
        mockMvc.perform(get("/api/scenes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getByChapter_pathVariant() throws Exception {
        sceneRepository.save(Scene.builder().chapterId(chapterId).name("A").order(0).build());
        mockMvc.perform(get("/api/scenes").param("chapterId", chapterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void update_returns200() throws Exception {
        Scene saved = sceneRepository.save(Scene.builder().chapterId(chapterId).name("old").order(0).build());
        SceneDTO dto = new SceneDTO();
        dto.setName("new");
        dto.setChapterId(chapterId);
        dto.setOrder(0);
        mockMvc.perform(put("/api/scenes/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("new"));
    }

    @Test
    void delete_returns204() throws Exception {
        Scene saved = sceneRepository.save(Scene.builder().chapterId(chapterId).name("X").order(0).build());
        mockMvc.perform(delete("/api/scenes/{id}", saved.getId()))
                .andExpect(status().isNoContent());
    }
}
