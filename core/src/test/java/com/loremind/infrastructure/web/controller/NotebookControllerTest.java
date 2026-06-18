package com.loremind.infrastructure.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.Notebook;
import com.loremind.domain.campaigncontext.NotebookSource;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.campaigncontext.ports.NotebookChatStreamer;
import com.loremind.domain.campaigncontext.ports.NotebookException;
import com.loremind.domain.campaigncontext.ports.NotebookIndexer;
import com.loremind.domain.campaigncontext.ports.NotebookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
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
 * Tests d'integration pour {@link NotebookController} (atelier RAG).
 * <p>
 * Les ports vers le Brain sont mockes : {@link NotebookIndexer} (indexation des
 * sources) et {@link NotebookChatStreamer} (chat ancre streame), sinon chaque test
 * ferait un vrai appel HTTP au Brain (indisponible en test).
 * <p>
 * Le {@code TaskExecutor} ("applicationTaskExecutor") est egalement mocke pour
 * executer la tache du chat stream EN LIGNE (synchrone) : tous les events SSE sont
 * ainsi ecrits avant le retour du controleur, ce qui rend les assertions sur le
 * flux deterministes (pas de course entre threads).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotebookControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private NotebookRepository notebookRepository;
    @Autowired private CampaignRepository campaignRepository;

    @MockitoBean private NotebookIndexer indexer;
    @MockitoBean private NotebookChatStreamer chatStreamer;
    @MockitoBean(name = "applicationTaskExecutor") private TaskExecutor taskExecutor;

    private String campaignId;

    @BeforeEach
    void setUp() {
        campaignId = campaignRepository.save(
                Campaign.builder().name("C").description("").build()).getId();
        // Tache du chat stream executee en ligne -> events SSE deterministes.
        doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; })
                .when(taskExecutor).execute(any(Runnable.class));
    }

    private Notebook persistNotebook() {
        return notebookRepository.save(
                Notebook.builder().campaignId(campaignId).name("Atelier").build());
    }

    // --- Notebooks (CRUD) ---

    @Test
    void create_returns200() throws Exception {
        var req = new NotebookController.CreateRequest(campaignId, "Mon atelier");
        mockMvc.perform(post("/api/notebooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Mon atelier"))
                .andExpect(jsonPath("$.campaignId").value(campaignId));
    }

    @Test
    void create_blankName_fallsBackToDefault() throws Exception {
        var req = new NotebookController.CreateRequest(campaignId, "   ");
        mockMvc.perform(post("/api/notebooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nouvel atelier"));
    }

    @Test
    void listByCampaign_returnsArray() throws Exception {
        persistNotebook();
        mockMvc.perform(get("/api/notebooks/campaign/{campaignId}", campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].campaignId").value(campaignId));
    }

    @Test
    void get_returns200_withSourcesAndMessages() throws Exception {
        Notebook nb = persistNotebook();
        mockMvc.perform(get("/api/notebooks/{id}", nb.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(nb.getId()))
                .andExpect(jsonPath("$.name").value("Atelier"))
                .andExpect(jsonPath("$.sources").isArray())
                .andExpect(jsonPath("$.messages").isArray());
    }

    @Test
    void get_returns404_whenMissing() throws Exception {
        mockMvc.perform(get("/api/notebooks/{id}", "999999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rename_returns200() throws Exception {
        Notebook nb = persistNotebook();
        var req = new NotebookController.RenameRequest("Renomme");
        mockMvc.perform(put("/api/notebooks/{id}", nb.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renomme"));
    }

    @Test
    void delete_returns204() throws Exception {
        Notebook nb = persistNotebook();
        mockMvc.perform(delete("/api/notebooks/{id}", nb.getId()))
                .andExpect(status().isNoContent());
    }

    // --- Sources ---

    @Test
    void addSource_returns200_andIndexes() throws Exception {
        Notebook nb = persistNotebook();
        when(indexer.index(any(), any(), any()))
                .thenReturn(new NotebookIndexer.IndexResult(12, 3, 0));
        MockMultipartFile file = new MockMultipartFile(
                "file", "livre.pdf", "application/pdf", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/notebooks/{id}/sources", nb.getId()).file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("livre.pdf"))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.chunkCount").value(12))
                .andExpect(jsonPath("$.pageCount").value(3));
    }

    @Test
    void addSource_returns502_whenBrainFails() throws Exception {
        Notebook nb = persistNotebook();
        when(indexer.index(any(), any(), any()))
                .thenThrow(new NotebookException("Brain injoignable"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "livre.pdf", "application/pdf", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/notebooks/{id}/sources", nb.getId()).file(file))
                .andExpect(status().isBadGateway());
    }

    @Test
    void deleteSource_returns204() throws Exception {
        Notebook nb = persistNotebook();
        NotebookSource src = notebookRepository.saveSource(NotebookSource.builder()
                .notebookId(nb.getId()).filename("s.pdf").status("READY").build());
        mockMvc.perform(delete("/api/notebooks/sources/{sourceId}", src.getId()))
                .andExpect(status().isNoContent());
    }

    // --- Conversation : vider (archiver) + archives ---

    @Test
    void clearChat_returns204() throws Exception {
        Notebook nb = persistNotebook();
        mockMvc.perform(post("/api/notebooks/{id}/chat/clear", nb.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    void clearChat_returns404_whenMissing() throws Exception {
        mockMvc.perform(post("/api/notebooks/{id}/chat/clear", "999999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listArchives_returnsArray() throws Exception {
        Notebook nb = persistNotebook();
        mockMvc.perform(get("/api/notebooks/{id}/chat/archives", nb.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // --- Chat ancre streame (SSE) ---

    @Test
    void chatStream_happyPath_streamsTokenThenDone() throws Exception {
        Notebook nb = persistNotebook();
        // Le streamer mocke joue : 1 token puis fin.
        doAnswer(inv -> {
            java.util.function.Consumer<String> onToken = inv.getArgument(5);
            Runnable onDone = inv.getArgument(7);
            onToken.accept("Bonjour");
            onDone.run();
            return null;
        }).when(chatStreamer).stream(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(),
                any(), any(), any(), any(), any());

        var req = new NotebookController.ChatRequest("Salut ?", false, null, null);
        MvcResult result = mockMvc.perform(post("/api/notebooks/{id}/chat/stream", nb.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bonjour")))
                .andExpect(content().string(containsString("done")));

        // La reponse de l'assistant a ete persistee a la fin du stream.
        boolean persisted = notebookRepository.findMessagesByNotebookId(nb.getId()).stream()
                .anyMatch(m -> "assistant".equals(m.getRole()) && "Bonjour".equals(m.getContent()));
        org.junit.jupiter.api.Assertions.assertTrue(persisted, "reponse assistant persistee");
    }

    @Test
    void chatStream_emptyMessage_emitsError() throws Exception {
        Notebook nb = persistNotebook();
        var req = new NotebookController.ChatRequest("   ", false, null, null);
        MvcResult result = mockMvc.perform(post("/api/notebooks/{id}/chat/stream", nb.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("error")));
    }

    @Test
    void chatStream_streamerError_emitsError() throws Exception {
        Notebook nb = persistNotebook();
        doAnswer(inv -> {
            java.util.function.Consumer<Throwable> onError = inv.getArgument(8);
            onError.accept(new RuntimeException("boom"));
            return null;
        }).when(chatStreamer).stream(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(),
                any(), any(), any(), any(), any());

        var req = new NotebookController.ChatRequest("Salut ?", false, null, null);
        MvcResult result = mockMvc.perform(post("/api/notebooks/{id}/chat/stream", nb.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("boom")));
    }

    @Test
    void chatStream_returns404_whenMissing() throws Exception {
        var req = new NotebookController.ChatRequest("Salut ?", false, null, null);
        mockMvc.perform(post("/api/notebooks/{id}/chat/stream", "999999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void chatStream_deepMode_emitsSourcesAndProgress() throws Exception {
        Notebook nb = persistNotebook();
        // Source PRETE -> remontee par readySourceIds, donc selectionnable via sourceIds.
        NotebookSource src = notebookRepository.saveSource(NotebookSource.builder()
                .notebookId(nb.getId()).filename("s.pdf").status("READY").build());

        // Le streamer mocke joue, en mode approfondi : sources -> progress -> token -> fin.
        doAnswer(inv -> {
            java.util.function.Consumer<String> onSourcesJson = inv.getArgument(4);
            java.util.function.Consumer<String> onToken = inv.getArgument(5);
            java.util.function.Consumer<NotebookChatStreamer.Progress> onProgress = inv.getArgument(6);
            Runnable onDone = inv.getArgument(7);
            onSourcesJson.accept("{\"sources\":[{\"source_id\":\"" + src.getId() + "\",\"page\":1}]}");
            onProgress.accept(new NotebookChatStreamer.Progress(1, 3));
            onToken.accept("Reponse");
            onDone.run();
            return null;
        }).when(chatStreamer).stream(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(),
                any(), any(), any(), any(), any());

        // deep=true + sourceIds (filtrage) + archiveIds non nuls (branche buildArchiveContext).
        var req = new NotebookController.ChatRequest(
                "Analyse complete ?", true,
                java.util.List.of(src.getId()), java.util.List.of("2020-01-01T00:00"));
        MvcResult result = mockMvc.perform(post("/api/notebooks/{id}/chat/stream", nb.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("sources")))
                .andExpect(content().string(containsString("progress")))
                .andExpect(content().string(containsString("Reponse")));
    }
}
