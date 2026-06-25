package com.loremind.infrastructure.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.application.gamesystemcontext.GameSystemService;
import com.loremind.domain.gamesystemcontext.GameSystem;
import com.loremind.domain.gamesystemcontext.RulesImportResult;
import com.loremind.domain.gamesystemcontext.ports.RulesImportException;
import com.loremind.infrastructure.web.dto.gamesystemcontext.GameSystemDTO;
import com.loremind.infrastructure.web.dto.gamesystemcontext.RulesImportResponseDTO;
import com.loremind.infrastructure.web.mapper.GameSystemMapper;
import com.loremind.infrastructure.web.mapper.TemplateFieldMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/game-systems")
public class GameSystemController {

    private static final Logger log = LoggerFactory.getLogger(GameSystemController.class);

    /**
     * Timeout SSE = durée TOTALE maximale de l'import (pas un timeout d'inactivité :
     * les heartbeats ne le réarment pas). Un livre entier sur un modèle local peut
     * largement dépasser 15 min → 60 min. La déconnexion du client reste détectée
     * immédiatement par ailleurs (échec d'envoi → interruption de l'import).
     */
    private static final long IMPORT_SSE_TIMEOUT_MS = 60 * 60 * 1000L;

    private final GameSystemService gameSystemService;
    private final GameSystemMapper gameSystemMapper;
    private final TemplateFieldMapper templateFieldMapper;
    private final TaskExecutor taskExecutor;
    private final ObjectMapper objectMapper;

    public GameSystemController(GameSystemService gameSystemService,
                                GameSystemMapper gameSystemMapper,
                                TemplateFieldMapper templateFieldMapper,
                                @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
                                ObjectMapper objectMapper) {
        this.gameSystemService = gameSystemService;
        this.gameSystemMapper = gameSystemMapper;
        this.templateFieldMapper = templateFieldMapper;
        this.taskExecutor = taskExecutor;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<GameSystemDTO> createGameSystem(@RequestBody GameSystemDTO dto) {
        GameSystem created = gameSystemService.createGameSystem(toData(dto));
        return ResponseEntity.ok(gameSystemMapper.toDTO(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GameSystemDTO> getGameSystemById(@PathVariable String id) {
        return gameSystemService.getGameSystemById(id)
                .map(g -> ResponseEntity.ok(gameSystemMapper.toDTO(g)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<GameSystemDTO>> getAllGameSystems() {
        List<GameSystemDTO> dtos = gameSystemService.getAllGameSystems().stream()
                .map(gameSystemMapper::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/search")
    public ResponseEntity<List<GameSystemDTO>> searchGameSystems(@RequestParam("q") String query) {
        List<GameSystemDTO> dtos = gameSystemService.searchGameSystems(query).stream()
                .map(gameSystemMapper::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PutMapping("/{id}")
    public ResponseEntity<GameSystemDTO> updateGameSystem(@PathVariable String id, @RequestBody GameSystemDTO dto) {
        GameSystem updated = gameSystemService.updateGameSystem(id, toData(dto));
        return ResponseEntity.ok(gameSystemMapper.toDTO(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGameSystem(@PathVariable String id) {
        gameSystemService.deleteGameSystem(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Import d'un PDF de règles → proposition de sections (titre → markdown).
     * Ne persiste RIEN : l'UI présente la proposition pour révision/édition,
     * puis l'utilisateur enregistre le GameSystem via les endpoints habituels.
     */
    @PostMapping(value = "/import-rules", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RulesImportResponseDTO> importRules(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            RulesImportResult result = gameSystemService.importRulesFromPdf(
                    file.getBytes(), file.getOriginalFilename());
            return ResponseEntity.ok(new RulesImportResponseDTO(
                    result.sections(), result.pageCount(), result.ocrPageCount()));
        } catch (IOException e) {
            return ResponseEntity.badRequest().build();
        } catch (RulesImportException e) {
            // Brain injoignable / LLM en erreur / PDF illisible : 502 (dépendance amont).
            // On loggue la vraie cause (message du Brain propagé) pour le diagnostic.
            log.warn("Import de règles échoué : {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    /**
     * Variante streamée de l'import : émet l'avancement (SSE) puis le résultat,
     * pour que l'UI affiche une progression pendant un import long.
     * <p>
     * Événements : {@code progress} (current/total/pageCount/ocrPageCount/newSectionTitles),
     * {@code done} (sections + compteurs), {@code error} (message).
     */
    @PostMapping(value = "/import-rules/stream",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter importRulesStream(@RequestParam("file") MultipartFile file) throws IOException {
        SseEmitter emitter = new SseEmitter(IMPORT_SSE_TIMEOUT_MS);
        if (file == null || file.isEmpty()) {
            sendImportError(emitter, new AtomicBoolean(false), "Fichier PDF vide.");
            return emitter;
        }
        // Les octets sont lus sur le thread servlet (le MultipartFile n'est plus
        // disponible une fois la requête asynchrone) avant de partir en tâche de fond.
        byte[] bytes = file.getBytes();
        String filename = file.getOriginalFilename();

        // Suivi de la déconnexion du navigateur : dès qu'un envoi échoue (ou que
        // l'emitter se termine), on cesse d'envoyer ET on interrompt le streaming
        // amont (l'exception ClientGone remonte dans le doOnNext du WebClient →
        // annule la souscription → le Brain voit la coupure et stoppe le LLM).
        AtomicBoolean clientGone = new AtomicBoolean(false);
        emitter.onTimeout(() -> {
            // Timeout = durée totale dépassée, mais la connexion est encore vivante :
            // on envoie une vraie erreur au navigateur AVANT de fermer (sinon le flux
            // se termine en silence et l'UI reste figée sur la barre de progression).
            sendImportError(emitter, clientGone,
                    "L'import a dépassé la durée maximale autorisée et a été interrompu. "
                            + "Réessayez avec un modèle plus rapide ou un PDF plus petit.");
            clientGone.set(true);
        });
        emitter.onError(e -> clientGone.set(true));

        taskExecutor.execute(() -> {
            try {
                gameSystemService.importRulesFromPdfStreaming(
                        bytes, filename,
                        progress -> sendImportEvent(emitter, clientGone, "progress", progress),
                        () -> sendImportHeartbeat(emitter, clientGone),
                        status -> sendImportEvent(emitter, clientGone, "status",
                                Map.of("message", status != null ? status : "")),
                        result -> {
                            sendImportEvent(emitter, clientGone, "done", result);
                            emitter.complete();
                        },
                        error -> {
                            if (clientGone.get()) {
                                // La "panne" amont n'est que l'écho de la déconnexion
                                // du navigateur : pas un échec d'import.
                                log.info("Import de règles (stream) interrompu : client déconnecté.");
                                return;
                            }
                            log.warn("Import de règles (stream) échoué : {}", error.getMessage());
                            sendImportError(emitter, clientGone, error.getMessage());
                        });
            } catch (ClientGoneException e) {
                log.info("Import de règles (stream) interrompu : client déconnecté.");
            } catch (Exception e) {
                log.warn("Import de règles (stream) échoué : {}", e.getMessage());
                sendImportError(emitter, clientGone, e.getMessage());
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

    /** Sérialise `payload` en JSON et l'envoie comme évènement SSE nommé. */
    private void sendImportEvent(
            SseEmitter emitter, AtomicBoolean clientGone, String eventName, Object payload) {
        if (clientGone.get()) {
            throw new ClientGoneException(null);
        }
        try {
            emitter.send(SseEmitter.event().name(eventName).data(
                    objectMapper.writeValueAsString(payload), MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            // IOException OU IllegalStateException (emitter déjà terminé) : le client
            // est parti. On marque l'état et on INTERROMPT le pipeline amont — sinon
            // chaque évènement suivant rejouerait l'échec (bruit de logs + LLM gaspillé).
            clientGone.set(true);
            emitter.completeWithError(e);
            throw new ClientGoneException(e);
        }
    }

    /**
     * Keep-alive vers le navigateur pendant un appel LLM long : un commentaire SSE
     * (ignoré par le front) suffit à réarmer le {@code proxy_read_timeout} de nginx.
     */
    private void sendImportHeartbeat(SseEmitter emitter, AtomicBoolean clientGone) {
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

    /** Envoie un évènement `error` {message} puis termine le flux. */
    private void sendImportError(SseEmitter emitter, AtomicBoolean clientGone, String message) {
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

    private GameSystemService.GameSystemData toData(GameSystemDTO dto) {
        return new GameSystemService.GameSystemData(
                dto.getName(),
                dto.getDescription(),
                dto.getRulesMarkdown(),
                templateFieldMapper.toDomainList(dto.getCharacterTemplate()),
                templateFieldMapper.toDomainList(dto.getNpcTemplate()),
                templateFieldMapper.toDomainList(dto.getEnemyTemplate()),
                dto.getFoundryActorType(),
                dto.getAuthor(),
                dto.isPublic()
        );
    }

    /**
     * Importe une structure d'acteur Foundry (exportée par le module) : remplace le
     * template ENNEMI par les champs mappés + pose le type d'acteur. Renvoie le système
     * mis à jour (le front rafraîchit l'éditeur de template).
     */
    @PostMapping("/{id}/import-foundry-structure")
    public ResponseEntity<GameSystemDTO> importFoundryStructure(
            @PathVariable String id, @RequestBody FoundryStructureRequest req) {
        List<GameSystemService.FoundryStructField> fields =
                (req.fields() == null ? List.<StructFieldDto>of() : req.fields()).stream()
                        .map(f -> new GameSystemService.FoundryStructField(f.path(), f.label(), f.type()))
                        .collect(Collectors.toList());
        GameSystem updated = gameSystemService.importFoundryStructure(id, req.actorType(), fields);
        return ResponseEntity.ok(gameSystemMapper.toDTO(updated));
    }

    public record FoundryStructureRequest(String system, String actorType, List<StructFieldDto> fields) {}

    public record StructFieldDto(String path, String label, String type) {}
}
