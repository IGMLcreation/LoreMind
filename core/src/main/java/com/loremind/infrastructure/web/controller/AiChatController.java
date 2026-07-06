package com.loremind.infrastructure.web.controller;

import com.loremind.application.generationcontext.StreamChatForCampaignUseCase;
import com.loremind.application.generationcontext.StreamChatForLoreUseCase;
import com.loremind.application.generationcontext.StreamChatForSessionUseCase;
import com.loremind.domain.generationcontext.ChatMessage;
import com.loremind.domain.generationcontext.ChatStreamCallbacks;
import com.loremind.domain.generationcontext.ChatUsage;
import com.loremind.infrastructure.web.dto.generationcontext.ChatMessageDTO;
import com.loremind.infrastructure.web.dto.generationcontext.ChatStreamCampaignRequestDTO;
import com.loremind.infrastructure.web.dto.generationcontext.ChatStreamRequestDTO;
import com.loremind.infrastructure.web.dto.generationcontext.ChatStreamSessionRequestDTO;
import com.loremind.infrastructure.web.sse.SseJson;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

/**
 * REST Controller pour le chat IA streamé (Server-Sent Events).
 * <p>
 * Deux endpoints :
 *  - POST /api/ai/chat/stream           → chat ancré sur un Lore
 *  - POST /api/ai/chat/stream-campaign  → chat ancré sur une Campagne
 *                                         (qui tire automatiquement son Lore)
 * <p>
 * Le streaming est lancé dans un thread séparé (AsyncTaskExecutor) pour
 * ne pas bloquer le thread servlet pendant toute la durée de la génération.
 * SseEmitter est thread-safe : les callbacks du port AiChatProvider peuvent
 * écrire directement dessus depuis n'importe quel thread.
 */
@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    /** Timeout SSE long — les modèles LLM locaux peuvent générer pendant quelques minutes. */
    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L;

    private final StreamChatForLoreUseCase streamChatForLoreUseCase;
    private final StreamChatForCampaignUseCase streamChatForCampaignUseCase;
    private final StreamChatForSessionUseCase streamChatForSessionUseCase;
    private final TaskExecutor taskExecutor;

    public AiChatController(
            StreamChatForLoreUseCase streamChatForLoreUseCase,
            StreamChatForCampaignUseCase streamChatForCampaignUseCase,
            StreamChatForSessionUseCase streamChatForSessionUseCase,
            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        this.streamChatForLoreUseCase = streamChatForLoreUseCase;
        this.streamChatForCampaignUseCase = streamChatForCampaignUseCase;
        this.streamChatForSessionUseCase = streamChatForSessionUseCase;
        this.taskExecutor = taskExecutor;
    }

    // --- Endpoints ----------------------------------------------------------

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatStreamRequestDTO body) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        List<ChatMessage> messages = toDomainMessages(body.getMessages());

        taskExecutor.execute(() -> runLoreStreaming(emitter, body.getLoreId(), body.getPageId(), messages));
        return emitter;
    }

    @PostMapping(value = "/chat/stream-campaign", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStreamCampaign(@RequestBody ChatStreamCampaignRequestDTO body) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        List<ChatMessage> messages = toDomainMessages(body.getMessages());

        taskExecutor.execute(() -> runCampaignStreaming(
                emitter, body.getCampaignId(), body.getEntityType(), body.getEntityId(), messages));
        return emitter;
    }

    /**
     * Chat IA ancré sur une Session de jeu : récupère automatiquement la
     * Campagne / Lore / GameSystem associés + injecte le journal horodaté.
     */
    @PostMapping(value = "/chat/stream-session", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStreamSession(@RequestBody ChatStreamSessionRequestDTO body) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        List<ChatMessage> messages = toDomainMessages(body.getMessages());

        taskExecutor.execute(() -> runSessionStreaming(emitter, body.getSessionId(), messages));
        return emitter;
    }

    // --- Exécution du streaming dans un thread dédié ------------------------

    private void runLoreStreaming(
            SseEmitter emitter, String loreId, String pageId, List<ChatMessage> messages) {
        try {
            streamChatForLoreUseCase.execute(loreId, pageId, messages, callbacksFor(emitter));
        } catch (Exception e) {
            // Inclut IllegalArgumentException (Lore ou Page introuvable) : dans tous
            // les cas on envoie un event error puis on termine proprement.
            fail(emitter, e);
        }
    }

    private void runCampaignStreaming(
            SseEmitter emitter,
            String campaignId,
            String entityType,
            String entityId,
            List<ChatMessage> messages) {
        try {
            streamChatForCampaignUseCase.execute(
                    campaignId, entityType, entityId, messages, callbacksFor(emitter));
        } catch (Exception e) {
            fail(emitter, e);
        }
    }

    private void runSessionStreaming(
            SseEmitter emitter,
            String sessionId,
            List<ChatMessage> messages) {
        try {
            streamChatForSessionUseCase.execute(sessionId, messages, callbacksFor(emitter));
        } catch (Exception e) {
            fail(emitter, e);
        }
    }

    /** Callbacks de streaming qui écrivent chaque évènement sur cet emitter SSE. */
    private ChatStreamCallbacks callbacksFor(SseEmitter emitter) {
        return new ChatStreamCallbacks(
                usage -> sendUsage(emitter, usage),
                token -> sendToken(emitter, token),
                () -> complete(emitter),
                error -> fail(emitter, error));
    }

    // --- Helpers SSE (un seul point d'écriture par type d'événement) --------

    private void sendUsage(SseEmitter emitter, ChatUsage usage) {
        try {
            String payload = "{\"system\":" + usage.system()
                    + ",\"history\":" + usage.history()
                    + ",\"current\":" + usage.current()
                    + ",\"max\":" + usage.max() + "}";
            emitter.send(SseEmitter.event().name("usage").data(payload));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private void sendToken(SseEmitter emitter, String token) {
        try {
            emitter.send(SseEmitter.event()
                    .data("{\"token\":" + SseJson.escape(token) + "}"));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private void complete(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("done").data("{}"));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private void fail(SseEmitter emitter, Throwable error) {
        try {
            String message = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data("{\"message\":" + SseJson.escape(message) + "}"));
            emitter.complete();
        } catch (IOException ioe) {
            emitter.completeWithError(ioe);
        }
    }

    // --- Utilitaires --------------------------------------------------------

    private List<ChatMessage> toDomainMessages(List<ChatMessageDTO> dtos) {
        if (dtos == null) return List.of();
        return dtos.stream()
                .map(dto -> new ChatMessage(dto.getRole(), dto.getContent()))
                .toList();
    }
}
