package com.loremind.infrastructure.web.controller;

import com.loremind.application.playcontext.SessionService;
import com.loremind.domain.playcontext.Session;
import com.loremind.infrastructure.web.dto.playcontext.SessionDTO;
import com.loremind.infrastructure.web.mapper.SessionMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    private final SessionMapper sessionMapper;

    public SessionController(SessionService sessionService, SessionMapper sessionMapper) {
        this.sessionService = sessionService;
        this.sessionMapper = sessionMapper;
    }

    public record StartSessionRequest(String playthroughId) {}

    public record RenameSessionRequest(String name) {}

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
}
