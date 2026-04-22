package com.loremind.infrastructure.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.campaigncontext.Arc;
import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.Chapter;
import com.loremind.domain.campaigncontext.ports.ArcRepository;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.campaigncontext.ports.ChapterRepository;
import com.loremind.infrastructure.web.dto.campaigncontext.ChapterDTO;
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
class ChapterControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private ArcRepository arcRepository;
    @Autowired private ChapterRepository chapterRepository;

    private String arcId;

    @BeforeEach
    void setUp() {
        String campaignId = campaignRepository.save(Campaign.builder().name("C").description("").build()).getId();
        arcId = arcRepository.save(Arc.builder().campaignId(campaignId).name("A").order(0).build()).getId();
    }

    @Test
    void create_returns200() throws Exception {
        ChapterDTO dto = new ChapterDTO();
        dto.setName("Ch1");
        dto.setArcId(arcId);
        dto.setOrder(0);
        mockMvc.perform(post("/api/chapters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ch1"));
    }

    @Test
    void getById_returns200() throws Exception {
        Chapter saved = chapterRepository.save(Chapter.builder().arcId(arcId).name("Ch").order(0).build());
        mockMvc.perform(get("/api/chapters/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ch"));
    }

    @Test
    void getById_returns404_whenMissing() throws Exception {
        mockMvc.perform(get("/api/chapters/{id}", "999999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAll_returnsArray() throws Exception {
        chapterRepository.save(Chapter.builder().arcId(arcId).name("A").order(0).build());
        mockMvc.perform(get("/api/chapters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getByArc_pathVariant() throws Exception {
        chapterRepository.save(Chapter.builder().arcId(arcId).name("A").order(0).build());
        mockMvc.perform(get("/api/chapters/arc/{id}", arcId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void update_returns200() throws Exception {
        Chapter saved = chapterRepository.save(Chapter.builder().arcId(arcId).name("old").order(0).build());
        ChapterDTO dto = new ChapterDTO();
        dto.setName("new");
        dto.setArcId(arcId);
        dto.setOrder(0);
        mockMvc.perform(put("/api/chapters/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("new"));
    }

    @Test
    void delete_returns204() throws Exception {
        Chapter saved = chapterRepository.save(Chapter.builder().arcId(arcId).name("X").order(0).build());
        mockMvc.perform(delete("/api/chapters/{id}", saved.getId()))
                .andExpect(status().isNoContent());
    }
}
