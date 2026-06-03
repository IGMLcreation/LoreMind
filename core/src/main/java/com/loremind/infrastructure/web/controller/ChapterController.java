package com.loremind.infrastructure.web.controller;

import com.loremind.application.campaigncontext.ChapterService;
import com.loremind.application.campaigncontext.ChapterStatusEnricher;
import com.loremind.domain.campaigncontext.Chapter;
import com.loremind.infrastructure.web.dto.campaigncontext.ChapterDTO;
import com.loremind.infrastructure.web.mapper.ChapterMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller pour le contexte Chapter.
 * Si {@code ?playthroughId=} est fourni, les DTOs renvoyés sont enrichis de leur
 * {@code progressionStatus} et {@code effectiveStatus} relatifs à ce Playthrough.
 */
@RestController
@RequestMapping("/api/chapters")
public class ChapterController {

    private final ChapterService chapterService;
    private final ChapterMapper chapterMapper;
    private final ChapterStatusEnricher statusEnricher;

    public ChapterController(ChapterService chapterService,
                             ChapterMapper chapterMapper,
                             ChapterStatusEnricher statusEnricher) {
        this.chapterService = chapterService;
        this.chapterMapper = chapterMapper;
        this.statusEnricher = statusEnricher;
    }

    @PostMapping
    public ResponseEntity<ChapterDTO> createChapter(@RequestBody ChapterDTO chapterDTO) {
        Chapter created = chapterService.createChapter(chapterMapper.toDomain(chapterDTO));
        return ResponseEntity.ok(chapterMapper.toDTO(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChapterDTO> getChapterById(
            @PathVariable String id,
            @RequestParam(value = "playthroughId", required = false) String playthroughId) {
        return chapterService.getChapterById(id)
                .map(chapter -> {
                    ChapterDTO dto = chapterMapper.toDTO(chapter);
                    if (playthroughId != null && !playthroughId.isBlank()) {
                        statusEnricher.enrich(List.of(dto), List.of(chapter), playthroughId);
                    }
                    return ResponseEntity.ok(dto);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<ChapterDTO>> getAllChapters(
            @RequestParam(value = "arcId", required = false) String arcId,
            @RequestParam(value = "playthroughId", required = false) String playthroughId) {
        List<Chapter> chapters = (arcId != null && !arcId.isBlank())
                ? chapterService.getChaptersByArcId(arcId)
                : chapterService.getAllChapters();
        List<ChapterDTO> chapterDTOs = chapters.stream()
                .map(chapterMapper::toDTO)
                .collect(Collectors.toList());

        if (playthroughId != null && !playthroughId.isBlank()) {
            statusEnricher.enrich(chapterDTOs, chapters, playthroughId);
        }
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
}
