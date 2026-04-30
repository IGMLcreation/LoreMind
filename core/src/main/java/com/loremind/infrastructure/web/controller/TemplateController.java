package com.loremind.infrastructure.web.controller;

import com.loremind.application.lorecontext.TemplateService;
import com.loremind.domain.lorecontext.Template;
import com.loremind.domain.shared.template.TemplateField;
import com.loremind.infrastructure.web.dto.lorecontext.TemplateDTO;
import com.loremind.infrastructure.web.mapper.TemplateFieldMapper;
import com.loremind.infrastructure.web.mapper.TemplateMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller pour le contexte Template.
 * Expose également un endpoint filtré par Lore pour alimenter le panneau
 * "Templates" du sidebar secondaire.
 */
@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final TemplateService templateService;
    private final TemplateMapper templateMapper;
    private final TemplateFieldMapper fieldMapper;

    public TemplateController(TemplateService templateService,
                              TemplateMapper templateMapper,
                              TemplateFieldMapper fieldMapper) {
        this.templateService = templateService;
        this.templateMapper = templateMapper;
        this.fieldMapper = fieldMapper;
    }

    @PostMapping
    public ResponseEntity<TemplateDTO> createTemplate(@RequestBody TemplateDTO dto) {
        List<TemplateField> fields = dto.getFields() == null
                ? List.of()
                : dto.getFields().stream().map(fieldMapper::toDomain).toList();
        Template created = templateService.createTemplate(
                dto.getLoreId(),
                dto.getName(),
                dto.getDescription(),
                dto.getDefaultNodeId(),
                fields
        );
        return ResponseEntity.ok(templateMapper.toDTO(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TemplateDTO> getTemplateById(@PathVariable String id) {
        return templateService.getTemplateById(id)
                .map(template -> ResponseEntity.ok(templateMapper.toDTO(template)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<TemplateDTO>> getAllTemplates(
            @RequestParam(value = "loreId", required = false) String loreId) {
        List<Template> templates = (loreId != null && !loreId.isBlank())
                ? templateService.getTemplatesByLoreId(loreId)
                : templateService.getAllTemplates();
        List<TemplateDTO> dtos = templates.stream()
                .map(templateMapper::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/search")
    public ResponseEntity<List<TemplateDTO>> searchTemplates(@RequestParam("q") String query) {
        List<TemplateDTO> dtos = templateService.searchTemplates(query).stream()
                .map(templateMapper::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TemplateDTO> updateTemplate(@PathVariable String id,
                                                       @RequestBody TemplateDTO dto) {
        Template changes = templateMapper.toDomain(dto);
        Template updated = templateService.updateTemplate(id, changes);
        return ResponseEntity.ok(templateMapper.toDTO(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable String id) {
        templateService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }
}
