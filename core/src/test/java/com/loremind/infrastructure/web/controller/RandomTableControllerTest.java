package com.loremind.infrastructure.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.randomtable.RandomTable;
import com.loremind.domain.campaigncontext.randomtable.RandomTableEntry;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.campaigncontext.ports.exceptions.RandomTableGenerationException;
import com.loremind.domain.campaigncontext.ports.RandomTableGenerator;
import com.loremind.domain.campaigncontext.ports.RandomTableRepository;
import com.loremind.infrastructure.web.controller.RandomTableController.GenerateRequest;
import com.loremind.infrastructure.web.controller.RandomTableController.ImproviseRequest;
import com.loremind.infrastructure.web.dto.campaigncontext.RandomTableDTO;
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
 * Tests d'integration pour {@link RandomTableController} (CRUD + recherche +
 * generation IA d'une table + improvisation narrative).
 * <p>
 * Le port {@link RandomTableGenerator} (client du Brain) est mocke : sinon les
 * endpoints /generate et /improvise feraient un vrai appel reseau au service IA
 * (indisponible en test). Les repos reels servent aux fixtures.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RandomTableControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RandomTableRepository tableRepository;
    @Autowired private CampaignRepository campaignRepository;

    @MockitoBean private RandomTableGenerator generator;

    private String campaignId;

    @BeforeEach
    void setUp() {
        campaignId = campaignRepository.save(
                Campaign.builder().name("Camp").description("desc").build()).getId();
    }

    private RandomTableDTO dto(String name) {
        RandomTableDTO dto = new RandomTableDTO();
        dto.setName(name);
        dto.setDescription("Rencontres aleatoires");
        dto.setDiceFormula("1d20");
        dto.setIcon("dice");
        dto.setCampaignId(campaignId);
        dto.setOrder(0);
        return dto;
    }

    // --- POST / -------------------------------------------------------------

    @Test
    void create_returns200() throws Exception {
        mockMvc.perform(post("/api/random-tables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto("Rencontres"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Rencontres"))
                .andExpect(jsonPath("$.diceFormula").value("1d20"));
    }

    // --- GET /{id} ----------------------------------------------------------

    @Test
    void getById_returns200() throws Exception {
        RandomTable saved = tableRepository.save(RandomTable.builder()
                .name("Butin").diceFormula("1d6").campaignId(campaignId).order(0).build());
        mockMvc.perform(get("/api/random-tables/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Butin"));
    }

    @Test
    void getById_returns404_whenMissing() throws Exception {
        mockMvc.perform(get("/api/random-tables/{id}", "999999999"))
                .andExpect(status().isNotFound());
    }

    // --- GET /campaign/{campaignId} -----------------------------------------

    @Test
    void getByCampaign_returnsArray() throws Exception {
        tableRepository.save(RandomTable.builder()
                .name("A").diceFormula("1d20").campaignId(campaignId).order(0).build());
        mockMvc.perform(get("/api/random-tables/campaign/{campaignId}", campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("A"));
    }

    // --- PUT /{id} ----------------------------------------------------------

    @Test
    void update_returns200() throws Exception {
        RandomTable saved = tableRepository.save(RandomTable.builder()
                .name("old").diceFormula("1d20").campaignId(campaignId).order(0).build());
        mockMvc.perform(put("/api/random-tables/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto("new"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("new"));
    }

    @Test
    void update_returns400_whenMissing() throws Exception {
        // Le service leve IllegalArgumentException -> GlobalExceptionHandler -> 400.
        mockMvc.perform(put("/api/random-tables/{id}", "999999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto("x"))))
                .andExpect(status().isBadRequest());
    }

    // --- DELETE /{id} -------------------------------------------------------

    @Test
    void delete_returns204() throws Exception {
        RandomTable saved = tableRepository.save(RandomTable.builder()
                .name("X").diceFormula("1d20").campaignId(campaignId).order(0).build());
        mockMvc.perform(delete("/api/random-tables/{id}", saved.getId()))
                .andExpect(status().isNoContent());
    }

    // --- GET /search --------------------------------------------------------

    @Test
    void search_returnsArray() throws Exception {
        tableRepository.save(RandomTable.builder()
                .name("Complications urbaines").diceFormula("1d20")
                .campaignId(campaignId).order(0).build());
        mockMvc.perform(get("/api/random-tables/search").param("q", "Complications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // --- POST /generate -----------------------------------------------------

    @Test
    void generate_returns200() throws Exception {
        when(generator.generate(any(), any(), any())).thenReturn(
                new RandomTableGenerator.GeneratedTable(
                        "Rencontres en foret",
                        "Que croisez-vous ?",
                        List.of(RandomTableEntry.builder()
                                .minRoll(1).maxRoll(10).label("Gobelins").build())));

        GenerateRequest req = new GenerateRequest(campaignId, "rencontres en foret", "1d20");
        mockMvc.perform(post("/api/random-tables/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Rencontres en foret"));
    }

    @Test
    void generate_returns502_whenBrainUnreachable() throws Exception {
        when(generator.generate(any(), any(), any()))
                .thenThrow(new RandomTableGenerationException("Brain injoignable"));

        GenerateRequest req = new GenerateRequest(campaignId, "rencontres", "1d20");
        mockMvc.perform(post("/api/random-tables/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadGateway());
    }

    // --- POST /improvise ----------------------------------------------------

    @Test
    void improvise_returns200() throws Exception {
        when(generator.improvise(any(), any(), any(), any()))
                .thenReturn("Les gobelins surgissent des fourres...");

        ImproviseRequest req = new ImproviseRequest(
                campaignId, "Rencontres", "Gobelins", "3 gobelins armes");
        mockMvc.perform(post("/api/random-tables/improvise")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.narration").value("Les gobelins surgissent des fourres..."));
    }

    @Test
    void improvise_returns502_whenBrainUnreachable() throws Exception {
        when(generator.improvise(any(), any(), any(), any()))
                .thenThrow(new RandomTableGenerationException("Brain injoignable"));

        ImproviseRequest req = new ImproviseRequest(
                campaignId, "Rencontres", "Gobelins", "3 gobelins");
        mockMvc.perform(post("/api/random-tables/improvise")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadGateway());
    }
}
