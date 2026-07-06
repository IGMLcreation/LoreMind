package com.loremind.infrastructure.web.controller;

import com.loremind.application.campaigncontext.NotebookService;
import com.loremind.domain.campaigncontext.notebook.Notebook;
import com.loremind.domain.campaigncontext.notebook.NotebookSource;
import com.loremind.domain.campaigncontext.ports.NotebookChatStreamer;
import com.loremind.domain.campaigncontext.ports.exceptions.NotebookException;
import com.loremind.infrastructure.web.sse.SseJson;
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

    // L'analyse approfondie (map-reduce sur tout le doc) peut être longue sur un gros
    // livre / un modèle lent → 30 min. Pour aller plus vite : modèle gros-contexte
    // (moins de lots). Au-delà, l'expiration est gérée proprement (pas de crash).
    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L;

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

    // --- Conversation : vider (= archiver) et consulter les archives ---

    /**
     * « Vider la conversation » : le fil actif est ARCHIVÉ en un lot horodaté,
     * jamais supprimé — consultable ensuite via {@link #listArchives}.
     */
    @PostMapping("/{id}/chat/clear")
    public ResponseEntity<Void> clearChat(@PathVariable String id) {
        if (service.getNotebook(id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notebook introuvable");
        }
        service.clearChat(id);
        return ResponseEntity.noContent().build();
    }

    /** Archives de conversation, plus récentes d'abord : [{archivedAt, messages:[…]}]. */
    @GetMapping("/{id}/chat/archives")
    public ResponseEntity<List<Map<String, Object>>> listArchives(@PathVariable String id) {
        var grouped = new java.util.TreeMap<java.time.LocalDateTime, List<Map<String, Object>>>(
                java.util.Comparator.reverseOrder());
        for (var m : service.getArchivedMessages(id)) {
            grouped.computeIfAbsent(m.getArchivedAt(), k -> new java.util.ArrayList<>())
                    .add(Map.of(
                            "role", m.getRole(),
                            "content", m.getContent(),
                            "createdAt", m.getCreatedAt().toString()));
        }
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        grouped.forEach((archivedAt, messages) -> {
            Map<String, Object> archive = new LinkedHashMap<>();
            archive.put("archivedAt", archivedAt.toString());
            archive.put("messages", messages);
            out.add(archive);
        });
        return ResponseEntity.ok(out);
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
        // Sélection de l'UI (cases cochées) : on ne garde que les sources qui
        // appartiennent bien à CE notebook et sont prêtes — un id étranger est
        // ignoré. Limite le coût (ex. analyse approfondie sur 1 PDF au lieu de 5).
        // Variable finale : elle est capturée par la lambda du taskExecutor.
        List<String> readyIds = service.readySourceIds(id);
        final List<String> sourceIds;
        if (req.sourceIds() != null) {
            var wanted = new java.util.HashSet<>(req.sourceIds());
            sourceIds = readyIds.stream().filter(wanted::contains).toList();
        } else {
            sourceIds = readyIds;
        }
        // Contexte = brief de campagne + archives cochées en référence (le tout
        // dans une variable finale : capturée par la lambda du taskExecutor).
        final String context = joinContexts(
                service.buildContext(nb.getCampaignId()),
                service.buildArchiveContext(id, req.archiveIds()));

        boolean deep = req.deep() != null && req.deep();
        taskExecutor.execute(() -> {
            StringBuilder assistant = new StringBuilder();
            chatStreamer.stream(sourceIds, history, context, deep, new NotebookChatStreamer.Callbacks(
                    sourcesJson -> sendSources(emitter, sourcesJson),
                    token -> { assistant.append(token); sendToken(emitter, token); },
                    progress -> sendProgress(emitter, progress),
                    () -> {
                        // Persiste la réponse de l'assistant à la fin du stream.
                        if (!assistant.isEmpty()) {
                            service.addMessage(id, "assistant", assistant.toString());
                        }
                        complete(emitter);
                    },
                    error -> fail(emitter, error)));
        });
        return emitter;
    }

    /** Concatène brief de campagne et contexte d'archives (séparés d'une ligne vide), en ignorant les vides. */
    private static String joinContexts(String campaignContext, String archiveContext) {
        if (archiveContext.isEmpty()) return campaignContext;
        if (campaignContext.isEmpty()) return archiveContext;
        return campaignContext + "\n\n" + archiveContext;
    }

    // --- Helpers SSE ---
    // IMPORTANT : on attrape AUSSI IllegalStateException. Si le flux a déjà été fermé
    // (timeout async, client déconnecté), `emitter.send/complete` la lève — et comme
    // ces helpers tournent dans un thread d'exécuteur, une exception non gérée y
    // remontait jusqu'au pool ("Exception in thread task-1: ResponseBodyEmitter has
    // already completed"). On l'ignore silencieusement : il n'y a plus rien à envoyer.

    private void sendToken(SseEmitter emitter, String token) {
        try {
            emitter.send(SseEmitter.event().name("token").data("{\"token\":" + SseJson.escape(token) + "}"));
        } catch (IOException | IllegalStateException e) {
            // flux fermé/expiré : on cesse d'écrire
        }
    }

    private void sendSources(SseEmitter emitter, String sourcesJson) {
        try {
            // JSON brut du Brain ({"sources":[{source_id,page,score},…]}), relayé tel quel.
            emitter.send(SseEmitter.event().name("sources").data(sourcesJson));
        } catch (IOException | IllegalStateException e) {
            // flux fermé/expiré : on cesse d'écrire
        }
    }

    private void sendProgress(SseEmitter emitter, NotebookChatStreamer.Progress p) {
        try {
            emitter.send(SseEmitter.event().name("progress")
                    .data("{\"current\":" + p.current() + ",\"total\":" + p.total() + "}"));
        } catch (IOException | IllegalStateException e) {
            // flux fermé/expiré : on cesse d'écrire
        }
    }

    private void complete(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("done").data("{}"));
            emitter.complete();
        } catch (IOException | IllegalStateException e) {
            // flux déjà fermé/expiré : rien à compléter
        }
    }

    private void fail(SseEmitter emitter, Throwable error) {
        try {
            String message = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
            emitter.send(SseEmitter.event().name("error").data("{\"message\":" + SseJson.escape(message) + "}"));
            emitter.complete();
        } catch (IOException | IllegalStateException e) {
            // flux déjà fermé/expiré : rien à envoyer
        }
    }

    public record CreateRequest(String campaignId, String name) {}
    public record RenameRequest(String name) {}
    /**
     * @param sourceIds  Optionnel : sous-ensemble de sources à utiliser pour ce tour
     *                   (cases cochées dans l'UI). Null = toutes les sources prêtes.
     *                   Toujours intersecté avec les sources du notebook (sécurité).
     * @param archiveIds Optionnel : archives de conversation cochées comme RÉFÉRENCE
     *                   (clés = archivedAt). Leur contenu est injecté dans le contexte
     *                   du prompt — toujours résolu dans CE notebook (sécurité).
     */
    public record ChatRequest(String message, Boolean deep, List<String> sourceIds,
                              List<String> archiveIds) {}
}
