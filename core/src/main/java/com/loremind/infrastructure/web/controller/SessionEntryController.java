package com.loremind.infrastructure.web.controller;

import com.loremind.application.playcontext.SessionEntryService;
import com.loremind.domain.playcontext.EntryType;
import com.loremind.domain.playcontext.SessionEntry;
import com.loremind.infrastructure.web.dto.playcontext.SessionEntryDTO;
import com.loremind.infrastructure.web.mapper.SessionEntryMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST Controller pour les entrées de journal d'une Session.
 * Endpoints imbriqués sous /api/sessions/{sessionId}/entries.
 */
@RestController
@RequestMapping("/api/sessions/{sessionId}/entries")
public class SessionEntryController {

    private final SessionEntryService entryService;
    private final SessionEntryMapper entryMapper;

    public SessionEntryController(SessionEntryService entryService, SessionEntryMapper entryMapper) {
        this.entryService = entryService;
        this.entryMapper = entryMapper;
    }

    public record EntryRequest(EntryType type, String content, LocalDateTime occurredAt) {}

    @GetMapping
    public ResponseEntity<List<SessionEntryDTO>> getEntries(@PathVariable String sessionId) {
        List<SessionEntryDTO> dtos = entryService.getBySessionId(sessionId).stream()
                .map(entryMapper::toDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    public ResponseEntity<SessionEntryDTO> createEntry(@PathVariable String sessionId,
                                                       @RequestBody EntryRequest request) {
        SessionEntry created = entryService.createEntry(
                sessionId,
                new SessionEntryService.EntryData(request.type(), request.content(), request.occurredAt())
        );
        return ResponseEntity.ok(entryMapper.toDTO(created));
    }

    @PutMapping("/{entryId}")
    public ResponseEntity<SessionEntryDTO> updateEntry(@PathVariable String sessionId,
                                                       @PathVariable String entryId,
                                                       @RequestBody EntryRequest request) {
        SessionEntry updated = entryService.updateEntry(
                entryId,
                new SessionEntryService.EntryData(request.type(), request.content(), request.occurredAt())
        );
        return ResponseEntity.ok(entryMapper.toDTO(updated));
    }

    @DeleteMapping("/{entryId}")
    public ResponseEntity<Void> deleteEntry(@PathVariable String sessionId,
                                            @PathVariable String entryId) {
        entryService.deleteEntry(entryId);
        return ResponseEntity.noContent().build();
    }
}
