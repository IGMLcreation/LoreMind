package com.loremind.infrastructure.web.controller;

import com.loremind.application.campaigncontext.ChapterService;
import com.loremind.domain.campaigncontext.Chapter;
import com.loremind.infrastructure.web.dto.campaigncontext.ChapterDTO;
import com.loremind.infrastructure.web.mapper.ChapterMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller pour le contexte Chapter.
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
        Chapter chapter = chapterMapper.toDomain(chapterDTO);
        Chapter createdChapter = chapterService.createChapter(chapter.getName(), chapter.getDescription(), chapter.getArcId(), chapter.getOrder());
        return ResponseEntity.ok(chapterMapper.toDTO(createdChapter));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChapterDTO> getChapterById(@PathVariable String id) {
        return chapterService.getChapterById(id)
                .map(chapter -> ResponseEntity.ok(chapterMapper.toDTO(chapter)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<ChapterDTO>> getAllChapters() {
        List<Chapter> chapters = chapterService.getAllChapters();
        List<ChapterDTO> chapterDTOs = chapters.stream()
                .map(chapterMapper::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(chapterDTOs);
    }

    @GetMapping("/arc/{arcId}")
    public ResponseEntity<List<ChapterDTO>> getChaptersByArcId(@PathVariable String arcId) {
        List<Chapter> chapters = chapterService.getChaptersByArcId(arcId);
        List<ChapterDTO> chapterDTOs = chapters.stream()
                .map(chapterMapper::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(chapterDTOs);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ChapterDTO> updateChapter(@PathVariable String id, @RequestBody ChapterDTO chapterDTO) {
        Chapter updatedChapter = chapterService.updateChapter(id, chapterMapper.toDomain(chapterDTO));
        return ResponseEntity.ok(chapterMapper.toDTO(updatedChapter));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteChapter(@PathVariable String id) {
        chapterService.deleteChapter(id);
        return ResponseEntity.noContent().build();
    }
}
