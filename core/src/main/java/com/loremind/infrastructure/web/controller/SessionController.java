package com.loremind.infrastructure.web.controller;

import com.loremind.application.playcontext.SessionRecapService;
import com.loremind.application.playcontext.SessionService;
import com.loremind.domain.playcontext.Session;
import com.loremind.domain.playcontext.ports.SessionRecapException;
import com.loremind.infrastructure.web.dto.playcontext.SessionDTO;
import com.loremind.infrastructure.web.mapper.SessionMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller pour le Play Context.
 * Adaptateur d'infrastructure qui expose l'API REST des Sessions.
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;
    private final SessionRecapService recapService;
    private final SessionMapper sessionMapper;

    public SessionController(SessionService sessionService,
                             SessionRecapService recapService,
                             SessionMapper sessionMapper) {
        this.sessionService = sessionService;
        this.recapService = recapService;
        this.sessionMapper = sessionMapper;
    }

    public record StartSessionRequest(String playthroughId) {}

    public record RenameSessionRequest(String name) {}

    public record CurrentSceneRequest(String sceneId) {}

    @PostMapping
    public ResponseEntity<SessionDTO> startSession(@RequestBody StartSessionRequest request) {
        Session session = sessionService.startSession(request.playthroughId());
        return ResponseEntity.ok(sessionMapper.toDTO(session));
    }

    @GetMapping("/active")
    public ResponseEntity<SessionDTO> getActiveSession(
            @RequestParam(value = "playthroughId", required = false) String playthroughId) {
        var maybe = (playthroughId == null || playthroughId.isBlank())
                ? sessionService.getActive()
                : sessionService.getActiveByPlaythrough(playthroughId);
        return maybe
                .map(s -> ResponseEntity.ok(sessionMapper.toDTO(s)))
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping
    public ResponseEntity<List<SessionDTO>> getSessions(@RequestParam(value = "playthroughId", required = false) String playthroughId) {
        List<Session> sessions = (playthroughId == null || playthroughId.isBlank())
                ? sessionService.getAll()
                : sessionService.getByPlaythroughId(playthroughId);
        List<SessionDTO> dtos = sessions.stream()
                .map(sessionMapper::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SessionDTO> getSessionById(@PathVariable String id) {
        return sessionService.getById(id)
                .map(s -> ResponseEntity.ok(sessionMapper.toDTO(s)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/end")
    public ResponseEntity<SessionDTO> endSession(@PathVariable String id) {
        Session ended = sessionService.endSession(id);
        return ResponseEntity.ok(sessionMapper.toDTO(ended));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SessionDTO> renameSession(@PathVariable String id,
                                                    @RequestBody RenameSessionRequest request) {
        Session renamed = sessionService.renameSession(id, request.name());
        return ResponseEntity.ok(sessionMapper.toDTO(renamed));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSession(@PathVariable String id) {
        sessionService.deleteSession(id);
        return ResponseEntity.noContent().build();
    }

    /** Épingle (sceneId) ou dés-épingle (null/vide) la scène courante — mode cockpit. */
    @PutMapping("/{id}/current-scene")
    public ResponseEntity<SessionDTO> setCurrentScene(@PathVariable String id,
                                                      @RequestBody CurrentSceneRequest request) {
        Session updated = sessionService.setCurrentScene(id, request.sceneId());
        return ResponseEntity.ok(sessionMapper.toDTO(updated));
    }

    /** Récap « précédemment… » : résume le journal de la séance précédente de la même Partie. */
    @PostMapping("/{id}/recap")
    public ResponseEntity<SessionRecapService.RecapResult> recap(@PathVariable String id) {
        try {
            return ResponseEntity.ok(recapService.recapPreviousSession(id));
        } catch (SessionRecapException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        }
    }
}
