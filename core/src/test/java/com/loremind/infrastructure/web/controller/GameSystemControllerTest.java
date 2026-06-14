package com.loremind.infrastructure.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.gamesystemcontext.RulesImportProgress;
import com.loremind.domain.gamesystemcontext.RulesImportResult;
import com.loremind.domain.gamesystemcontext.ports.RulesImportException;
import com.loremind.domain.gamesystemcontext.ports.RulesPdfImporter;
import com.loremind.infrastructure.web.dto.gamesystemcontext.GameSystemDTO;
import com.loremind.infrastructure.web.dto.shared.TemplateFieldDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'integration du GameSystemController centres sur la persistance
 * des templates PJ/PNJ via l'API REST. Le CRUD de base est suppose stable.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GameSystemControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private RulesPdfImporter rulesPdfImporter;
    @MockBean(name = "applicationTaskExecutor") private TaskExecutor taskExecutor;

    @BeforeEach
    void setUp() {
        // Tache de l'import streame executee en ligne -> events SSE deterministes.
        doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; })
                .when(taskExecutor).execute(any(Runnable.class));
    }

    /** Cree un GameSystem minimal via l'API et renvoie son id. */
    private String createGameSystem(String name) throws Exception {
        GameSystemDTO dto = new GameSystemDTO();
        dto.setName(name);
        MvcResult posted = mockMvc.perform(post("/api/game-systems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(
                posted.getResponse().getContentAsString(), GameSystemDTO.class).getId();
    }

    @Test
    void create_persistsCharacterAndNpcTemplates() throws Exception {
        GameSystemDTO dto = new GameSystemDTO();
        dto.setName("Nimble Test");
        dto.setRulesMarkdown("## Combat\n- d20");
        dto.setCharacterTemplate(List.of(
                new TemplateFieldDTO("Histoire", "TEXT", null),
                new TemplateFieldDTO("PV", "NUMBER", null),
                new TemplateFieldDTO("Portrait", "IMAGE", "HERO")));
        dto.setNpcTemplate(List.of(
                new TemplateFieldDTO("Motivation", "TEXT", null)));

        mockMvc.perform(post("/api/game-systems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.characterTemplate.length()").value(3))
                .andExpect(jsonPath("$.characterTemplate[1].type").value("NUMBER"))
                .andExpect(jsonPath("$.characterTemplate[2].layout").value("HERO"))
                .andExpect(jsonPath("$.npcTemplate.length()").value(1))
                .andExpect(jsonPath("$.npcTemplate[0].name").value("Motivation"));
    }

    @Test
    void update_replacesTemplates() throws Exception {
        // Creation initiale avec un seul champ.
        GameSystemDTO dto = new GameSystemDTO();
        dto.setName("RuleSet");
        dto.setCharacterTemplate(List.of(new TemplateFieldDTO("Old", "TEXT", null)));

        MvcResult posted = mockMvc.perform(post("/api/game-systems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andReturn();

        GameSystemDTO created = objectMapper.readValue(
                posted.getResponse().getContentAsString(), GameSystemDTO.class);

        // Replace template integralement.
        created.setCharacterTemplate(List.of(
                new TemplateFieldDTO("Histoire", "TEXT", null),
                new TemplateFieldDTO("Niveau", "NUMBER", null)));

        mockMvc.perform(put("/api/game-systems/{id}", created.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(created)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.characterTemplate.length()").value(2))
                .andExpect(jsonPath("$.characterTemplate[0].name").value("Histoire"))
                .andExpect(jsonPath("$.characterTemplate[1].type").value("NUMBER"));

        // Verification que le GET independant retourne bien les nouveaux champs (pas de cache stale).
        mockMvc.perform(get("/api/game-systems/{id}", created.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.characterTemplate[?(@.name == 'Old')]").doesNotExist())
                .andExpect(jsonPath("$.characterTemplate[?(@.name == 'Histoire')]").exists());
    }

    @Test
    void create_rejectsDuplicateFieldNames() throws Exception {
        GameSystemDTO dto = new GameSystemDTO();
        dto.setName("BadRules");
        dto.setCharacterTemplate(List.of(
                new TemplateFieldDTO("Nom", "TEXT", null),
                new TemplateFieldDTO("nom", "NUMBER", null)));

        mockMvc.perform(post("/api/game-systems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void create_persistsEnemyTemplate() throws Exception {
        GameSystemDTO dto = new GameSystemDTO();
        dto.setName("Bestiaire");
        dto.setEnemyTemplate(List.of(
                new TemplateFieldDTO("Niveau", "NUMBER", null),
                new TemplateFieldDTO("Tactique", "TEXT", null)));

        mockMvc.perform(post("/api/game-systems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enemyTemplate.length()").value(2))
                .andExpect(jsonPath("$.enemyTemplate[0].name").value("Niveau"))
                .andExpect(jsonPath("$.enemyTemplate[1].type").value("TEXT"));
    }

    // --- CRUD : lecture / recherche / suppression ---------------------------

    @Test
    void getById_returns200() throws Exception {
        String id = createGameSystem("Lisible");
        mockMvc.perform(get("/api/game-systems/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Lisible"));
    }

    @Test
    void getById_returns404_whenMissing() throws Exception {
        mockMvc.perform(get("/api/game-systems/{id}", "999999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAll_returnsArray() throws Exception {
        createGameSystem("Systeme A");
        mockMvc.perform(get("/api/game-systems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void search_returnsMatching() throws Exception {
        createGameSystem("Dragonbane Unique");
        mockMvc.perform(get("/api/game-systems/search").param("q", "Dragonbane"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.name == 'Dragonbane Unique')]").exists());
    }

    @Test
    void delete_returns204_thenGone() throws Exception {
        String id = createGameSystem("A supprimer");
        mockMvc.perform(delete("/api/game-systems/{id}", id))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/game-systems/{id}", id))
                .andExpect(status().isNotFound());
    }

    // --- Import de regles (PDF) : multipart ---------------------------------

    @Test
    void importRules_returns200_withSections() throws Exception {
        when(rulesPdfImporter.importRules(any(), any()))
                .thenReturn(new RulesImportResult(Map.of("Combat", "## Combat\n- d20"), 5, 1));
        MockMultipartFile file = new MockMultipartFile(
                "file", "regles.pdf", "application/pdf", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/game-systems/import-rules").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageCount").value(5))
                .andExpect(jsonPath("$.ocrPageCount").value(1))
                .andExpect(jsonPath("$.sections.Combat").value("## Combat\n- d20"));
    }

    @Test
    void importRules_returns400_whenFileEmpty() throws Exception {
        MockMultipartFile empty = new MockMultipartFile(
                "file", "regles.pdf", "application/pdf", new byte[0]);
        mockMvc.perform(multipart("/api/game-systems/import-rules").file(empty))
                .andExpect(status().isBadRequest());
    }

    @Test
    void importRules_returns502_whenBrainFails() throws Exception {
        when(rulesPdfImporter.importRules(any(), any()))
                .thenThrow(new RulesImportException("Brain injoignable"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "regles.pdf", "application/pdf", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/game-systems/import-rules").file(file))
                .andExpect(status().isBadGateway());
    }

    // --- Import de regles streame (SSE) -------------------------------------

    @Test
    void importRulesStream_emitsProgressThenDone() throws Exception {
        doAnswer(inv -> {
            Consumer<RulesImportProgress> onProgress = inv.getArgument(2);
            Consumer<RulesImportResult> onDone = inv.getArgument(5);
            onProgress.accept(new RulesImportProgress(1, 2, 5, 0, List.of("Combat")));
            onDone.accept(new RulesImportResult(Map.of("Combat", "## Combat"), 5, 0));
            return null;
        }).when(rulesPdfImporter).importRulesStreaming(
                any(), any(), any(), any(), any(), any(), any());

        MockMultipartFile file = new MockMultipartFile(
                "file", "regles.pdf", "application/pdf", new byte[]{1, 2, 3});
        MvcResult result = mockMvc.perform(
                        multipart("/api/game-systems/import-rules/stream").file(file))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("progress")))
                .andExpect(content().string(containsString("done")))
                .andExpect(content().string(containsString("Combat")));
    }

    @Test
    void importRulesStream_emptyFile_emitsError() throws Exception {
        MockMultipartFile empty = new MockMultipartFile(
                "file", "regles.pdf", "application/pdf", new byte[0]);
        MvcResult result = mockMvc.perform(
                        multipart("/api/game-systems/import-rules/stream").file(empty))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("error")))
                .andExpect(content().string(containsString("vide")));
    }

    @Test
    void importRulesStream_brainError_emitsError() throws Exception {
        doAnswer(inv -> {
            Consumer<Throwable> onError = inv.getArgument(6);
            onError.accept(new RuntimeException("structuration KO"));
            return null;
        }).when(rulesPdfImporter).importRulesStreaming(
                any(), any(), any(), any(), any(), any(), any());

        MockMultipartFile file = new MockMultipartFile(
                "file", "regles.pdf", "application/pdf", new byte[]{1, 2, 3});
        MvcResult result = mockMvc.perform(
                        multipart("/api/game-systems/import-rules/stream").file(file))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("error")))
                .andExpect(content().string(containsString("structuration KO")));
    }

    /**
     * Couvre {@code sendImportHeartbeat} (callback onHeartbeat, arg 3) ET la branche
     * "status" de {@code sendImportEvent} (callback onStatus, arg 4) : un import qui
     * envoie un keepalive et un message de statut avant de produire son resultat.
     */
    @Test
    void importRulesStream_emitsHeartbeatAndStatus_thenDone() throws Exception {
        doAnswer(inv -> {
            Runnable onHeartbeat = inv.getArgument(3);
            Consumer<String> onStatus = inv.getArgument(4);
            Consumer<RulesImportResult> onDone = inv.getArgument(5);
            // Keepalive (commentaire SSE, ignore par le front) -> sendImportHeartbeat.
            onHeartbeat.run();
            // Message de statut lisible -> sendImportEvent(..., "status", ...).
            onStatus.accept("Fournisseur sature, nouvelle tentative...");
            onDone.accept(new RulesImportResult(Map.of("Combat", "## Combat"), 5, 0));
            return null;
        }).when(rulesPdfImporter).importRulesStreaming(
                any(), any(), any(), any(), any(), any(), any());

        MockMultipartFile file = new MockMultipartFile(
                "file", "regles.pdf", "application/pdf", new byte[]{1, 2, 3});
        MvcResult result = mockMvc.perform(
                        multipart("/api/game-systems/import-rules/stream").file(file))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                // Le commentaire keepalive est present dans le flux brut.
                .andExpect(content().string(containsString("keepalive")))
                // L'event "status" et son message sont serialises.
                .andExpect(content().string(containsString("status")))
                .andExpect(content().string(containsString("Fournisseur sature")))
                .andExpect(content().string(containsString("done")));
    }

    /**
     * Couvre la branche onStatus avec un message null : le controleur substitue une
     * chaine vide ({@code status != null ? status : ""}) sans planter.
     */
    @Test
    void importRulesStream_nullStatus_emitsEmptyStatus() throws Exception {
        doAnswer(inv -> {
            Consumer<String> onStatus = inv.getArgument(4);
            Consumer<RulesImportResult> onDone = inv.getArgument(5);
            onStatus.accept(null);
            onDone.accept(new RulesImportResult(Map.of("Combat", "## Combat"), 5, 0));
            return null;
        }).when(rulesPdfImporter).importRulesStreaming(
                any(), any(), any(), any(), any(), any(), any());

        MockMultipartFile file = new MockMultipartFile(
                "file", "regles.pdf", "application/pdf", new byte[]{1, 2, 3});
        MvcResult result = mockMvc.perform(
                        multipart("/api/game-systems/import-rules/stream").file(file))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("status")))
                .andExpect(content().string(containsString("done")));
    }
}
