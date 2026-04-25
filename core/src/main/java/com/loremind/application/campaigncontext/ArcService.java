package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.Arc;
import com.loremind.domain.campaigncontext.Chapter;
import com.loremind.domain.campaigncontext.ports.ArcRepository;
import com.loremind.domain.campaigncontext.ports.ChapterRepository;
import com.loremind.domain.campaigncontext.ports.SceneRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service d'application pour le contexte Arc.
 * Orchestre la logique métier en utilisant le Port ArcRepository.
 * Fait partie de la couche Application de l'Architecture Hexagonale.
 */
@Service
public class ArcService {

    private final ArcRepository arcRepository;
    private final ChapterRepository chapterRepository;
    private final SceneRepository sceneRepository;

    public ArcService(ArcRepository arcRepository,
                      ChapterRepository chapterRepository,
                      SceneRepository sceneRepository) {
        this.arcRepository = arcRepository;
        this.chapterRepository = chapterRepository;
        this.sceneRepository = sceneRepository;
    }

    /** Compte des entités qui seront supprimées en cascade avec l'arc. */
    public record DeletionImpact(int chapters, int scenes) {}

    public Arc createArc(String name, String description, String campaignId, int order) {
        return createArc(name, description, campaignId, order, null);
    }

    public Arc createArc(String name, String description, String campaignId, int order, String icon) {
        Arc arc = Arc.builder()
                .name(name)
                .description(description)
                .campaignId(campaignId)
                .order(order)
                .icon(icon)
                .build();
        return arcRepository.save(arc);
    }

    public Optional<Arc> getArcById(String id) {
        return arcRepository.findById(id);
    }

    public List<Arc> getAllArcs() {
        return arcRepository.findAll();
    }

    public List<Arc> getArcsByCampaignId(String campaignId) {
        return arcRepository.findByCampaignId(campaignId);
    }

    /**
     * Met à jour un Arc avec tous ses champs narratifs.
     * Accepte un objet Arc pour éviter l'explosion de paramètres (Parameter Object pattern).
     */
    public Arc updateArc(String id, Arc updated) {
        Optional<Arc> existingArc = arcRepository.findById(id);
        if (existingArc.isEmpty()) {
            throw new IllegalArgumentException("Arc non trouvé avec l'ID: " + id);
        }

        Arc arc = existingArc.get();
        BeanUtils.copyProperties(updated, arc, "id");
        return arcRepository.save(arc);
    }

    /**
     * Calcule l'impact d'une suppression en cascade : chapitres + scènes
     * qui disparaîtront avec l'arc.
     */
    public DeletionImpact getDeletionImpact(String id) {
        List<Chapter> chapters = chapterRepository.findByArcId(id);
        int sceneTotal = 0;
        for (Chapter chapter : chapters) {
            sceneTotal += sceneRepository.findByChapterId(chapter.getId()).size();
        }
        return new DeletionImpact(chapters.size(), sceneTotal);
    }

    /**
     * Supprime l'arc et toutes ses entités dépendantes (chapitres → scènes).
     * Transactionnel : atomique.
     */
    @Transactional
    public void deleteArc(String id) {
        for (Chapter chapter : chapterRepository.findByArcId(id)) {
            for (var scene : sceneRepository.findByChapterId(chapter.getId())) {
                sceneRepository.deleteById(scene.getId());
            }
            chapterRepository.deleteById(chapter.getId());
        }
        arcRepository.deleteById(id);
    }

    public boolean arcExists(String id) {
        return arcRepository.existsById(id);
    }
}
