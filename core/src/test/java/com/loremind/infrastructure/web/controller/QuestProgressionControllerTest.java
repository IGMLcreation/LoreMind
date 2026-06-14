package com.loremind.infrastructure.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.campaigncontext.Arc;
import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.Chapter;
import com.loremind.domain.campaigncontext.ProgressionStatus;
import com.loremind.domain.campaigncontext.ports.ArcRepository;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.campaigncontext.ports.ChapterRepository;
import com.loremind.domain.playcontext.Playthrough;
import com.loremind.domain.playcontext.ports.PlaythroughRepository;
import com.loremind.domain.playcontext.ports.QuestProgressionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'intégration de {@link QuestProgressionController}.
 * Couvre list (GET, map chapterId->status) et setStatus (PUT) avec :
 *  - statut valide IN_PROGRESS / COMPLETED
 *  - statut vide/null => NOT_STARTED (suppression de ligne)
 *  - statut invalide => 400 (badRequest renvoyé directement par le contrôleur)
 * <p>
 * Le {@code chapterId} doit être un id de Chapter REEL (clé numérique) : on crée
 * donc la chaîne campaign -> arc -> chapter en fixture.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class QuestProgressionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private ArcRepository arcRepository;
    @Autowired private ChapterRepository chapterRepository;
    @Autowired private PlaythroughRepository playthroughRepository;
    @Autowired private QuestProgressionRepository repo;

    private String playthroughId;
    private String chapterId;

    @BeforeEach
    void setUp() {
        // Chaîne de fixtures : campaign -> playthrough (+ arc -> chapter pour un chapterId reel).
        String campId = campaignRepository.save(Campaign.builder().name("C").description("").build()).getId();
        String arcId = arcRepository.save(Arc.builder().campaignId(campId).name("A").order(0).build()).getId();
        chapterId = chapterRepository.save(Chapter.builder().arcId(arcId).name("Ch").order(0).build()).getId();
        playthroughId = playthroughRepository.save(
                Playthrough.builder().campaignId(campId).name("Table").description("").build()).getId();
    }

    @Test
    void list_returnsEmptyMap_whenNoProgressions() throws Exception {
        mockMvc.perform(get("/api/playthroughs/{pid}/quest-progressions", playthroughId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isMap());
    }

    @Test
    void list_returnsStatusMap() throws Exception {
        // Pré-positionne une progression explicite via le repo réel.
        repo.setStatus(playthroughId, chapterId, ProgressionStatus.IN_PROGRESS);
        mockMvc.perform(get("/api/playthroughs/{pid}/quest-progressions", playthroughId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['" + chapterId + "']").value("IN_PROGRESS"));
    }

    @Test
    void setStatus_inProgress_returns204() throws Exception {
        mockMvc.perform(put("/api/playthroughs/{pid}/quest-progressions/{cid}", playthroughId, chapterId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new QuestProgressionController.SetStatusRequest("IN_PROGRESS"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void setStatus_completed_returns204() throws Exception {
        mockMvc.perform(put("/api/playthroughs/{pid}/quest-progressions/{cid}", playthroughId, chapterId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new QuestProgressionController.SetStatusRequest("COMPLETED"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void setStatus_nullStatus_returns204_asNotStarted() throws Exception {
        // status null => NOT_STARTED (branche : pas de parsing)
        mockMvc.perform(put("/api/playthroughs/{pid}/quest-progressions/{cid}", playthroughId, chapterId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new QuestProgressionController.SetStatusRequest(null))))
                .andExpect(status().isNoContent());
    }

    @Test
    void setStatus_blankStatus_returns204_asNotStarted() throws Exception {
        // status vide => NOT_STARTED (branche isBlank)
        mockMvc.perform(put("/api/playthroughs/{pid}/quest-progressions/{cid}", playthroughId, chapterId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new QuestProgressionController.SetStatusRequest("   "))))
                .andExpect(status().isNoContent());
    }

    @Test
    void setStatus_invalidStatus_returns400() throws Exception {
        // valeur d'enum inconnue => IllegalArgumentException attrapée => badRequest direct
        mockMvc.perform(put("/api/playthroughs/{pid}/quest-progressions/{cid}", playthroughId, chapterId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new QuestProgressionController.SetStatusRequest("NOPE"))))
                .andExpect(status().isBadRequest());
    }
}
