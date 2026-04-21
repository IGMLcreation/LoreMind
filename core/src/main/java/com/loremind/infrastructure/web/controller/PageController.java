package com.loremind.infrastructure.web.controller;

import com.loremind.application.lorecontext.PageService;
import com.loremind.domain.lorecontext.Page;
import com.loremind.infrastructure.web.dto.lorecontext.PageDTO;
import com.loremind.infrastructure.web.mapper.PageMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller pour le contexte Page.
 * - GET /api/pages?loreId=X   → toutes les pages d'un lore
 * - GET /api/pages?nodeId=Y   → toutes les pages d'un noeud
 * - GET /api/pages/{id}       → détail
 * - POST /api/pages           → création (loreId, nodeId, templateId, title)
 * - PUT /api/pages/{id}       → mise à jour complète (title, nodeId, values, notes, tags, relatedPageIds)
 * - DELETE /api/pages/{id}    → suppression
 */
@RestController
@RequestMapping("/api/pages")
public class PageController {

    private final PageService pageService;
    private final PageMapper pageMapper;

    public PageController(PageService pageService, PageMapper pageMapper) {
        this.pageService = pageService;
        this.pageMapper = pageMapper;
    }

    @PostMapping
    public ResponseEntity<PageDTO> createPage(@RequestBody PageDTO dto) {
        Page created = pageService.createPage(
                dto.getLoreId(),
                dto.getNodeId(),
                dto.getTemplateId(),
                dto.getTitle()
        );
        return ResponseEntity.ok(pageMapper.toDTO(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PageDTO> getPageById(@PathVariable String id) {
        return pageService.getPageById(id)
                .map(page -> ResponseEntity.ok(pageMapper.toDTO(page)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<PageDTO>> getPages(
            @RequestParam(value = "loreId", required = false) String loreId,
            @RequestParam(value = "nodeId", required = false) String nodeId) {
        List<Page> pages;
        if (loreId != null && !loreId.isBlank()) {
            pages = pageService.getPagesByLoreId(loreId);
        } else if (nodeId != null && !nodeId.isBlank()) {
            pages = pageService.getPagesByNodeId(nodeId);
        } else {
            pages = pageService.getAllPages();
        }
        List<PageDTO> dtos = pages.stream().map(pageMapper::toDTO).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/search")
    public ResponseEntity<List<PageDTO>> searchPages(@RequestParam("q") String query) {
        List<PageDTO> dtos = pageService.searchPages(query).stream()
                .map(pageMapper::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PageDTO> updatePage(@PathVariable String id,
                                               @RequestBody PageDTO dto) {
        Page changes = pageMapper.toDomain(dto);
        Page updated = pageService.updatePage(id, changes);
        return ResponseEntity.ok(pageMapper.toDTO(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePage(@PathVariable String id) {
        pageService.deletePage(id);
        return ResponseEntity.noContent().build();
    }
}
