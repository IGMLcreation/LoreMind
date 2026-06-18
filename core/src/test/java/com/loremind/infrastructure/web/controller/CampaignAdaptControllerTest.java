package com.loremind.infrastructure.web.controller;

import com.loremind.application.campaigncontext.CampaignAdaptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'integration pour {@link CampaignAdaptController} (conseil PDF -> campagne, SSE).
 * <p>
 * Le {@link CampaignAdaptService} est mocke : il appelle le Brain (indisponible en
 * test). Le {@code TaskExecutor} ("applicationTaskExecutor") est mocke pour executer
 * la tache de streaming EN LIGNE -> events SSE deterministes.
 * <p>
 * Signature de {@code adviseStreaming(campaignId, pdfBytes, filename, messagesJson,
 * onToken, onComplete, onError)} : indices des callbacks -> onToken=4, onComplete=5,
 * onError=6.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CampaignAdaptControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private CampaignAdaptService campaignAdaptService;
    @MockitoBean(name = "applicationTaskExecutor") private TaskExecutor taskExecutor;

    @BeforeEach
    void setUp() {
        // Tache de streaming executee en ligne -> events SSE deterministes.
        doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; })
                .when(taskExecutor).execute(any(Runnable.class));
    }

    private MockMultipartFile pdf(byte[] content) {
        return new MockMultipartFile("file", "aventure.pdf", "application/pdf", content);
    }

    private MvcResult perform(MockMultipartFile file) throws Exception {
        return mockMvc.perform(multipart("/api/campaigns/{id}/adapt-pdf/stream", "camp-1")
                        .file(file)
                        .param("messages", "[]"))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    @Test
    void adaptStream_streamsTokenThenDone() throws Exception {
        // Le service mocke joue : 1 token -> fin.
        doAnswer(inv -> {
            Consumer<String> onToken = inv.getArgument(4);
            Runnable onComplete = inv.getArgument(5);
            onToken.accept("Conseil markdown");
            onComplete.run();
            return null;
        }).when(campaignAdaptService).adviseStreaming(any(), any(), any(), any(), any(), any(), any());

        MvcResult result = perform(pdf("%PDF-1.4 fake".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Conseil markdown")))
                .andExpect(content().string(containsString("done")));
    }

    @Test
    void adaptStream_serviceInvokesError_emitsError() throws Exception {
        doAnswer(inv -> {
            Consumer<Throwable> onError = inv.getArgument(6);
            onError.accept(new RuntimeException("brain HS"));
            return null;
        }).when(campaignAdaptService).adviseStreaming(any(), any(), any(), any(), any(), any(), any());

        MvcResult result = perform(pdf("%PDF-1.4 fake".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("error")))
                .andExpect(content().string(containsString("brain HS")));
    }

    @Test
    void adaptStream_campaignMissing_emitsError() throws Exception {
        // Le service leve IllegalArgumentException -> le controller catch et envoie "Campagne introuvable.".
        doThrow(new IllegalArgumentException("Campagne introuvable : camp-1"))
                .when(campaignAdaptService).adviseStreaming(any(), any(), any(), any(), any(), any(), any());

        MvcResult result = perform(pdf("%PDF-1.4 fake".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("error")))
                .andExpect(content().string(containsString("Campagne introuvable")));
    }

    @Test
    void adaptStream_emptyFile_emitsError_withoutCallingService() throws Exception {
        // Fichier vide : branche court-circuit, le service n'est jamais appele.
        MvcResult result = mockMvc.perform(
                        multipart("/api/campaigns/{id}/adapt-pdf/stream", "camp-1")
                                .file(new MockMultipartFile("file", "vide.pdf", "application/pdf", new byte[0])))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("error")))
                .andExpect(content().string(containsString("Fichier PDF vide")));
    }
}
