package com.loremind.infrastructure.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.CatalogItem;
import com.loremind.domain.campaigncontext.ItemCatalog;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.campaigncontext.ports.ItemCatalogGenerationException;
import com.loremind.domain.campaigncontext.ports.ItemCatalogGenerator;
import com.loremind.domain.campaigncontext.ports.ItemCatalogRepository;
import com.loremind.infrastructure.web.controller.ItemCatalogController.GenerateRequest;
import com.loremind.infrastructure.web.dto.campaigncontext.ItemCatalogDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'integration pour {@link ItemCatalogController} (CRUD + recherche +
 * generation IA d'un catalogue d'objets).
 * <p>
 * Le port {@link ItemCatalogGenerator} (client du Brain) est mocke : sinon
 * l'endpoint /generate ferait un vrai appel reseau au service IA (indisponible
 * en test). Les repos reels servent aux fixtures.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ItemCatalogControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ItemCatalogRepository catalogRepository;
    @Autowired private CampaignRepository campaignRepository;

    @MockitoBean private ItemCatalogGenerator generator;

    private String campaignId;

    @BeforeEach
    void setUp() {
        campaignId = campaignRepository.save(
                Campaign.builder().name("Camp").description("desc").build()).getId();
    }

    private ItemCatalogDTO dto(String name) {
        ItemCatalogDTO dto = new ItemCatalogDTO();
        dto.setName(name);
        dto.setDescription("Une boutique");
        dto.setIcon("store");
        dto.setCampaignId(campaignId);
        dto.setOrder(0);
        return dto;
    }

    // --- POST / -------------------------------------------------------------

    @Test
    void create_returns200() throws Exception {
        mockMvc.perform(post("/api/item-catalogs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto("Echoppe"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Echoppe"))
                .andExpect(jsonPath("$.campaignId").value(campaignId));
    }

    // --- GET /{id} ----------------------------------------------------------

    @Test
    void getById_returns200() throws Exception {
        ItemCatalog saved = catalogRepository.save(ItemCatalog.builder()
                .name("Tresor").campaignId(campaignId).order(0).build());
        mockMvc.perform(get("/api/item-catalogs/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Tresor"));
    }

    @Test
    void getById_returns404_whenMissing() throws Exception {
        mockMvc.perform(get("/api/item-catalogs/{id}", "999999999"))
                .andExpect(status().isNotFound());
    }

    // --- GET /campaign/{campaignId} -----------------------------------------

    @Test
    void getByCampaign_returnsArray() throws Exception {
        catalogRepository.save(ItemCatalog.builder()
                .name("A").campaignId(campaignId).order(0).build());
        mockMvc.perform(get("/api/item-catalogs/campaign/{campaignId}", campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("A"));
    }

    // --- PUT /{id} ----------------------------------------------------------

    @Test
    void update_returns200() throws Exception {
        ItemCatalog saved = catalogRepository.save(ItemCatalog.builder()
                .name("old").campaignId(campaignId).order(0).build());
        ItemCatalogDTO dto = dto("new");
        mockMvc.perform(put("/api/item-catalogs/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("new"));
    }

    @Test
    void update_returns400_whenMissing() throws Exception {
        // Le service leve IllegalArgumentException -> GlobalExceptionHandler -> 400.
        mockMvc.perform(put("/api/item-catalogs/{id}", "999999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto("x"))))
                .andExpect(status().isBadRequest());
    }

    // --- DELETE /{id} -------------------------------------------------------

    @Test
    void delete_returns204() throws Exception {
        ItemCatalog saved = catalogRepository.save(ItemCatalog.builder()
                .name("X").campaignId(campaignId).order(0).build());
        mockMvc.perform(delete("/api/item-catalogs/{id}", saved.getId()))
                .andExpect(status().isNoContent());
    }

    // --- GET /search --------------------------------------------------------

    @Test
    void search_returnsArray() throws Exception {
        catalogRepository.save(ItemCatalog.builder()
                .name("Forge de Naheulbeuk").campaignId(campaignId).order(0).build());
        mockMvc.perform(get("/api/item-catalogs/search").param("q", "Forge"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // --- POST /generate -----------------------------------------------------

    @Test
    void generate_returns200() throws Exception {
        when(generator.generate(any(), any())).thenReturn(
                new ItemCatalogGenerator.GeneratedCatalog(
                        "Boutique magique",
                        "Objets enchantes",
                        List.of(CatalogItem.builder().name("Potion").price("50 po").build())));

        GenerateRequest req = new GenerateRequest(campaignId, "une boutique de magie");
        mockMvc.perform(post("/api/item-catalogs/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Boutique magique"));
    }

    @Test
    void generate_returns502_whenBrainUnreachable() throws Exception {
        when(generator.generate(any(), any()))
                .thenThrow(new ItemCatalogGenerationException("Brain injoignable"));

        GenerateRequest req = new GenerateRequest(campaignId, "boutique");
        mockMvc.perform(post("/api/item-catalogs/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadGateway());
    }
}
