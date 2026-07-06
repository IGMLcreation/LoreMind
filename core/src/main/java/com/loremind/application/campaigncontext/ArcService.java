package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.structure.Arc;
import com.loremind.domain.campaigncontext.structure.Chapter;
import com.loremind.domain.campaigncontext.generation.FieldProposal;
import com.loremind.domain.campaigncontext.quest.Quest;
import com.loremind.domain.shared.ReorderSupport;
import com.loremind.domain.campaigncontext.ports.ArcRepository;
import com.loremind.domain.campaigncontext.ports.ChapterRepository;
import com.loremind.domain.campaigncontext.ports.QuestRepository;
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
    private final QuestRepository questRepository;

    public ArcService(ArcRepository arcRepository,
                      ChapterRepository chapterRepository,
                      SceneRepository sceneRepository,
                      QuestRepository questRepository) {
        this.arcRepository = arcRepository;
        this.chapterRepository = chapterRepository;
        this.sceneRepository = sceneRepository;
        this.questRepository = questRepository;
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

    /**
     * Création à partir d'un Arc complet (utilisé par le controller pour faire passer
     * les nouveaux champs comme type sans démultiplier les paramètres).
     */
    public Arc createArc(Arc input) {
        input.setId(null);
        return arcRepository.save(input);
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
     * Patch CIBLÉ champ-par-champ d'un arc (Pilier A — co-création). Applique UNIQUEMENT
     * les {@link FieldProposal} reçus ; les autres champs restent INTACTS (contraste voulu
     * avec {@link #updateArc} qui écrase tout via BeanUtils).
     */
    @Transactional
    public Arc patchArc(String id, List<FieldProposal> fields) {
        Arc arc = arcRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Arc non trouvé avec l'ID: " + id));
        if (fields != null) {
            for (FieldProposal f : fields) {
                if (f == null || f.key() == null) continue;
                applyField(arc, f.key(), f.proposedValue());
            }
        }
        return arcRepository.save(arc);
    }

    /** Whitelist STRICTE des champs étoffables d'un arc ; clé inconnue ignorée. */
    private void applyField(Arc arc, String key, String value) {
        switch (key) {
            case "description" -> arc.setDescription(value);
            case "themes" -> arc.setThemes(value);
            case "stakes" -> arc.setStakes(value);
            case "rewards" -> arc.setRewards(value);
            case "resolution" -> arc.setResolution(value);
            case "gmNotes" -> arc.setGmNotes(value);
            default -> { /* clé inconnue → ignorée (garde-fou anti-écrasement) */ }
        }
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
        // Détache les quêtes rattachées (arc HUB) : elles deviennent TRANSVERSES plutôt
        // que fantômes (arcId pointant un arc disparu). Weak ref, pas de FK cascade.
        for (Quest quest : questRepository.findByArcId(id)) {
            quest.setArcId(null);
            questRepository.save(quest);
        }
        arcRepository.deleteById(id);
    }

    public boolean arcExists(String id) {
        return arcRepository.existsById(id);
    }

    /**
     * Réordonne les arcs d'une campagne : {@code order} = position dans la liste fournie.
     * Les ids inconnus sont ignorés. Transactionnel.
     */
    @Transactional
    public void reorderArcs(List<String> orderedIds) {
        ReorderSupport.reorder(orderedIds,
                arcRepository::findById,
                Arc::setOrder,
                arcRepository::save);
    }
}
