package com.loremind.infrastructure.web.controller;

import com.loremind.application.campaigncontext.NotebookService;
import com.loremind.domain.campaigncontext.Notebook;
import com.loremind.domain.campaigncontext.NotebookSource;
import com.loremind.domain.campaigncontext.ports.NotebookChatStreamer;
import com.loremind.domain.campaigncontext.ports.NotebookException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller des notebooks (atelier RAG). CRUD + upload/indexation de sources
 * + chat ancré streamé (SSE) qui persiste la conversation.
 */
@RestController
@RequestMapping("/api/notebooks")
public class NotebookController {

    private static final long SSE_TIMEOUT_MS = 10 * 60 * 1000L;

    private final NotebookService service;
    private final NotebookChatStreamer chatStreamer;
    private final TaskExecutor taskExecutor;

    public NotebookController(
            NotebookService service,
            NotebookChatStreamer chatStreamer,
            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        this.service = service;
        this.chatStreamer = chatStreamer;
        this.taskExecutor = taskExecutor;
    }

    // --- Notebooks ---

    @PostMapping
    public ResponseEntity<Notebook> create(@RequestBody CreateRequest req) {
        return ResponseEntity.ok(service.createNotebook(req.campaignId(), req.name()));
    }

    @GetMapping("/campaign/{campaignId}")
    public ResponseEntity<List<Notebook>> listByCampaign(@PathVariable String campaignId) {
        return ResponseEntity.ok(service.getNotebooksByCampaign(campaignId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String id) {
        Notebook nb = service.getNotebook(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notebook introuvable"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", nb.getId());
        out.put("name", nb.getName());
        out.put("campaignId", nb.getCampaignId());
        out.put("sources", service.getSources(id));
        out.put("messages", service.getMessages(id));
        return ResponseEntity.ok(out);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Notebook> rename(@PathVariable String id, @RequestBody RenameRequest req) {
        return ResponseEntity.ok(service.renameNotebook(id, req.name()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.deleteNotebook(id);
        return ResponseEntity.noContent().build();
    }

    // --- Sources ---

    @PostMapping("/{id}/sources")
    public ResponseEntity<NotebookSource> addSource(
            @PathVariable String id,
            @RequestParam("file") MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            NotebookSource source = service.addSource(id, file.getOriginalFilename(), bytes);
            return ResponseEntity.ok(source);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fichier illisible", e);
        } catch (NotebookException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        }
    }

    @DeleteMapping("/sources/{sourceId}")
    public ResponseEntity<Void> deleteSource(@PathVariable String sourceId) {
        service.deleteSource(sourceId);
        return ResponseEntity.noContent().build();
    }

    // --- Chat ancré streamé ---

    @PostMapping(value = "/{id}/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@PathVariable String id, @RequestBody ChatRequest req) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        Notebook nb = service.getNotebook(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notebook introuvable"));

        String userMessage = req.message() == null ? "" : req.message().trim();
        if (userMessage.isEmpty()) {
            fail(emitter, new IllegalArgumentException("Message vide."));
            return emitter;
        }
        // Persiste le message utilisateur AVANT le stream (l'historique inclura ce tour).
        service.addMessage(id, "user", userMessage);

        List<NotebookChatStreamer.Msg> history = service.getMessages(id).stream()
                .map(m -> new NotebookChatStreamer.Msg(m.getRole(), m.getContent()))
                .toList();
        List<String> sourceIds = service.readySourceIds(id);
        String context = service.buildContext(nb.getCampaignId());

        boolean deep = req.deep() != null && req.deep();
        taskExecutor.execute(() -> {
            StringBuilder assistant = new StringBuilder();
            chatStreamer.stream(
                    sourceIds, history, context, deep,
                    token -> { assistant.append(token); sendToken(emitter, token); },
                    progress -> sendProgress(emitter, progress),
                    () -> {
                        // Persiste la réponse de l'assistant à la fin du stream.
                        if (assistant.length() > 0) {
                            service.addMessage(id, "assistant", assistant.toString());
                        }
                        complete(emitter);
                    },
                    error -> fail(emitter, error));
        });
        return emitter;
    }

    // --- Helpers SSE (mêmes conventions que AiChatController) ---

    private void sendToken(SseEmitter emitter, String token) {
        try {
            emitter.send(SseEmitter.event().name("token").data("{\"token\":" + jsonEscape(token) + "}"));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private void sendProgress(SseEmitter emitter, NotebookChatStreamer.Progress p) {
        try {
            emitter.send(SseEmitter.event().name("progress")
                    .data("{\"current\":" + p.current() + ",\"total\":" + p.total() + "}"));
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
            emitter.send(SseEmitter.event().name("error").data("{\"message\":" + jsonEscape(message) + "}"));
            emitter.complete();
        } catch (IOException ioe) {
            emitter.completeWithError(ioe);
        }
    }

    private String jsonEscape(String raw) {
        if (raw == null) return "\"\"";
        StringBuilder sb = new StringBuilder(raw.length() + 2).append('"');
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    public record CreateRequest(String campaignId, String name) {}
    public record RenameRequest(String name) {}
    public record ChatRequest(String message, Boolean deep) {}
}
