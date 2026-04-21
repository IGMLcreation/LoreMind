package com.loremind.infrastructure.web.controller;

import com.loremind.application.generationcontext.StreamChatForCampaignUseCase;
import com.loremind.application.generationcontext.StreamChatForLoreUseCase;
import com.loremind.domain.generationcontext.ChatMessage;
import com.loremind.infrastructure.web.dto.generationcontext.ChatMessageDTO;
import com.loremind.infrastructure.web.dto.generationcontext.ChatStreamCampaignRequestDTO;
import com.loremind.infrastructure.web.dto.generationcontext.ChatStreamRequestDTO;
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
import java.util.stream.Collectors;

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
    private final TaskExecutor taskExecutor;

    public AiChatController(
            StreamChatForLoreUseCase streamChatForLoreUseCase,
            StreamChatForCampaignUseCase streamChatForCampaignUseCase,
            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        this.streamChatForLoreUseCase = streamChatForLoreUseCase;
        this.streamChatForCampaignUseCase = streamChatForCampaignUseCase;
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

    // --- Exécution du streaming dans un thread dédié ------------------------

    private void runLoreStreaming(
            SseEmitter emitter, String loreId, String pageId, List<ChatMessage> messages) {
        try {
            streamChatForLoreUseCase.execute(
                    loreId, pageId, messages,
                    token -> sendToken(emitter, token),
                    () -> complete(emitter),
                    error -> fail(emitter, error));
        } catch (IllegalArgumentException e) {
            // Lore ou Page introuvable : on envoie un event error puis on termine proprement.
            fail(emitter, e);
        } catch (Exception e) {
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
                    campaignId, entityType, entityId, messages,
                    token -> sendToken(emitter, token),
                    () -> complete(emitter),
                    error -> fail(emitter, error));
        } catch (Exception e) {
            fail(emitter, e);
        }
    }

    // --- Helpers SSE (un seul point d'écriture par type d'événement) --------

    private void sendToken(SseEmitter emitter, String token) {
        try {
            emitter.send(SseEmitter.event()
                    .data("{\"token\":" + jsonEscape(token) + "}"));
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
                    .data("{\"message\":" + jsonEscape(message) + "}"));
            emitter.complete();
        } catch (IOException ioe) {
            emitter.completeWithError(ioe);
        }
    }

    // --- Utilitaires --------------------------------------------------------

    /** Encadre une chaîne de guillemets et échappe les caractères JSON dangereux. */
    private String jsonEscape(String raw) {
        if (raw == null) return "\"\"";
        StringBuilder sb = new StringBuilder(raw.length() + 2);
        sb.append('"');
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private List<ChatMessage> toDomainMessages(List<ChatMessageDTO> dtos) {
        if (dtos == null) return List.of();
        return dtos.stream()
                .map(dto -> new ChatMessage(dto.getRole(), dto.getContent()))
                .collect(Collectors.toList());
    }
}
