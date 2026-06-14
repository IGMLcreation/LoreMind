package com.loremind.infrastructure.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.playcontext.Playthrough;
import com.loremind.domain.playcontext.Session;
import com.loremind.domain.playcontext.ports.PlaythroughRepository;
import com.loremind.domain.playcontext.ports.SessionRepository;
import com.loremind.infrastructure.web.controller.SessionController.RenameSessionRequest;
import com.loremind.infrastructure.web.controller.SessionController.StartSessionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'intégration du SessionController.
 * Chaîne de fixtures : Campaign -> Playthrough (une session appartient à une partie).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SessionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private PlaythroughRepository playthroughRepository;
    @Autowired private SessionRepository sessionRepository;

    private String playthroughId;

    @BeforeEach
    void setUp() {
        String campaignId = campaignRepository.save(
                Campaign.builder().name("C").description("").build()).getId();
        playthroughId = playthroughRepository.save(
                Playthrough.builder().campaignId(campaignId).name("Partie").build()).getId();
    }

    private Session saveSession() {
        return sessionRepository.save(Session.builder()
                .name("Session test")
                .playthroughId(playthroughId)
                .startedAt(LocalDateTime.now())
                .build());
    }

    @Test
    void startSession_returns200() throws Exception {
        StartSessionRequest req = new StartSessionRequest(playthroughId);
        mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playthroughId").value(playthroughId))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void startSession_returns400_whenPlaythroughMissing() throws Exception {
        StartSessionRequest req = new StartSessionRequest("999999999");
        mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getSessions_all_returnsArray() throws Exception {
        saveSession();
        mockMvc.perform(get("/api/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getSessions_byPlaythrough_returnsArray() throws Exception {
        saveSession();
        mockMvc.perform(get("/api/sessions").param("playthroughId", playthroughId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getActive_global_returnsActive() throws Exception {
        saveSession();
        mockMvc.perform(get("/api/sessions/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void getActive_byPlaythrough_returnsActive() throws Exception {
        saveSession();
        mockMvc.perform(get("/api/sessions/active").param("playthroughId", playthroughId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playthroughId").value(playthroughId));
    }

    @Test
    void getActive_returns204_whenNone() throws Exception {
        // Session terminée -> aucune active
        sessionRepository.save(Session.builder()
                .name("ended").playthroughId(playthroughId)
                .startedAt(LocalDateTime.now().minusHours(2))
                .endedAt(LocalDateTime.now().minusHours(1))
                .build());
        mockMvc.perform(get("/api/sessions/active").param("playthroughId", playthroughId))
                .andExpect(status().isNoContent());
    }

    @Test
    void getById_returns200() throws Exception {
        Session s = saveSession();
        mockMvc.perform(get("/api/sessions/{id}", s.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Session test"));
    }

    @Test
    void getById_returns404_whenMissing() throws Exception {
        mockMvc.perform(get("/api/sessions/{id}", "999999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void endSession_returns200() throws Exception {
        Session s = saveSession();
        mockMvc.perform(post("/api/sessions/{id}/end", s.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void endSession_returns400_whenMissing() throws Exception {
        mockMvc.perform(post("/api/sessions/{id}/end", "999999999"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void renameSession_returns200() throws Exception {
        Session s = saveSession();
        RenameSessionRequest req = new RenameSessionRequest("Nouveau nom");
        mockMvc.perform(patch("/api/sessions/{id}", s.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nouveau nom"));
    }

    @Test
    void renameSession_returns400_whenBlankName() throws Exception {
        Session s = saveSession();
        RenameSessionRequest req = new RenameSessionRequest("   ");
        mockMvc.perform(patch("/api/sessions/{id}", s.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteSession_returns204() throws Exception {
        Session s = saveSession();
        mockMvc.perform(delete("/api/sessions/{id}", s.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteSession_returns400_whenMissing() throws Exception {
        mockMvc.perform(delete("/api/sessions/{id}", "999999999"))
                .andExpect(status().isBadRequest());
    }
}
