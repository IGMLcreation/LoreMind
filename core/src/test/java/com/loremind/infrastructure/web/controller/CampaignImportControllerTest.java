package com.loremind.infrastructure.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.application.campaigncontext.CampaignImportService;
import com.loremind.application.campaigncontext.CampaignImportService.ApplyResult;
import com.loremind.domain.campaigncontext.CampaignImportProgress;
import com.loremind.domain.campaigncontext.CampaignImportProposal;
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

import java.util.List;
import java.util.function.Consumer;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'integration pour {@link CampaignImportController} (import PDF -> arbre).
 * <p>
 * Le {@link CampaignImportService} est mocke : son {@code importStructureStreaming}
 * delegue sinon au Brain Python (indisponible en test) et {@code applyStructure}
 * persiste en base. On controle ici les callbacks (progress / done / error) du
 * streaming et le mapping HTTP du apply.
 * <p>
 * Le {@code TaskExecutor} ("applicationTaskExecutor") est mocke pour executer la
 * tache d'import EN LIGNE (synchrone) : tous les events SSE sont ecrits avant le
 * retour du controleur, rendant les assertions sur le flux deterministes.
 * <p>
 * Indices des callbacks de
 * {@link CampaignImportService#importStructureStreaming} :
 * (0) pdfBytes, (1) filename, (2) onProgress, (3) onHeartbeat, (4) onStatus,
 * (5) onDone, (6) onError.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CampaignImportControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private CampaignImportService campaignImportService;
    @MockitoBean(name = "applicationTaskExecutor") private TaskExecutor taskExecutor;

    private static final String CAMPAIGN_ID = "camp-1";

    @BeforeEach
    void setUp() {
        // Tache d'import executee en ligne -> events SSE deterministes.
        doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; })
                .when(taskExecutor).execute(any(Runnable.class));
    }

    private MockMultipartFile pdf(byte[] bytes) {
        return new MockMultipartFile("file", "campagne.pdf", "application/pdf", bytes);
    }

    // --- POST /stream (SSE) ------------------------------------------------

    @Test
    void importStream_emptyFile_emitsError() throws Exception {
        MockMultipartFile empty = pdf(new byte[0]);
        MvcResult result = mockMvc.perform(
                        multipart("/api/campaigns/{id}/import-structure/stream", CAMPAIGN_ID).file(empty))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("error")))
                .andExpect(content().string(containsString("vide")));
    }

    @Test
    void importStream_happyPath_streamsDone() throws Exception {
        // Le service mocke joue : onDone avec une proposition vide.
        doAnswer(inv -> {
            Consumer<CampaignImportProposal> onDone = inv.getArgument(5);
            onDone.accept(new CampaignImportProposal(List.of(), List.of()));
            return null;
        }).when(campaignImportService).importStructureStreaming(
                any(), any(), any(), any(), any(), any(), any());

        MvcResult result = mockMvc.perform(
                        multipart("/api/campaigns/{id}/import-structure/stream", CAMPAIGN_ID)
                                .file(pdf(new byte[]{1, 2, 3})))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("done")));
    }

    @Test
    void importStream_serviceError_emitsError() throws Exception {
        // Le service mocke joue : onError avec un message.
        doAnswer(inv -> {
            Consumer<Throwable> onError = inv.getArgument(6);
            onError.accept(new RuntimeException("Brain injoignable"));
            return null;
        }).when(campaignImportService).importStructureStreaming(
                any(), any(), any(), any(), any(), any(), any());

        MvcResult result = mockMvc.perform(
                        multipart("/api/campaigns/{id}/import-structure/stream", CAMPAIGN_ID)
                                .file(pdf(new byte[]{1, 2, 3})))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("error")))
                .andExpect(content().string(containsString("Brain injoignable")));
    }

    @Test
    void importStream_thrownException_emitsError() throws Exception {
        // Le service leve directement (catch general du controleur).
        doAnswer(inv -> { throw new RuntimeException("boom extraction"); })
                .when(campaignImportService).importStructureStreaming(
                        any(), any(), any(), any(), any(), any(), any());

        MvcResult result = mockMvc.perform(
                        multipart("/api/campaigns/{id}/import-structure/stream", CAMPAIGN_ID)
                                .file(pdf(new byte[]{1, 2, 3})))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("boom extraction")));
    }

    @Test
    void importStream_progressAndStatus_streamedBeforeDone() throws Exception {
        // Joue la sequence complete des callbacks intermediaires (progress / status /
        // heartbeat) puis onDone -> couvre sendEvent("progress"), sendEvent("status")
        // et sendHeartbeat (helpers SSE non touches par les tests precedents).
        doAnswer(inv -> {
            Consumer<CampaignImportProgress> onProgress = inv.getArgument(2);
            Runnable onHeartbeat = inv.getArgument(3);
            Consumer<String> onStatus = inv.getArgument(4);
            Consumer<CampaignImportProposal> onDone = inv.getArgument(5);
            onProgress.accept(new CampaignImportProgress(2, 10, 5, 1, 1, 2, 3, 4));
            onStatus.accept("Analyse en cours");
            onHeartbeat.run();
            onDone.accept(new CampaignImportProposal(List.of(), List.of()));
            return null;
        }).when(campaignImportService).importStructureStreaming(
                any(), any(), any(), any(), any(), any(), any());

        MvcResult result = mockMvc.perform(
                        multipart("/api/campaigns/{id}/import-structure/stream", CAMPAIGN_ID)
                                .file(pdf(new byte[]{1, 2, 3})))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("progress")))
                .andExpect(content().string(containsString("status")))
                .andExpect(content().string(containsString("Analyse en cours")))
                .andExpect(content().string(containsString("keepalive")))
                .andExpect(content().string(containsString("done")));
    }

    @Test
    void importStream_nullStatus_emitsEmptyMessage() throws Exception {
        // onStatus(null) -> branche "status != null ? status : \"\"" du controleur.
        doAnswer(inv -> {
            Consumer<String> onStatus = inv.getArgument(4);
            Consumer<CampaignImportProposal> onDone = inv.getArgument(5);
            onStatus.accept(null);
            onDone.accept(new CampaignImportProposal(List.of(), List.of()));
            return null;
        }).when(campaignImportService).importStructureStreaming(
                any(), any(), any(), any(), any(), any(), any());

        MvcResult result = mockMvc.perform(
                        multipart("/api/campaigns/{id}/import-structure/stream", CAMPAIGN_ID)
                                .file(pdf(new byte[]{1, 2, 3})))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("status")))
                .andExpect(content().string(containsString("done")));
    }

    @Test
    void importStream_nullErrorMessage_emitsFallbackMessage() throws Exception {
        // onError avec un message null -> sendError utilise "Erreur inconnue.".
        doAnswer(inv -> {
            Consumer<Throwable> onError = inv.getArgument(6);
            onError.accept(new RuntimeException());  // getMessage() == null
            return null;
        }).when(campaignImportService).importStructureStreaming(
                any(), any(), any(), any(), any(), any(), any());

        MvcResult result = mockMvc.perform(
                        multipart("/api/campaigns/{id}/import-structure/stream", CAMPAIGN_ID)
                                .file(pdf(new byte[]{1, 2, 3})))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Erreur inconnue")));
    }

    @Test
    void importStream_serviceThrows_emitsError() throws Exception {
        // Le service LEVE (au lieu d'invoquer onError) : le catch(Exception) du
        // controleur relaie le message via sendError.
        doThrow(new RuntimeException("panne interne"))
                .when(campaignImportService).importStructureStreaming(
                        any(), any(), any(), any(), any(), any(), any());

        MvcResult result = mockMvc.perform(
                        multipart("/api/campaigns/{id}/import-structure/stream", CAMPAIGN_ID)
                                .file(pdf(new byte[]{1, 2, 3})))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("error")))
                .andExpect(content().string(containsString("panne interne")));
    }

    // --- POST /apply -------------------------------------------------------

    @Test
    void apply_returns200_withSummary() throws Exception {
        when(campaignImportService.applyStructure(eq(CAMPAIGN_ID), any()))
                .thenReturn(new ApplyResult(1, 2, 3, 4));
        CampaignImportProposal proposal = new CampaignImportProposal(List.of(), List.of());

        mockMvc.perform(post("/api/campaigns/{id}/import-structure/apply", CAMPAIGN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(proposal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arcsCreated").value(1))
                .andExpect(jsonPath("$.chaptersCreated").value(2))
                .andExpect(jsonPath("$.scenesCreated").value(3))
                .andExpect(jsonPath("$.npcsCreated").value(4));
    }

    @Test
    void apply_returns404_whenCampaignMissing() throws Exception {
        when(campaignImportService.applyStructure(any(), any()))
                .thenThrow(new IllegalArgumentException("Campagne introuvable"));
        CampaignImportProposal proposal = new CampaignImportProposal(List.of(), List.of());

        mockMvc.perform(post("/api/campaigns/{id}/import-structure/apply", CAMPAIGN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(proposal)))
                .andExpect(status().isNotFound());
    }
}
