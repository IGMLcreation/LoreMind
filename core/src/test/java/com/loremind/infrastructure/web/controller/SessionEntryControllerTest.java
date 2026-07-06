package com.loremind.infrastructure.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.playcontext.EntryType;
import com.loremind.domain.playcontext.Playthrough;
import com.loremind.domain.playcontext.Session;
import com.loremind.domain.playcontext.SessionEntry;
import com.loremind.domain.playcontext.ports.PlaythroughRepository;
import com.loremind.domain.playcontext.ports.SessionEntryRepository;
import com.loremind.domain.playcontext.ports.SessionRepository;
import com.loremind.infrastructure.web.controller.SessionEntryController.EntryRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'intégration du SessionEntryController (endpoints imbriqués sous une Session).
 * Chaîne de fixtures : Campaign -> Playthrough -> Session.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SessionEntryControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private PlaythroughRepository playthroughRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private SessionEntryRepository entryRepository;

    private static final LocalDateTime FIXED_TIME =
            LocalDateTime.of(2024, java.time.Month.JANUARY, 1, 0, 0);

    private String sessionId;

    @BeforeEach
    void setUp() {
        String campaignId = campaignRepository.save(
                Campaign.builder().name("C").description("").build()).getId();
        String playthroughId = playthroughRepository.save(
                Playthrough.builder().campaignId(campaignId).name("Partie").build()).getId();
        sessionId = sessionRepository.save(Session.builder()
                .name("Session")
                .playthroughId(playthroughId)
                .startedAt(FIXED_TIME)
                .build()).getId();
    }

    private SessionEntry saveEntry() {
        return entryRepository.save(SessionEntry.builder()
                .sessionId(sessionId)
                .type(EntryType.NOTE)
                .content("Contenu initial")
                .occurredAt(FIXED_TIME)
                .build());
    }

    @Test
    void getEntries_returnsArray() throws Exception {
        saveEntry();
        mockMvc.perform(get("/api/sessions/{sessionId}/entries", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void createEntry_returns200() throws Exception {
        EntryRequest req = new EntryRequest(EntryType.EVENT, "Combat gagné", FIXED_TIME);
        mockMvc.perform(post("/api/sessions/{sessionId}/entries", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Combat gagné"))
                .andExpect(jsonPath("$.type").value("EVENT"));
    }

    @Test
    void createEntry_defaultsTypeToNote_whenNull() throws Exception {
        EntryRequest req = new EntryRequest(null, "Note sans type", null);
        mockMvc.perform(post("/api/sessions/{sessionId}/entries", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("NOTE"));
    }

    @Test
    void createEntry_returns400_whenSessionMissing() throws Exception {
        EntryRequest req = new EntryRequest(EntryType.NOTE, "x", null);
        mockMvc.perform(post("/api/sessions/{sessionId}/entries", "999999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createEntry_returns400_whenContentBlank() throws Exception {
        EntryRequest req = new EntryRequest(EntryType.NOTE, "   ", null);
        mockMvc.perform(post("/api/sessions/{sessionId}/entries", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateEntry_returns200() throws Exception {
        SessionEntry e = saveEntry();
        EntryRequest req = new EntryRequest(EntryType.DICE_ROLL, "Jet modifié", FIXED_TIME);
        mockMvc.perform(put("/api/sessions/{sessionId}/entries/{entryId}", sessionId, e.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Jet modifié"))
                .andExpect(jsonPath("$.type").value("DICE_ROLL"));
    }

    @Test
    void updateEntry_returns400_whenMissing() throws Exception {
        EntryRequest req = new EntryRequest(EntryType.NOTE, "x", null);
        mockMvc.perform(put("/api/sessions/{sessionId}/entries/{entryId}", sessionId, "999999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteEntry_returns204() throws Exception {
        SessionEntry e = saveEntry();
        mockMvc.perform(delete("/api/sessions/{sessionId}/entries/{entryId}", sessionId, e.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteEntry_returns400_whenMissing() throws Exception {
        mockMvc.perform(delete("/api/sessions/{sessionId}/entries/{entryId}", sessionId, "999999999"))
                .andExpect(status().isBadRequest());
    }
}
