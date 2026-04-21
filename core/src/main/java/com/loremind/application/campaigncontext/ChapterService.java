package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.Chapter;
import com.loremind.domain.campaigncontext.ports.ChapterRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service d'application pour le contexte Chapter.
 * Orchestre la logique métier en utilisant le Port ChapterRepository.
 * Fait partie de la couche Application de l'Architecture Hexagonale.
 */
@Service
public class ChapterService {

    private final ChapterRepository chapterRepository;

    public ChapterService(ChapterRepository chapterRepository) {
        this.chapterRepository = chapterRepository;
    }

    public Chapter createChapter(String name, String description, String arcId, int order) {
        Chapter chapter = Chapter.builder()
                .name(name)
                .description(description)
                .arcId(arcId)
                .order(order)
                .build();
        return chapterRepository.save(chapter);
    }

    public Optional<Chapter> getChapterById(String id) {
        return chapterRepository.findById(id);
    }

    public List<Chapter> getAllChapters() {
        return chapterRepository.findAll();
    }

    public List<Chapter> getChaptersByArcId(String arcId) {
        return chapterRepository.findByArcId(arcId);
    }

    /**
     * Met à jour un Chapter avec tous ses champs narratifs (Parameter Object pattern).
     */
    public Chapter updateChapter(String id, Chapter updated) {
        Optional<Chapter> existingChapter = chapterRepository.findById(id);
        if (existingChapter.isEmpty()) {
            throw new IllegalArgumentException("Chapter non trouvé avec l'ID: " + id);
        }

        Chapter chapter = existingChapter.get();
        BeanUtils.copyProperties(updated, chapter, "id");
        return chapterRepository.save(chapter);
    }

    public void deleteChapter(String id) {
        chapterRepository.deleteById(id);
    }

    public boolean chapterExists(String id) {
        return chapterRepository.existsById(id);
    }
}
