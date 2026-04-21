package com.loremind.infrastructure.web.controller;

import com.loremind.application.conversationcontext.ConversationService;
import com.loremind.domain.conversationcontext.Conversation;
import com.loremind.domain.conversationcontext.ConversationMessage;
import com.loremind.infrastructure.web.dto.conversationcontext.AppendMessageDTO;
import com.loremind.infrastructure.web.dto.conversationcontext.ConversationDTO;
import com.loremind.infrastructure.web.dto.conversationcontext.ConversationMessageDTO;
import com.loremind.infrastructure.web.dto.conversationcontext.CreateConversationDTO;
import com.loremind.infrastructure.web.dto.conversationcontext.RenameConversationDTO;
import com.loremind.infrastructure.web.mapper.ConversationMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * API REST des conversations persistees.
 *
 * GET /api/conversations?loreId=...&entityType=...&entityId=...   (listing filtre)
 * GET /api/conversations?campaignId=...&entityType=...&entityId=...
 * GET /api/conversations/{id}                                     (detail + messages)
 * POST /api/conversations                                         (create)
 * PATCH /api/conversations/{id}/title                             (rename)
 * DELETE /api/conversations/{id}
 *
 * L'ajout de messages est piloje cote chat stream (use case dedie),
 * pas par ce controller.
 */
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService service;
    private final ConversationMapper mapper;

    public ConversationController(ConversationService service, ConversationMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    public ResponseEntity<List<ConversationDTO>> list(
            @RequestParam(required = false) String loreId,
            @RequestParam(required = false) String campaignId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId) {
        List<Conversation> rows = service.listByContext(loreId, campaignId, entityType, entityId);
        return ResponseEntity.ok(rows.stream().map(mapper::toListDTO).collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConversationDTO> getById(@PathVariable String id) {
        return service.getById(id)
                .map(c -> ResponseEntity.ok(mapper.toDTO(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ConversationDTO> create(@RequestBody CreateConversationDTO dto) {
        Conversation created = service.create(new ConversationService.CreateData(
                dto.getTitle(),
                dto.getLoreId(),
                dto.getCampaignId(),
                dto.getEntityType(),
                dto.getEntityId()));
        return ResponseEntity.ok(mapper.toDTO(created));
    }

    @PatchMapping("/{id}/title")
    public ResponseEntity<Void> rename(@PathVariable String id, @RequestBody RenameConversationDTO dto) {
        service.rename(id, dto.getTitle());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<ConversationMessageDTO> appendMessage(
            @PathVariable String id,
            @RequestBody AppendMessageDTO dto) {
        ConversationMessage saved = service.appendMessage(id, dto.getRole(), dto.getContent());
        return ResponseEntity.ok(mapper.toMessageDTO(saved));
    }

    /**
     * Auto-genere et persiste un titre base sur les premiers messages.
     * Appele par le front apres le 1er couple user/assistant.
     */
    @PostMapping("/{id}/auto-title")
    public ResponseEntity<RenameConversationDTO> autoTitle(@PathVariable String id) {
        String title = service.autoGenerateTitle(id);
        return ResponseEntity.ok(RenameConversationDTO.builder().title(title).build());
    }
}
