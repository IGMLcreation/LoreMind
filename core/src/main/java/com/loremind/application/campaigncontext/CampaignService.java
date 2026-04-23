package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.Arc;
import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.Chapter;
import com.loremind.domain.campaigncontext.ports.ArcRepository;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.campaigncontext.ports.ChapterRepository;
import com.loremind.domain.campaigncontext.ports.CharacterRepository;
import com.loremind.domain.campaigncontext.ports.SceneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service d'application pour le contexte Campaign.
 * Orchestre la logique métier en utilisant le Port CampaignRepository.
 * Fait partie de la couche Application de l'Architecture Hexagonale.
 */
@Service
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final ArcRepository arcRepository;
    private final ChapterRepository chapterRepository;
    private final SceneRepository sceneRepository;
    private final CharacterRepository characterRepository;

    public CampaignService(
            CampaignRepository campaignRepository,
            ArcRepository arcRepository,
            ChapterRepository chapterRepository,
            SceneRepository sceneRepository,
            CharacterRepository characterRepository) {
        this.campaignRepository = campaignRepository;
        this.arcRepository = arcRepository;
        this.chapterRepository = chapterRepository;
        this.sceneRepository = sceneRepository;
        this.characterRepository = characterRepository;
    }

    /**
     * Parameter Object pour la création / mise à jour d'une Campaign.
     * Évite une signature à rallonge et rend les évolutions futures (theme,
     * coverImageUrl, etc.) sans casser les appelants.
     *
     * <p>{@code loreId} est nullable : une campagne peut exister sans univers associé.</p>
     */
    public record CampaignData(String name, String description, String loreId, String gameSystemId) {}

    /**
     * Compte des entités qui seront supprimées en cascade si la campagne est effacée.
     * Utilisé par l'UI pour afficher un récapitulatif dans le dialogue de confirmation.
     */
    public record DeletionImpact(int arcs, int chapters, int scenes, int characters) {}

    public Campaign createCampaign(CampaignData data) {
        Campaign campaign = Campaign.builder()
                .name(data.name())
                .description(data.description())
                .loreId(normalizeId(data.loreId()))
                .gameSystemId(normalizeId(data.gameSystemId()))
                .arcsCount(0)
                .build();
        return campaignRepository.save(campaign);
    }

    public Optional<Campaign> getCampaignById(String id) {
        return campaignRepository.findById(id);
    }

    public List<Campaign> getAllCampaigns() {
        return campaignRepository.findAll();
    }

    public Campaign updateCampaign(String id, CampaignData data) {
        Optional<Campaign> existingCampaign = campaignRepository.findById(id);
        if (existingCampaign.isEmpty()) {
            throw new IllegalArgumentException("Campaign non trouvé avec l'ID: " + id);
        }

        Campaign campaign = existingCampaign.get();
        campaign.setName(data.name());
        campaign.setDescription(data.description());
        campaign.setLoreId(normalizeId(data.loreId()));
        campaign.setGameSystemId(normalizeId(data.gameSystemId()));
        return campaignRepository.save(campaign);
    }

    /**
     * Normalise un ID entrant : une chaîne vide/blanche est traitée comme "pas de lien".
     * Utile car les payloads JSON peuvent envoyer "" au lieu de null.
     */
    private String normalizeId(String id) {
        return (id == null || id.isBlank()) ? null : id;
    }

    /**
     * Calcule l'impact d'une suppression en cascade : nombre d'arcs, chapitres,
     * scènes et personnages qui disparaîtront avec la campagne. Utilisé par l'UI
     * pour afficher "X arcs, Y chapitres, Z scènes seront supprimés".
     */
    public DeletionImpact getDeletionImpact(String id) {
        List<Arc> arcs = arcRepository.findByCampaignId(id);
        int chapterTotal = 0;
        int sceneTotal = 0;
        for (Arc arc : arcs) {
            List<Chapter> chapters = chapterRepository.findByArcId(arc.getId());
            chapterTotal += chapters.size();
            for (Chapter chapter : chapters) {
                sceneTotal += sceneRepository.findByChapterId(chapter.getId()).size();
            }
        }
        int characterTotal = characterRepository.findByCampaignId(id).size();
        return new DeletionImpact(arcs.size(), chapterTotal, sceneTotal, characterTotal);
    }

    /**
     * Supprime la campagne et toutes ses entités dépendantes (arcs → chapitres →
     * scènes, plus les personnages). L'opération est transactionnelle : soit
     * tout disparaît, soit rien ne change. Les FKs applicatives n'ayant pas
     * de contrainte CASCADE au niveau DB, on orchestre la cascade ici.
     */
    @Transactional
    public void deleteCampaign(String id) {
        List<Arc> arcs = arcRepository.findByCampaignId(id);
        for (Arc arc : arcs) {
            List<Chapter> chapters = chapterRepository.findByArcId(arc.getId());
            for (Chapter chapter : chapters) {
                for (var scene : sceneRepository.findByChapterId(chapter.getId())) {
                    sceneRepository.deleteById(scene.getId());
                }
                chapterRepository.deleteById(chapter.getId());
            }
            arcRepository.deleteById(arc.getId());
        }
        for (var character : characterRepository.findByCampaignId(id)) {
            characterRepository.deleteById(character.getId());
        }
        campaignRepository.deleteById(id);
    }

    public boolean campaignExists(String id) {
        return campaignRepository.existsById(id);
    }

    public List<Campaign> searchCampaigns(String query) {
        if (query == null || query.isBlank()) return List.of();
        return campaignRepository.searchByName(query.trim());
    }
}
