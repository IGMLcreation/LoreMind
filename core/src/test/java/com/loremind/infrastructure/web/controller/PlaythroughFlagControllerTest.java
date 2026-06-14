package com.loremind.infrastructure.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.playcontext.Playthrough;
import com.loremind.domain.playcontext.ports.PlaythroughFlagRepository;
import com.loremind.domain.playcontext.ports.PlaythroughRepository;
import com.loremind.infrastructure.web.dto.playcontext.PlaythroughFlagDTO;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'intégration de {@link PlaythroughFlagController}.
 * Couvre les 3 endpoints : list (GET), setFlag (PUT), deleteFlag (DELETE).
 * Les flags sont indexés par playthroughId : on crée une campagne puis un playthrough en fixture.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlaythroughFlagControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private PlaythroughRepository playthroughRepository;
    @Autowired private PlaythroughFlagRepository flagRepository;

    private String playthroughId;

    @BeforeEach
    void setUp() {
        // Chaîne de fixtures : campaign -> playthrough
        String campId = campaignRepository.save(Campaign.builder().name("C").description("").build()).getId();
        playthroughId = playthroughRepository.save(
                Playthrough.builder().campaignId(campId).name("Table").description("").build()).getId();
    }

    @Test
    void list_returnsEmptyArray_whenNoFlags() throws Exception {
        mockMvc.perform(get("/api/playthroughs/{pid}/flags", playthroughId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void list_returnsExistingFlags() throws Exception {
        // Pré-positionne un flag via le repo réel
        flagRepository.setFlag(playthroughId, "porte_ouverte", true);
        mockMvc.perform(get("/api/playthroughs/{pid}/flags", playthroughId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("porte_ouverte"))
                .andExpect(jsonPath("$[0].value").value(true));
    }

    @Test
    void setFlag_returns200_andEcho() throws Exception {
        PlaythroughFlagDTO body = new PlaythroughFlagDTO("dragon_vaincu", true);
        mockMvc.perform(put("/api/playthroughs/{pid}/flags/{name}", playthroughId, "dragon_vaincu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("dragon_vaincu"))
                .andExpect(jsonPath("$.value").value(true));
    }

    @Test
    void setFlag_false_returns200() throws Exception {
        PlaythroughFlagDTO body = new PlaythroughFlagDTO("ignore", false);
        mockMvc.perform(put("/api/playthroughs/{pid}/flags/{name}", playthroughId, "ignore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(false));
    }

    @Test
    void deleteFlag_returns204() throws Exception {
        flagRepository.setFlag(playthroughId, "a_supprimer", true);
        mockMvc.perform(delete("/api/playthroughs/{pid}/flags/{name}", playthroughId, "a_supprimer"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteFlag_returns204_whenAbsent() throws Exception {
        // deleteFlag est idempotent : pas d'erreur même si le flag n'existe pas
        mockMvc.perform(delete("/api/playthroughs/{pid}/flags/{name}", playthroughId, "inexistant"))
                .andExpect(status().isNoContent());
    }
}
