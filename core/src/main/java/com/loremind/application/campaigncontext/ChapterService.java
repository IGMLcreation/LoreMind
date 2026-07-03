package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.Chapter;
import com.loremind.domain.campaigncontext.FieldProposal;
import com.loremind.domain.shared.ReorderSupport;
import com.loremind.domain.campaigncontext.ports.ChapterRepository;
import com.loremind.domain.campaigncontext.ports.SceneRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final SceneRepository sceneRepository;

    public ChapterService(ChapterRepository chapterRepository, SceneRepository sceneRepository) {
        this.chapterRepository = chapterRepository;
        this.sceneRepository = sceneRepository;
    }

    /** Compte des scènes qui seront supprimées en cascade avec le chapitre. */
    public record DeletionImpact(int scenes) {}

    public Chapter createChapter(String name, String description, String arcId, int order) {
        return createChapter(name, description, arcId, order, null);
    }

    public Chapter createChapter(String name, String description, String arcId, int order, String icon) {
        Chapter chapter = Chapter.builder()
                .name(name)
                .description(description)
                .arcId(arcId)
                .order(order)
                .icon(icon)
                .build();
        return chapterRepository.save(chapter);
    }

    /**
     * Création à partir d'un Chapter complet (utilisé par le controller pour faire passer
     * les nouveaux champs comme progressionStatus / prerequisites sans démultiplier les
     * paramètres). L'id est forcé à null pour laisser la DB le générer.
     */
    public Chapter createChapter(Chapter input) {
        input.setId(null);
        return chapterRepository.save(input);
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

    /**
     * Patch CIBLÉ champ-par-champ d'un chapitre (Pilier A — co-création). Applique
     * UNIQUEMENT les {@link FieldProposal} reçus ; les autres champs restent INTACTS
     * (contraste voulu avec {@link #updateChapter} qui écrase tout via BeanUtils).
     */
    @Transactional
    public Chapter patchChapter(String id, List<FieldProposal> fields) {
        Chapter chapter = chapterRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Chapter non trouvé avec l'ID: " + id));
        if (fields != null) {
            for (FieldProposal f : fields) {
                if (f == null || f.key() == null) continue;
                applyField(chapter, f.key(), f.proposedValue());
            }
        }
        return chapterRepository.save(chapter);
    }

    /** Whitelist STRICTE des champs étoffables d'un chapitre ; clé inconnue ignorée. */
    private void applyField(Chapter chapter, String key, String value) {
        switch (key) {
            case "description" -> chapter.setDescription(value);
            case "gmNotes" -> chapter.setGmNotes(value);
            case "playerObjectives" -> chapter.setPlayerObjectives(value);
            case "narrativeStakes" -> chapter.setNarrativeStakes(value);
            default -> { /* clé inconnue → ignorée (garde-fou anti-écrasement) */ }
        }
    }

    /** Compte des scènes qui tomberont avec le chapitre. */
    public DeletionImpact getDeletionImpact(String id) {
        return new DeletionImpact(sceneRepository.findByChapterId(id).size());
    }

    /** Supprime le chapitre et toutes ses scènes. Transactionnel : atomique. */
    @Transactional
    public void deleteChapter(String id) {
        for (var scene : sceneRepository.findByChapterId(id)) {
            sceneRepository.deleteById(scene.getId());
        }
        chapterRepository.deleteById(id);
    }

    public boolean chapterExists(String id) {
        return chapterRepository.existsById(id);
    }

    /**
     * Réordonne (et déplace) les chapitres d'un arc : {@code order} = position. Si
     * {@code arcId} est fourni, on réaffecte le chapitre à cet arc. Transactionnel.
     */
    @Transactional
    public void reorderChapters(String arcId, List<String> orderedIds) {
        ReorderSupport.reorder(orderedIds,
                id -> chapterRepository.findById(id).orElse(null),
                (chapter, i) -> {
                    if (arcId != null && !arcId.isBlank()) chapter.setArcId(arcId);
                    chapter.setOrder(i);
                },
                chapterRepository::save);
    }
}
