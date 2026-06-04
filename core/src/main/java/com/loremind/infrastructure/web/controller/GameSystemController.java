package com.loremind.infrastructure.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.application.gamesystemcontext.GameSystemService;
import com.loremind.domain.gamesystemcontext.GameSystem;
import com.loremind.domain.gamesystemcontext.RulesImportProgress;
import com.loremind.domain.gamesystemcontext.RulesImportResult;
import com.loremind.domain.gamesystemcontext.ports.RulesImportException;
import com.loremind.domain.shared.template.TemplateField;
import com.loremind.infrastructure.web.dto.gamesystemcontext.GameSystemDTO;
import com.loremind.infrastructure.web.dto.gamesystemcontext.RulesImportResponseDTO;
import com.loremind.infrastructure.web.dto.shared.TemplateFieldDTO;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/game-systems")
public class GameSystemController {

    private static final Logger log = LoggerFactory.getLogger(GameSystemController.class);

    /** Timeout SSE généreux : un import de livre entier peut durer plusieurs minutes. */
    private static final long IMPORT_SSE_TIMEOUT_MS = 15 * 60 * 1000L;

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
            sendImportError(emitter, "Fichier PDF vide.");
            return emitter;
        }
        // Les octets sont lus sur le thread servlet (le MultipartFile n'est plus
        // disponible une fois la requête asynchrone) avant de partir en tâche de fond.
        byte[] bytes = file.getBytes();
        String filename = file.getOriginalFilename();

        taskExecutor.execute(() -> {
            try {
                gameSystemService.importRulesFromPdfStreaming(
                        bytes, filename,
                        progress -> sendImportEvent(emitter, "progress", progress),
                        result -> {
                            sendImportEvent(emitter, "done", result);
                            emitter.complete();
                        },
                        error -> {
                            log.warn("Import de règles (stream) échoué : {}", error.getMessage());
                            sendImportError(emitter, error.getMessage());
                        });
            } catch (Exception e) {
                log.warn("Import de règles (stream) échoué : {}", e.getMessage());
                sendImportError(emitter, e.getMessage());
            }
        });
        return emitter;
    }

    /** Sérialise `payload` en JSON et l'envoie comme évènement SSE nommé. */
    private void sendImportEvent(SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(
                    objectMapper.writeValueAsString(payload), MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    /** Envoie un évènement `error` {message} puis termine le flux. */
    private void sendImportError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(
                    objectMapper.writeValueAsString(Map.of(
                            "message", message != null ? message : "Erreur inconnue.")),
                    MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private GameSystemService.GameSystemData toData(GameSystemDTO dto) {
        return new GameSystemService.GameSystemData(
                dto.getName(),
                dto.getDescription(),
                dto.getRulesMarkdown(),
                toDomainFields(dto.getCharacterTemplate()),
                toDomainFields(dto.getNpcTemplate()),
                dto.getAuthor(),
                dto.isPublic()
        );
    }

    private List<TemplateField> toDomainFields(List<TemplateFieldDTO> dtos) {
        if (dtos == null) return new ArrayList<>();
        List<TemplateField> out = new ArrayList<>(dtos.size());
        for (TemplateFieldDTO d : dtos) out.add(templateFieldMapper.toDomain(d));
        return out;
    }
}
