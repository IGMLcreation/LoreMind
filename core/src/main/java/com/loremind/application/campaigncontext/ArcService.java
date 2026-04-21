package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.Arc;
import com.loremind.domain.campaigncontext.ports.ArcRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

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

    public ArcService(ArcRepository arcRepository) {
        this.arcRepository = arcRepository;
    }

    public Arc createArc(String name, String description, String campaignId, int order) {
        Arc arc = Arc.builder()
                .name(name)
                .description(description)
                .campaignId(campaignId)
                .order(order)
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

    public void deleteArc(String id) {
        arcRepository.deleteById(id);
    }

    public boolean arcExists(String id) {
        return arcRepository.existsById(id);
    }
}
