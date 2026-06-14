package com.loremind.infrastructure.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.application.generationcontext.StreamChatForCampaignUseCase;
import com.loremind.application.generationcontext.StreamChatForLoreUseCase;
import com.loremind.application.generationcontext.StreamChatForSessionUseCase;
import com.loremind.domain.generationcontext.ChatMessage;
import com.loremind.domain.generationcontext.ChatUsage;
import com.loremind.infrastructure.web.dto.generationcontext.ChatMessageDTO;
import com.loremind.infrastructure.web.dto.generationcontext.ChatStreamCampaignRequestDTO;
import com.loremind.infrastructure.web.dto.generationcontext.ChatStreamRequestDTO;
import com.loremind.infrastructure.web.dto.generationcontext.ChatStreamSessionRequestDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.function.Consumer;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'integration pour {@link AiChatController} (chat IA streame en SSE).
 * <p>
 * Les trois use cases ({@link StreamChatForLoreUseCase},
 * {@link StreamChatForCampaignUseCase}, {@link StreamChatForSessionUseCase}) sont
 * mockes : sinon chaque test ferait un vrai appel au Brain (indisponible en test).
 * <p>
 * Le {@code TaskExecutor} ("applicationTaskExecutor") est mocke pour executer la
 * tache de streaming EN LIGNE (synchrone) : tous les events SSE sont ecrits avant
 * le retour du controleur, ce qui rend les assertions sur le flux deterministes.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AiChatControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private StreamChatForLoreUseCase loreUseCase;
    @MockBean private StreamChatForCampaignUseCase campaignUseCase;
    @MockBean private StreamChatForSessionUseCase sessionUseCase;
    @MockBean(name = "applicationTaskExecutor") private TaskExecutor taskExecutor;

    @BeforeEach
    void setUp() {
        // Tache de streaming executee en ligne -> events SSE deterministes.
        doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; })
                .when(taskExecutor).execute(any(Runnable.class));
    }

    private MvcResult perform(String url, Object body) throws Exception {
        return mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    // --- /chat/stream (Lore) ------------------------------------------------

    @Test
    void chatStream_streamsUsageTokenDone() throws Exception {
        // Le use case mocke joue : usage -> 1 token -> fin.
        doAnswer(inv -> {
            Consumer<ChatUsage> onUsage = inv.getArgument(3);
            Consumer<String> onToken = inv.getArgument(4);
            Runnable onComplete = inv.getArgument(5);
            onUsage.accept(new ChatUsage(10, 20, 30, 8000));
            onToken.accept("Bonjour");
            onComplete.run();
            return null;
        }).when(loreUseCase).execute(any(), any(), any(), any(), any(), any(), any());

        ChatStreamRequestDTO body = new ChatStreamRequestDTO();
        body.setLoreId("lore-1");
        body.setMessages(List.of(new ChatMessageDTO("user", "Salut ?")));

        MvcResult result = perform("/api/ai/chat/stream", body);

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"system\":10")))
                .andExpect(content().string(containsString("\"max\":8000")))
                .andExpect(content().string(containsString("Bonjour")))
                .andExpect(content().string(containsString("done")));
    }

    @Test
    void chatStream_passesDomainMessagesToUseCase() throws Exception {
        doAnswer(inv -> { ((Runnable) inv.getArgument(5)).run(); return null; })
                .when(loreUseCase).execute(any(), any(), any(), any(), any(), any(), any());

        ChatStreamRequestDTO body = new ChatStreamRequestDTO();
        body.setLoreId("lore-1");
        body.setPageId("page-9");
        body.setMessages(List.of(
                new ChatMessageDTO("user", "Q1"),
                new ChatMessageDTO("assistant", "R1")));

        MvcResult result = perform("/api/ai/chat/stream", body);
        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<ChatMessage>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(loreUseCase).execute(
                org.mockito.ArgumentMatchers.eq("lore-1"),
                org.mockito.ArgumentMatchers.eq("page-9"),
                captor.capture(), any(), any(), any(), any());
        List<ChatMessage> passed = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(2, passed.size());
        org.junit.jupiter.api.Assertions.assertEquals("user", passed.get(0).role());
        org.junit.jupiter.api.Assertions.assertEquals("Q1", passed.get(0).content());
    }

    @Test
    void chatStream_useCaseInvokesError_emitsError() throws Exception {
        doAnswer(inv -> {
            Consumer<Throwable> onError = inv.getArgument(6);
            onError.accept(new RuntimeException("brain HS"));
            return null;
        }).when(loreUseCase).execute(any(), any(), any(), any(), any(), any(), any());

        ChatStreamRequestDTO body = new ChatStreamRequestDTO();
        body.setLoreId("lore-1");

        MvcResult result = perform("/api/ai/chat/stream", body);
        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("error")))
                .andExpect(content().string(containsString("brain HS")));
    }

    @Test
    void chatStream_useCaseThrows_emitsError() throws Exception {
        // Lore introuvable -> le use case leve, le controller catch et fail(emitter).
        doThrow(new IllegalArgumentException("Lore introuvable"))
                .when(loreUseCase).execute(any(), any(), any(), any(), any(), any(), any());

        ChatStreamRequestDTO body = new ChatStreamRequestDTO();
        body.setLoreId("missing");

        MvcResult result = perform("/api/ai/chat/stream", body);
        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Lore introuvable")));
    }

    // --- /chat/stream-campaign ----------------------------------------------

    @Test
    void chatStreamCampaign_streamsTokenThenDone() throws Exception {
        doAnswer(inv -> {
            Consumer<String> onToken = inv.getArgument(5);
            Runnable onComplete = inv.getArgument(6);
            onToken.accept("Campagne");
            onComplete.run();
            return null;
        }).when(campaignUseCase).execute(any(), any(), any(), any(), any(), any(), any(), any());

        ChatStreamCampaignRequestDTO body = new ChatStreamCampaignRequestDTO();
        body.setCampaignId("camp-1");
        body.setEntityType("scene");
        body.setEntityId("scene-3");
        body.setMessages(List.of(new ChatMessageDTO("user", "Aide")));

        MvcResult result = perform("/api/ai/chat/stream-campaign", body);
        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Campagne")))
                .andExpect(content().string(containsString("done")));
    }

    @Test
    void chatStreamCampaign_useCaseThrows_emitsError() throws Exception {
        doThrow(new IllegalArgumentException("Campagne introuvable"))
                .when(campaignUseCase).execute(any(), any(), any(), any(), any(), any(), any(), any());

        ChatStreamCampaignRequestDTO body = new ChatStreamCampaignRequestDTO();
        body.setCampaignId("missing");

        MvcResult result = perform("/api/ai/chat/stream-campaign", body);
        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Campagne introuvable")));
    }

    // --- /chat/stream-session -----------------------------------------------

    @Test
    void chatStreamSession_streamsTokenThenDone() throws Exception {
        doAnswer(inv -> {
            Consumer<String> onToken = inv.getArgument(3);
            Runnable onComplete = inv.getArgument(4);
            onToken.accept("Session");
            onComplete.run();
            return null;
        }).when(sessionUseCase).execute(any(), any(), any(), any(), any(), any());

        ChatStreamSessionRequestDTO body = new ChatStreamSessionRequestDTO();
        body.setSessionId("sess-1");
        body.setMessages(List.of(new ChatMessageDTO("user", "Resume")));

        MvcResult result = perform("/api/ai/chat/stream-session", body);
        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Session")))
                .andExpect(content().string(containsString("done")));
    }

    @Test
    void chatStreamSession_useCaseThrows_emitsError() throws Exception {
        doThrow(new IllegalArgumentException("Session introuvable"))
                .when(sessionUseCase).execute(any(), any(), any(), any(), any(), any());

        ChatStreamSessionRequestDTO body = new ChatStreamSessionRequestDTO();
        body.setSessionId("missing");

        MvcResult result = perform("/api/ai/chat/stream-session", body);
        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Session introuvable")));
    }
}
