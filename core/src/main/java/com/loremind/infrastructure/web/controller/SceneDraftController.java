package com.loremind.infrastructure.web.controller;

import com.loremind.application.campaigncontext.ChapterService;
import com.loremind.application.campaigncontext.SceneService;
import com.loremind.application.generationcontext.DraftScenesUseCase;
import com.loremind.domain.campaigncontext.structure.Scene;
import com.loremind.domain.campaigncontext.generation.SceneDraftProposal;
import com.loremind.domain.campaigncontext.ports.exceptions.NarrativeAssistException;
import com.loremind.infrastructure.web.dto.campaigncontext.SceneDTO;
import com.loremind.infrastructure.web.mapper.SceneMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller du Pilier A (capacité « create ») : peuple un chapitre en scènes.
 *
 * <p>{@code /generate} produit une PROPOSITION d'ébauches (non persistée) ; l'utilisateur
 * révise/coche côté front, puis {@code /apply} reçoit les ébauches retenues et crée les
 * scènes dans le chapitre. Découplage propose/apply comme le reste du Pilier A.</p>
 */
@RestController
@RequestMapping("/api/chapters/{chapterId}/draft-scenes")
public class SceneDraftController {

    private static final int DEFAULT_COUNT = 4;

    private final DraftScenesUseCase draftUseCase;
    private final SceneService sceneService;
    private final ChapterService chapterService;
    private final SceneMapper sceneMapper;

    public SceneDraftController(DraftScenesUseCase draftUseCase,
                                SceneService sceneService,
                                ChapterService chapterService,
                                SceneMapper sceneMapper) {
        this.draftUseCase = draftUseCase;
        this.sceneService = sceneService;
        this.chapterService = chapterService;
        this.sceneMapper = sceneMapper;
    }

    /** Génère une proposition d'ébauches de scènes (non persistée). */
    @PostMapping("/generate")
    public ResponseEntity<SceneDraftProposal> generate(@PathVariable String chapterId,
                                                       @RequestBody(required = false) GenerateRequest body) {
        String campaignId = body != null ? body.campaignId() : null;
        String instruction = body != null ? body.instruction() : null;
        int count = body != null && body.count() != null ? body.count() : DEFAULT_COUNT;
        try {
            return ResponseEntity.ok(draftUseCase.execute(chapterId, campaignId, instruction, count));
        } catch (NarrativeAssistException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        }
    }

    /** Crée les scènes ACCEPTÉES dans le chapitre ; renvoie les scènes créées. */
    @PostMapping("/apply")
    public ResponseEntity<List<SceneDTO>> apply(@PathVariable String chapterId,
                                                @RequestBody SceneDraftProposal proposal) {
        // Garde-fou : ne jamais créer de scènes orphelines si le chapitre n'existe pas
        // (l'apply ne passe pas par le use case, contrairement au /generate).
        if (!chapterService.chapterExists(chapterId)) {
            throw new IllegalArgumentException("Chapitre non trouvé: " + chapterId);
        }
        List<Scene> created = sceneService.createDraftScenes(chapterId, proposal.scenes());
        List<SceneDTO> dtos = created.stream().map(sceneMapper::toDTO).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    public record GenerateRequest(String campaignId, String instruction, Integer count) {}
}
