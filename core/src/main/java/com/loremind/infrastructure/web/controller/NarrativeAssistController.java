package com.loremind.infrastructure.web.controller;

import com.loremind.application.campaigncontext.ArcService;
import com.loremind.application.campaigncontext.ChapterService;
import com.loremind.application.campaigncontext.SceneService;
import com.loremind.application.generationcontext.NarrativeAssistFieldsUseCase;
import com.loremind.domain.campaigncontext.generation.EntityFieldPatchProposal;
import com.loremind.domain.campaigncontext.ports.exceptions.NarrativeAssistException;
import com.loremind.infrastructure.web.mapper.ArcMapper;
import com.loremind.infrastructure.web.mapper.ChapterMapper;
import com.loremind.infrastructure.web.mapper.SceneMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST Controller du Pilier A (co-création « propose → applique »), GÉNÉRIQUE par type
 * d'entité narrative ({@code arc|chapter|scene}).
 *
 * <p>{@code /generate} produit une PROPOSITION de patch (non persistée) ; l'utilisateur
 * révise champ par champ côté front, puis {@code /apply} reçoit le record filtré aux champs
 * acceptés et patche l'entité via son service (patch ciblé anti-écrasement).</p>
 */
@RestController
@RequestMapping("/api/assist/{entityType}/{entityId}")
public class NarrativeAssistController {

    private final NarrativeAssistFieldsUseCase assistUseCase;
    private final SceneService sceneService;
    private final ChapterService chapterService;
    private final ArcService arcService;
    private final SceneMapper sceneMapper;
    private final ChapterMapper chapterMapper;
    private final ArcMapper arcMapper;

    public NarrativeAssistController(NarrativeAssistFieldsUseCase assistUseCase,
                                     SceneService sceneService,
                                     ChapterService chapterService,
                                     ArcService arcService,
                                     SceneMapper sceneMapper,
                                     ChapterMapper chapterMapper,
                                     ArcMapper arcMapper) {
        this.assistUseCase = assistUseCase;
        this.sceneService = sceneService;
        this.chapterService = chapterService;
        this.arcService = arcService;
        this.sceneMapper = sceneMapper;
        this.chapterMapper = chapterMapper;
        this.arcMapper = arcMapper;
    }

    /** Génère une proposition d'étoffage (non persistée). {@code campaignId} facultatif (contexte). */
    @PostMapping("/generate")
    public ResponseEntity<EntityFieldPatchProposal> generate(@PathVariable String entityType,
                                                             @PathVariable String entityId,
                                                             @RequestBody(required = false) GenerateRequest body) {
        String campaignId = body != null ? body.campaignId() : null;
        String instruction = body != null ? body.instruction() : null;
        try {
            return ResponseEntity.ok(assistUseCase.execute(entityType, entityId, campaignId, instruction));
        } catch (NarrativeAssistException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        }
    }

    /** Applique les champs ACCEPTÉS (patch ciblé) sur l'entité du type demandé. */
    @PostMapping("/apply")
    public ResponseEntity<Object> apply(@PathVariable String entityType,
                                        @PathVariable String entityId,
                                        @RequestBody EntityFieldPatchProposal proposal) {
        String type = entityType == null ? "" : entityType.trim().toLowerCase();
        Object dto = switch (type) {
            case "scene" -> sceneMapper.toDTO(sceneService.patchScene(entityId, proposal.fields()));
            case "chapter" -> chapterMapper.toDTO(chapterService.patchChapter(entityId, proposal.fields()));
            case "arc" -> arcMapper.toDTO(arcService.patchArc(entityId, proposal.fields()));
            default -> throw new IllegalArgumentException("Type d'entité narrative inconnu: " + entityType);
        };
        return ResponseEntity.ok(dto);
    }

    public record GenerateRequest(String campaignId, String instruction) {}
}
