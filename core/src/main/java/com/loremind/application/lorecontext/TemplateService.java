package com.loremind.application.lorecontext;

import com.loremind.domain.lorecontext.Template;
import com.loremind.domain.lorecontext.TemplateField;
import com.loremind.domain.lorecontext.ports.TemplateRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service d'application pour le contexte Template.
 * Orchestre la logique métier via le Port TemplateRepository.
 * Couche Application de l'Architecture Hexagonale.
 */
@Service
public class TemplateService {

    private final TemplateRepository templateRepository;

    public TemplateService(TemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    public Template createTemplate(String loreId,
                                   String name,
                                   String description,
                                   String defaultNodeId,
                                   List<TemplateField> fields) {
        Template template = Template.builder()
                .loreId(loreId)
                .name(name)
                .description(description)
                .defaultNodeId(defaultNodeId)
                .fields(fields != null ? new ArrayList<>(fields) : new ArrayList<>())
                .build();
        return templateRepository.save(template);
    }

    public Optional<Template> getTemplateById(String id) {
        return templateRepository.findById(id);
    }

    public List<Template> getAllTemplates() {
        return templateRepository.findAll();
    }

    public List<Template> getTemplatesByLoreId(String loreId) {
        return templateRepository.findByLoreId(loreId);
    }

    public List<Template> searchTemplates(String query) {
        if (query == null || query.isBlank()) return List.of();
        return templateRepository.searchByName(query.trim());
    }

    /**
     * Met à jour un Template existant.
     * Pattern Parameter Object via l'entité Template elle-même :
     * le controller construit un Template avec les champs à modifier, le service
     * recharge l'existant et applique les changements.
     */
    public Template updateTemplate(String id, Template changes) {
        Template existing = templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template non trouvé avec l'ID: " + id));

        existing.setName(changes.getName());
        existing.setDescription(changes.getDescription());
        existing.setDefaultNodeId(changes.getDefaultNodeId());
        existing.setFields(changes.getFields() != null
                ? new ArrayList<>(changes.getFields())
                : new ArrayList<>());
        // loreId volontairement immuable : un template ne migre pas d'un Lore à l'autre.
        return templateRepository.save(existing);
    }

    public void deleteTemplate(String id) {
        templateRepository.deleteById(id);
    }
}
