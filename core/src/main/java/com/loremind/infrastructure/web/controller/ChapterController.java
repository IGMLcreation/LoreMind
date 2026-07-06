package com.loremind.infrastructure.web.controller;

import com.loremind.application.campaigncontext.ChapterService;
import com.loremind.domain.campaigncontext.structure.Chapter;
import com.loremind.infrastructure.web.dto.campaigncontext.ChapterDTO;
import com.loremind.infrastructure.web.mapper.ChapterMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller pour le contexte Chapter.
 *
 * <p>Depuis le Niveau 1, le Chapitre est une donnée de SCÉNARIO pure : plus de prérequis
 * ni de statut de progression (le gating et la progression vivent sur les Quêtes). Les
 * endpoints GET n'enrichissent donc plus rien à partir d'un {@code playthroughId}.</p>
 */
@RestController
@RequestMapping("/api/chapters")
public class ChapterController {

    private final ChapterService chapterService;
    private final ChapterMapper chapterMapper;

    public ChapterController(ChapterService chapterService, ChapterMapper chapterMapper) {
        this.chapterService = chapterService;
        this.chapterMapper = chapterMapper;
    }

    @PostMapping
    public ResponseEntity<ChapterDTO> createChapter(@RequestBody ChapterDTO chapterDTO) {
        Chapter created = chapterService.createChapter(chapterMapper.toDomain(chapterDTO));
        return ResponseEntity.ok(chapterMapper.toDTO(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChapterDTO> getChapterById(@PathVariable String id) {
        return chapterService.getChapterById(id)
                .map(chapter -> ResponseEntity.ok(chapterMapper.toDTO(chapter)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<ChapterDTO>> getAllChapters(
            @RequestParam(value = "arcId", required = false) String arcId) {
        List<Chapter> chapters = (arcId != null && !arcId.isBlank())
                ? chapterService.getChaptersByArcId(arcId)
                : chapterService.getAllChapters();
        List<ChapterDTO> chapterDTOs = chapters.stream()
                .map(chapterMapper::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(chapterDTOs);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ChapterDTO> updateChapter(@PathVariable String id, @RequestBody ChapterDTO chapterDTO) {
        Chapter updated = chapterService.updateChapter(id, chapterMapper.toDomain(chapterDTO));
        return ResponseEntity.ok(chapterMapper.toDTO(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteChapter(@PathVariable String id) {
        chapterService.deleteChapter(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/deletion-impact")
    public ResponseEntity<ChapterService.DeletionImpact> getDeletionImpact(@PathVariable String id) {
        if (!chapterService.chapterExists(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(chapterService.getDeletionImpact(id));
    }

    /** Réordonne (et déplace) les chapitres d'un arc : order = position. */
    @PutMapping("/reorder")
    public ResponseEntity<Void> reorder(@RequestBody ReorderRequest req) {
        chapterService.reorderChapters(req.arcId(), req.orderedIds());
        return ResponseEntity.noContent().build();
    }

    public record ReorderRequest(String arcId, List<String> orderedIds) {}
}
