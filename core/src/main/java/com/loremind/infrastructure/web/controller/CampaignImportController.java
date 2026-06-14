package com.loremind.infrastructure.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.application.campaigncontext.CampaignImportService;
import com.loremind.domain.campaigncontext.CampaignImportProposal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * REST Controller pour l'import d'un PDF de campagne → arbre arc/chapitre/scène.
 * <p>
 *  - POST /api/campaigns/{id}/import-structure/stream  (multipart) → SSE de la
 *    proposition (progress + done). Ne persiste rien.
 *  - POST /api/campaigns/{id}/import-structure/apply   (JSON arbre révisé) →
 *    crée les entités et renvoie le récapitulatif.
 */
@RestController
@RequestMapping("/api/campaigns/{campaignId}/import-structure")
public class CampaignImportController {

    private static final Logger log = LoggerFactory.getLogger(CampaignImportController.class);

    /**
     * Timeout SSE = durée TOTALE maximale de l'import (pas un timeout d'inactivité :
     * les heartbeats ne le réarment pas). Un livre entier sur un modèle local peut
     * largement dépasser 15 min → 60 min. La déconnexion du client reste détectée
     * immédiatement par ailleurs (échec d'envoi → interruption de l'import).
     */
    private static final long IMPORT_SSE_TIMEOUT_MS = 60 * 60 * 1000L;

    private final CampaignImportService campaignImportService;
    private final TaskExecutor taskExecutor;
    private final ObjectMapper objectMapper;

    public CampaignImportController(
            CampaignImportService campaignImportService,
            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
            ObjectMapper objectMapper) {
        this.campaignImportService = campaignImportService;
        this.taskExecutor = taskExecutor;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/stream",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter importStream(
            @PathVariable String campaignId,
            @RequestParam("file") MultipartFile file) throws IOException {
        SseEmitter emitter = new SseEmitter(IMPORT_SSE_TIMEOUT_MS);
        if (file == null || file.isEmpty()) {
            sendError(emitter, new AtomicBoolean(false), "Fichier PDF vide.");
            return emitter;
        }
        byte[] bytes = file.getBytes();
        String filename = file.getOriginalFilename();

        // Suivi de la déconnexion du navigateur : dès qu'un envoi échoue (ou que
        // l'emitter se termine), on cesse d'envoyer ET on interrompt le streaming
        // amont (ClientGoneException remonte dans le doOnNext du WebClient →
        // annule la souscription → le Brain voit la coupure et stoppe le LLM).
        AtomicBoolean clientGone = new AtomicBoolean(false);
        emitter.onTimeout(() -> {
            // Timeout = durée totale dépassée, mais la connexion est encore vivante :
            // on envoie une vraie erreur au navigateur AVANT de fermer (sinon le flux
            // se termine en silence et l'UI reste figée sur la barre de progression).
            sendError(emitter, clientGone,
                    "L'import a dépassé la durée maximale autorisée et a été interrompu. "
                            + "Réessayez avec un modèle plus rapide ou un PDF plus petit.");
            clientGone.set(true);
        });
        emitter.onError(e -> clientGone.set(true));

        taskExecutor.execute(() -> {
            try {
                campaignImportService.importStructureStreaming(
                        bytes, filename,
                        progress -> sendEvent(emitter, clientGone, "progress", progress),
                        () -> sendHeartbeat(emitter, clientGone),
                        status -> sendEvent(emitter, clientGone, "status",
                                Map.of("message", status != null ? status : "")),
                        proposal -> {
                            sendEvent(emitter, clientGone, "done", proposal);
                            emitter.complete();
                        },
                        error -> {
                            if (clientGone.get()) {
                                log.info("Import campagne (stream) interrompu : client déconnecté.");
                                return;
                            }
                            log.warn("Import campagne (stream) échoué : {}", error.getMessage());
                            sendError(emitter, clientGone, error.getMessage());
                        });
            } catch (ClientGoneException e) {
                log.info("Import campagne (stream) interrompu : client déconnecté.");
            } catch (Exception e) {
                log.warn("Import campagne (stream) échoué : {}", e.getMessage());
                sendError(emitter, clientGone, e.getMessage());
            }
        });
        return emitter;
    }

    /** Signale que le navigateur a fermé le flux SSE : inutile de continuer l'import. */
    private static final class ClientGoneException extends RuntimeException {
        ClientGoneException(Throwable cause) {
            super("Client SSE déconnecté.", cause);
        }
    }

    @PostMapping(value = "/apply", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CampaignImportService.ApplyResult> apply(
            @PathVariable String campaignId,
            @RequestBody CampaignImportProposal proposal) {
        try {
            CampaignImportService.ApplyResult result =
                    campaignImportService.applyStructure(campaignId, proposal);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // --- Helpers SSE ---------------------------------------------------------

    private void sendEvent(
            SseEmitter emitter, AtomicBoolean clientGone, String eventName, Object payload) {
        if (clientGone.get()) {
            throw new ClientGoneException(null);
        }
        try {
            emitter.send(SseEmitter.event().name(eventName).data(
                    objectMapper.writeValueAsString(payload), MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            // IOException OU IllegalStateException (emitter déjà terminé) : le client
            // est parti — on interrompt le pipeline amont au lieu de rejouer l'échec.
            clientGone.set(true);
            emitter.completeWithError(e);
            throw new ClientGoneException(e);
        }
    }

    /**
     * Keep-alive vers le navigateur pendant un appel LLM long : un commentaire SSE
     * (ignoré par le front) suffit à réarmer le {@code proxy_read_timeout} de nginx.
     */
    private void sendHeartbeat(SseEmitter emitter, AtomicBoolean clientGone) {
        if (clientGone.get()) {
            throw new ClientGoneException(null);
        }
        try {
            emitter.send(SseEmitter.event().comment("keepalive"));
        } catch (Exception e) {
            clientGone.set(true);
            emitter.completeWithError(e);
            throw new ClientGoneException(e);
        }
    }

    private void sendError(SseEmitter emitter, AtomicBoolean clientGone, String message) {
        if (clientGone.get()) {
            return; // le client n'est plus là pour lire le message d'erreur.
        }
        try {
            emitter.send(SseEmitter.event().name("error").data(
                    objectMapper.writeValueAsString(Map.of(
                            "message", message != null ? message : "Erreur inconnue.")),
                    MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (Exception e) {
            clientGone.set(true);
            emitter.completeWithError(e);
        }
    }
}
