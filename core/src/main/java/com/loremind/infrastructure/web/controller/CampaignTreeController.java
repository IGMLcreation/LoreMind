package com.loremind.infrastructure.web.controller;

import com.loremind.application.campaigncontext.CampaignReadinessAssessment;
import com.loremind.application.campaigncontext.CampaignReadinessService;
import com.loremind.domain.campaigncontext.structure.Arc;
import com.loremind.domain.campaigncontext.structure.Chapter;
import com.loremind.domain.campaigncontext.bestiary.Enemy;
import com.loremind.domain.campaigncontext.ports.ArcRepository;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.campaigncontext.ports.ChapterRepository;
import com.loremind.domain.campaigncontext.ports.EnemyRepository;
import com.loremind.domain.campaigncontext.ports.NpcRepository;
import com.loremind.domain.campaigncontext.ports.QuestRepository;
import com.loremind.domain.campaigncontext.ports.RandomTableRepository;
import com.loremind.domain.campaigncontext.ports.SceneRepository;
import com.loremind.infrastructure.web.dto.campaigncontext.ArcDTO;
import com.loremind.infrastructure.web.dto.campaigncontext.ChapterDTO;
import com.loremind.infrastructure.web.dto.campaigncontext.NpcDTO;
import com.loremind.infrastructure.web.dto.campaigncontext.QuestDTO;
import com.loremind.infrastructure.web.dto.campaigncontext.RandomTableDTO;
import com.loremind.infrastructure.web.dto.campaigncontext.SceneDTO;
import com.loremind.infrastructure.web.mapper.ArcMapper;
import com.loremind.infrastructure.web.mapper.ChapterMapper;
import com.loremind.infrastructure.web.mapper.NpcMapper;
import com.loremind.infrastructure.web.mapper.QuestMapper;
import com.loremind.infrastructure.web.mapper.RandomTableMapper;
import com.loremind.infrastructure.web.mapper.SceneMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Endpoint AGRÉGÉ de l'arbre de campagne : tout ce dont la sidebar a besoin en UNE
 * requête HTTP — arcs → chapitres → scènes, PNJ, tables, ennemis, quêtes et readiness.
 *
 * <p>Remplace la rafale d'appels du front (~1 + N arcs + M chapitres + 5 listes ≈ 15-20
 * requêtes À CHAQUE navigation) : la latence HTTP par appel dominait le temps de rendu
 * de la sidebar. Les DTO/formes JSON sont STRICTEMENT ceux des endpoints unitaires
 * (mêmes mappers ; les ennemis sont sérialisés en domaine, comme {@code EnemyController}).</p>
 */
@RestController
@RequestMapping("/api/campaigns/{campaignId}/tree")
public class CampaignTreeController {

    private final CampaignRepository campaignRepository;
    private final ArcRepository arcRepository;
    private final ChapterRepository chapterRepository;
    private final SceneRepository sceneRepository;
    private final NpcRepository npcRepository;
    private final RandomTableRepository randomTableRepository;
    private final EnemyRepository enemyRepository;
    private final QuestRepository questRepository;
    private final CampaignReadinessService readinessService;
    private final ArcMapper arcMapper;
    private final ChapterMapper chapterMapper;
    private final SceneMapper sceneMapper;
    private final NpcMapper npcMapper;
    private final RandomTableMapper randomTableMapper;
    private final QuestMapper questMapper;

    public CampaignTreeController(CampaignRepository campaignRepository,
                                  ArcRepository arcRepository,
                                  ChapterRepository chapterRepository,
                                  SceneRepository sceneRepository,
                                  NpcRepository npcRepository,
                                  RandomTableRepository randomTableRepository,
                                  EnemyRepository enemyRepository,
                                  QuestRepository questRepository,
                                  CampaignReadinessService readinessService,
                                  ArcMapper arcMapper,
                                  ChapterMapper chapterMapper,
                                  SceneMapper sceneMapper,
                                  NpcMapper npcMapper,
                                  RandomTableMapper randomTableMapper,
                                  QuestMapper questMapper) {
        this.campaignRepository = campaignRepository;
        this.arcRepository = arcRepository;
        this.chapterRepository = chapterRepository;
        this.sceneRepository = sceneRepository;
        this.npcRepository = npcRepository;
        this.randomTableRepository = randomTableRepository;
        this.enemyRepository = enemyRepository;
        this.questRepository = questRepository;
        this.readinessService = readinessService;
        this.arcMapper = arcMapper;
        this.chapterMapper = chapterMapper;
        this.sceneMapper = sceneMapper;
        this.npcMapper = npcMapper;
        this.randomTableMapper = randomTableMapper;
        this.questMapper = questMapper;
    }

    /**
     * Payload complet de la sidebar. {@code chaptersByArc}/{@code scenesByChapter}
     * reprennent la forme que le front construisait lui-même ({@code CampaignTreeData}).
     */
    public record CampaignTreeDTO(
            List<ArcDTO> arcs,
            Map<String, List<ChapterDTO>> chaptersByArc,
            Map<String, List<SceneDTO>> scenesByChapter,
            List<NpcDTO> npcs,
            List<RandomTableDTO> randomTables,
            List<Enemy> enemies,
            List<QuestDTO> quests,
            CampaignReadinessAssessment readiness
    ) {}

    @GetMapping
    public ResponseEntity<CampaignTreeDTO> getTree(@PathVariable String campaignId) {
        if (!campaignRepository.existsById(campaignId)) {
            return ResponseEntity.notFound().build();
        }

        List<Arc> arcs = arcRepository.findByCampaignId(campaignId);
        Map<String, List<ChapterDTO>> chaptersByArc = new HashMap<>();
        Map<String, List<SceneDTO>> scenesByChapter = new HashMap<>();
        for (Arc arc : arcs) {
            List<Chapter> chapters = chapterRepository.findByArcId(arc.getId());
            chaptersByArc.put(arc.getId(),
                    chapters.stream().map(chapterMapper::toDTO).collect(Collectors.toList()));
            for (Chapter chapter : chapters) {
                scenesByChapter.put(chapter.getId(),
                        sceneRepository.findByChapterId(chapter.getId()).stream()
                                .map(sceneMapper::toDTO).collect(Collectors.toList()));
            }
        }

        return ResponseEntity.ok(new CampaignTreeDTO(
                arcs.stream().map(arcMapper::toDTO).collect(Collectors.toList()),
                chaptersByArc,
                scenesByChapter,
                npcRepository.findByCampaignId(campaignId).stream()
                        .map(npcMapper::toDTO).collect(Collectors.toList()),
                randomTableRepository.findByCampaignId(campaignId).stream()
                        .map(randomTableMapper::toDTO).collect(Collectors.toList()),
                enemyRepository.findByCampaignId(campaignId),
                questRepository.findByCampaignId(campaignId).stream()
                        .map(questMapper::toDTO).collect(Collectors.toList()),
                readinessService.assess(campaignId)));
    }
}
