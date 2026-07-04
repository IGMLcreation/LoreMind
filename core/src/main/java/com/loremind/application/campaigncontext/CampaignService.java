package com.loremind.application.campaigncontext;

import com.loremind.application.playcontext.PlaythroughService;
import com.loremind.domain.campaigncontext.Arc;
import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.Chapter;
import com.loremind.domain.campaigncontext.ports.ArcRepository;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.campaigncontext.ports.ChapterRepository;
import com.loremind.domain.campaigncontext.ports.SceneRepository;
import com.loremind.domain.playcontext.Playthrough;
import com.loremind.domain.playcontext.ports.PlaythroughRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service d'application pour le contexte Campaign (scénario).
 *
 * <p>Depuis Playthrough : les PJ et les sessions ne sont plus rattachés directement
 * à la Campagne ; ils dépendent d'un Playthrough (Partie). La cascade de suppression
 * d'une campagne englobe donc ses Playthroughs (qui à leur tour cascadent).</p>
 */
@Service
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final ArcRepository arcRepository;
    private final ChapterRepository chapterRepository;
    private final SceneRepository sceneRepository;
    private final PlaythroughRepository playthroughRepository;
    private final PlaythroughService playthroughService;

    public CampaignService(
            CampaignRepository campaignRepository,
            ArcRepository arcRepository,
            ChapterRepository chapterRepository,
            SceneRepository sceneRepository,
            PlaythroughRepository playthroughRepository,
            PlaythroughService playthroughService) {
        this.campaignRepository = campaignRepository;
        this.arcRepository = arcRepository;
        this.chapterRepository = chapterRepository;
        this.sceneRepository = sceneRepository;
        this.playthroughRepository = playthroughRepository;
        this.playthroughService = playthroughService;
    }

    public record CampaignData(String name, String description, String loreId, String gameSystemId, int playerCount) {}

    public record DeletionImpact(int arcs, int chapters, int scenes, int playthroughs) {}

    public Campaign createCampaign(CampaignData data) {
        Campaign campaign = Campaign.builder()
                .name(data.name())
                .description(data.description())
                .loreId(normalizeId(data.loreId()))
                .gameSystemId(normalizeId(data.gameSystemId()))
                .arcsCount(0)
                .playerCount(data.playerCount())
                .build();
        Campaign saved = campaignRepository.save(campaign);

        // Une campagne sans Partie n'a pas de sens jouable : on crée d'office
        // une "Partie principale" pour que l'utilisateur puisse jouer immédiatement.
        playthroughService.create(saved.getId(), "Partie principale", null);
        return saved;
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
        campaign.setPlayerCount(data.playerCount());
        return campaignRepository.save(campaign);
    }

    /**
     * Sauvegarde les positions du graphe de campagne (JSON opaque, état de
     * présentation possédé par le front). Null/vide = retour à la disposition
     * automatique. Taille bornée : ce champ ne doit pas devenir un fourre-tout.
     */
    public void updateGraphPositions(String id, String positionsJson) {
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Campaign non trouvé avec l'ID: " + id));
        String value = (positionsJson == null || positionsJson.isBlank()) ? null : positionsJson;
        if (value != null && value.length() > 200_000) {
            throw new IllegalArgumentException("Positions de graphe trop volumineuses");
        }
        campaign.setGraphPositions(value);
        campaignRepository.save(campaign);
    }

    private String normalizeId(String id) {
        return (id == null || id.isBlank()) ? null : id;
    }

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
        int playthroughTotal = playthroughRepository.findByCampaignId(id).size();
        return new DeletionImpact(arcs.size(), chapterTotal, sceneTotal, playthroughTotal);
    }

    @Transactional
    public void deleteCampaign(String id) {
        // 1. Cascade des Playthroughs (qui cascadent eux-mêmes sur PJ/sessions/valeurs flags/progressions).
        for (Playthrough p : playthroughRepository.findByCampaignId(id)) {
            playthroughService.delete(p.getId());
        }
        // 2. Cascade du scénario : arcs → chapitres → scènes
        //    (Pas de déclarations globales de faits : ils existent implicitement via les
        //    prérequis FLAG_SET des chapitres, qui partent avec les chapitres.)
        for (Arc arc : arcRepository.findByCampaignId(id)) {
            for (Chapter chapter : chapterRepository.findByArcId(arc.getId())) {
                for (var scene : sceneRepository.findByChapterId(chapter.getId())) {
                    sceneRepository.deleteById(scene.getId());
                }
                chapterRepository.deleteById(chapter.getId());
            }
            arcRepository.deleteById(arc.getId());
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
