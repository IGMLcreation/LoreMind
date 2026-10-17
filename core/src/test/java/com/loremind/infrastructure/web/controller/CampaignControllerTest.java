package com.loremind.infrastructure.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.infrastructure.web.dto.campaigncontext.CampaignDTO;
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
class CampaignControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CampaignRepository campaignRepository;

    @Test
    void create_returns200_withOptionalLoreId() throws Exception {
        CampaignDTO dto = new CampaignDTO();
        dto.setName("Les Ombres");
        dto.setDescription("Dark fantasy");
        // loreId laisse null : campagne one-shot

        mockMvc.perform(post("/api/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Les Ombres"));
    }

    @Test
    void getById_returns200() throws Exception {
        Campaign saved = campaignRepository.save(Campaign.builder().name("X").description("").build());
        mockMvc.perform(get("/api/campaigns/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("X"));
    }

    @Test
    void getById_returns404_whenMissing() throws Exception {
        mockMvc.perform(get("/api/campaigns/{id}", "999999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAll_returnsArray() throws Exception {
        campaignRepository.save(Campaign.builder().name("A").description("").build());
        mockMvc.perform(get("/api/campaigns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void search_returnsMatches() throws Exception {
        campaignRepository.save(Campaign.builder().name("Les Ombres").description("").build());
        mockMvc.perform(get("/api/campaigns/search").param("q", "ombres"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void update_returns200() throws Exception {
        Campaign saved = campaignRepository.save(Campaign.builder().name("old").description("").build());
        CampaignDTO dto = new CampaignDTO();
        dto.setName("new");
        dto.setDescription("d");
        mockMvc.perform(put("/api/campaigns/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("new"));
    }

    @Test
    void delete_returns204() throws Exception {
        Campaign saved = campaignRepository.save(Campaign.builder().name("X").description("").build());
        mockMvc.perform(delete("/api/campaigns/{id}", saved.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    void publication_roundTripsThroughTheApi() throws Exception {
        Campaign saved = campaignRepository.save(Campaign.builder().name("Module").description("").build());

        mockMvc.perform(get("/api/campaigns/{id}/publication", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtitle").doesNotExist());

        mockMvc.perform(put("/api/campaigns/{id}/publication", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subtitle\":\"  Une enquête \",\"players\":\"3 à 5\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtitle").value("Une enquête"));

        mockMvc.perform(get("/api/campaigns/{id}/publication", saved.getId()))
                .andExpect(jsonPath("$.subtitle").value("Une enquête"))
                .andExpect(jsonPath("$.players").value("3 à 5"))
                // Aucun champ calculé (hasContent, hasCredits…) ne fuit dans le JSON.
                .andExpect(jsonPath("$.empty").doesNotExist())
                .andExpect(jsonPath("$.content").doesNotExist());
    }

    @Test
    void pdfExport_acceptsOptions_andNamesThePlayerEdition() throws Exception {
        Campaign saved = campaignRepository.save(Campaign.builder().name("Module").description("").build());

        mockMvc.perform(get("/api/campaigns/{id}/pdf-export", saved.getId())
                        .param("edition", "player").param("format", "A5").param("print", "true")
                        .param("sections", "NARRATIVE,LORE").param("lore", "LINKED"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Disposition", "attachment; filename=\"Module_joueurs.pdf\""));
    }
}
