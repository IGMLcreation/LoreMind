package com.loremind.infrastructure.web.controller;

import com.loremind.application.generationcontext.GeneratePageValuesUseCase;
import com.loremind.domain.generationcontext.ports.AiProviderException;
import com.loremind.infrastructure.web.dto.generationcontext.GenerationSuggestionsDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST Controller pour la génération IA d'une Page.
 * <p>
 * POST /api/pages/{id}/generate → suggestions de valeurs (non persistées)
 * <p>
 * Endpoint séparé de PageController par SRP : il expose le GenerationContext,
 * pas le CRUD de Page. URL RESTful conservée (action sur une ressource Page).
 */
@RestController
@RequestMapping("/api/pages")
public class PageGenerationController {

    private final GeneratePageValuesUseCase generatePageValuesUseCase;

    public PageGenerationController(GeneratePageValuesUseCase generatePageValuesUseCase) {
        this.generatePageValuesUseCase = generatePageValuesUseCase;
    }

    /**
     * Demande à l'IA de suggérer des valeurs pour les champs dynamiques
     * d'une Page. Ne modifie PAS la Page en base.
     * <p>
     * Codes retour :
     *  200 — suggestions renvoyées
     *  404 — page introuvable
     *  422 — template sans champs (rien à générer)
     *  502 — Brain Python indisponible ou en erreur
     *  500 — incohérence BDD (template/lore/dossier introuvable)
     */
    @PostMapping("/{id}/generate")
    public ResponseEntity<GenerationSuggestionsDTO> generate(@PathVariable String id) {
        try {
            Map<String, String> values = generatePageValuesUseCase.execute(id);
            return ResponseEntity.ok(new GenerationSuggestionsDTO(values));

        } catch (IllegalArgumentException e) {
            // Page introuvable — faute de l'appelant
            return ResponseEntity.notFound().build();

        } catch (AiProviderException e) {
            // Brain down / timeout / réponse invalide
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();

        } catch (IllegalStateException e) {
            // Distinction fine : template sans champs (422) vs autre incohérence BDD (500)
            if (e.getMessage() != null && e.getMessage().contains("aucun champ")) {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build();
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
