package com.loremind.infrastructure.web.controller;

import com.loremind.application.lorecontext.LoreNodeService;
import com.loremind.domain.lorecontext.LoreNode;
import com.loremind.infrastructure.web.dto.lorecontext.LoreNodeDTO;
import com.loremind.infrastructure.web.mapper.LoreNodeMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller pour le contexte LoreNode.
 */
@RestController
@RequestMapping("/api/lore-nodes")
public class LoreNodeController {

    private final LoreNodeService loreNodeService;
    private final LoreNodeMapper loreNodeMapper;

    public LoreNodeController(LoreNodeService loreNodeService, LoreNodeMapper loreNodeMapper) {
        this.loreNodeService = loreNodeService;
        this.loreNodeMapper = loreNodeMapper;
    }

    @PostMapping
    public ResponseEntity<LoreNodeDTO> createLoreNode(@RequestBody LoreNodeDTO loreNodeDTO) {
        LoreNode changes = loreNodeMapper.toDomain(loreNodeDTO);
        LoreNode created = loreNodeService.createLoreNode(changes);
        return ResponseEntity.ok(loreNodeMapper.toDTO(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LoreNodeDTO> getLoreNodeById(@PathVariable String id) {
        return loreNodeService.getLoreNodeById(id)
                .map(loreNode -> ResponseEntity.ok(loreNodeMapper.toDTO(loreNode)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/lore-nodes            → tous les dossiers (usage admin/debug)
     * GET /api/lore-nodes?loreId=X   → uniquement les dossiers du Lore X
     * <p>
     * Sans ce filtre, le frontend recevait TOUS les dossiers de TOUS les lores,
     * ce qui polluait l'affichage quand on switche entre deux lores.
     * Pattern aligné sur TemplateController et PageController.
     */
    @GetMapping
    public ResponseEntity<List<LoreNodeDTO>> getLoreNodes(
            @RequestParam(value = "loreId", required = false) String loreId) {
        List<LoreNode> loreNodes = (loreId != null && !loreId.isBlank())
                ? loreNodeService.getLoreNodesByLoreId(loreId)
                : loreNodeService.getAllLoreNodes();
        List<LoreNodeDTO> loreNodeDTOs = loreNodes.stream()
                .map(loreNodeMapper::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(loreNodeDTOs);
    }

    @GetMapping("/search")
    public ResponseEntity<List<LoreNodeDTO>> searchLoreNodes(@RequestParam("q") String query) {
        List<LoreNodeDTO> dtos = loreNodeService.searchLoreNodes(query).stream()
                .map(loreNodeMapper::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/lore/{loreId}")
    public ResponseEntity<List<LoreNodeDTO>> getLoreNodesByLoreId(@PathVariable String loreId) {
        List<LoreNode> loreNodes = loreNodeService.getLoreNodesByLoreId(loreId);
        List<LoreNodeDTO> loreNodeDTOs = loreNodes.stream()
                .map(loreNodeMapper::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(loreNodeDTOs);
    }

    @GetMapping("/parent/{parentId}")
    public ResponseEntity<List<LoreNodeDTO>> getLoreNodesByParentId(@PathVariable String parentId) {
        List<LoreNode> loreNodes = loreNodeService.getLoreNodesByParentId(parentId);
        List<LoreNodeDTO> loreNodeDTOs = loreNodes.stream()
                .map(loreNodeMapper::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(loreNodeDTOs);
    }

    @PutMapping("/{id}")
    public ResponseEntity<LoreNodeDTO> updateLoreNode(@PathVariable String id, @RequestBody LoreNodeDTO loreNodeDTO) {
        LoreNode changes = loreNodeMapper.toDomain(loreNodeDTO);
        LoreNode updated = loreNodeService.updateLoreNode(id, changes);
        return ResponseEntity.ok(loreNodeMapper.toDTO(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLoreNode(@PathVariable String id) {
        loreNodeService.deleteLoreNode(id);
        return ResponseEntity.noContent().build();
    }
}
