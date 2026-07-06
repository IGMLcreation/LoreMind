package com.loremind.infrastructure.transfer;

import com.loremind.domain.playcontext.ClockTrigger;
import com.loremind.infrastructure.persistence.entity.LoreNodeJpaEntity;
import com.loremind.infrastructure.persistence.jpa.ArcJpaRepository;
import com.loremind.infrastructure.persistence.jpa.CampaignJpaRepository;
import com.loremind.infrastructure.persistence.jpa.ChapterJpaRepository;
import com.loremind.infrastructure.persistence.jpa.ClockJpaRepository;
import com.loremind.infrastructure.persistence.jpa.LoreNodeJpaRepository;
import com.loremind.infrastructure.persistence.jpa.NpcJpaRepository;
import com.loremind.infrastructure.persistence.jpa.PageJpaRepository;
import com.loremind.infrastructure.persistence.jpa.QuestJpaRepository;
import com.loremind.infrastructure.persistence.jpa.SceneJpaRepository;
import com.loremind.infrastructure.persistence.jpa.TemplateJpaRepository;
import com.loremind.infrastructure.transfer.dto.ContentExport;
import org.springframework.stereotype.Component;

/**
 * 2e passe d'un import (cf. {@link ImportService}) : remappe les références qui
 * pointent vers des types insérés plus tard (parentId, defaultNodeId, refs faibles
 * String) puis re-save. Les références vers un id absent des maps (ex. relatedPageId
 * hors export) sont CONSERVÉES telles quelles.
 */
@Component
class ImportReferenceRemapper {

    private final LoreNodeJpaRepository loreNodeRepo;
    private final TemplateJpaRepository templateRepo;
    private final PageJpaRepository pageRepo;
    private final CampaignJpaRepository campaignRepo;
    private final ArcJpaRepository arcRepo;
    private final ChapterJpaRepository chapterRepo;
    private final NpcJpaRepository npcRepo;
    private final SceneJpaRepository sceneRepo;
    private final QuestJpaRepository questRepo;
    private final ClockJpaRepository clockRepo;

    ImportReferenceRemapper(LoreNodeJpaRepository loreNodeRepo,
                            TemplateJpaRepository templateRepo,
                            PageJpaRepository pageRepo,
                            CampaignJpaRepository campaignRepo,
                            ArcJpaRepository arcRepo,
                            ChapterJpaRepository chapterRepo,
                            NpcJpaRepository npcRepo,
                            SceneJpaRepository sceneRepo,
                            QuestJpaRepository questRepo,
                            ClockJpaRepository clockRepo) {
        this.loreNodeRepo = loreNodeRepo;
        this.templateRepo = templateRepo;
        this.pageRepo = pageRepo;
        this.campaignRepo = campaignRepo;
        this.arcRepo = arcRepo;
        this.chapterRepo = chapterRepo;
        this.npcRepo = npcRepo;
        this.sceneRepo = sceneRepo;
        this.questRepo = questRepo;
        this.clockRepo = clockRepo;
    }

    void remap(ImportIdMaps maps) {
        remapLoreNodeParents(maps);
        remapTemplateDefaultNodes(maps);
        remapCampaignWeakRefs(maps);
        remapPageRelatedPages(maps);
        remapArcRelatedPages(maps);
        remapChapterRelatedPages(maps);
        remapNpcRelatedPages(maps);
        remapSceneRefs(maps);
        remapQuestRefs(maps);
        remapClockTriggerRefs(maps);
    }

    /** LoreNode.parentId. */
    private void remapLoreNodeParents(ImportIdMaps maps) {
        for (LoreNodeJpaEntity e : maps.loreNodesToFix) {
            Long newParent = maps.loreNodeMap.get(e.getParentId());
            if (newParent != null) {
                e.setParentId(newParent);
                loreNodeRepo.save(e);
            }
        }
    }

    /** Template.defaultNodeId. */
    private void remapTemplateDefaultNodes(ImportIdMaps maps) {
        for (ContentExport.TemplateDto d : maps.templatesWithDefaultNode) {
            Long newTemplateId = maps.templateMap.get(d.id());
            Long newNode = maps.loreNodeMap.get(d.defaultNodeId());
            if (newTemplateId != null && newNode != null) {
                templateRepo.findById(newTemplateId).ifPresent(t -> {
                    t.setDefaultNodeId(newNode);
                    templateRepo.save(t);
                });
            }
        }
    }

    /** Campaign.loreId & gameSystemId (refs faibles String -> remap via maps Long). */
    private void remapCampaignWeakRefs(ImportIdMaps maps) {
        for (Long newCampaignId : maps.campaignMap.values()) {
            campaignRepo.findById(newCampaignId).ifPresent(c -> {
                String newLore = IdRemapper.remapStringId(maps.loreMap, c.getLoreId());
                String newGs = IdRemapper.remapStringId(maps.gameSystemMap, c.getGameSystemId());
                c.setLoreId(newLore);
                c.setGameSystemId(newGs);
                campaignRepo.save(c);
            });
        }
    }

    /** Page.relatedPageIds. */
    private void remapPageRelatedPages(ImportIdMaps maps) {
        for (Long newPageId : maps.pageMap.values()) {
            pageRepo.findById(newPageId).ifPresent(p -> {
                p.setRelatedPageIds(IdRemapper.remapStringList(maps.pageMap, p.getRelatedPageIds()));
                pageRepo.save(p);
            });
        }
    }

    /** Arc.relatedPageIds. */
    private void remapArcRelatedPages(ImportIdMaps maps) {
        for (Long newArcId : maps.arcMap.values()) {
            arcRepo.findById(newArcId).ifPresent(a -> {
                a.setRelatedPageIds(IdRemapper.remapStringList(maps.pageMap, a.getRelatedPageIds()));
                arcRepo.save(a);
            });
        }
    }

    /** Chapter.relatedPageIds (les chapitres n'ont plus de prérequis depuis le Niveau 1). */
    private void remapChapterRelatedPages(ImportIdMaps maps) {
        for (Long newChapterId : maps.chapterMap.values()) {
            chapterRepo.findById(newChapterId).ifPresent(c -> {
                c.setRelatedPageIds(IdRemapper.remapStringList(maps.pageMap, c.getRelatedPageIds()));
                chapterRepo.save(c);
            });
        }
    }

    /** Npc.relatedPageIds. */
    private void remapNpcRelatedPages(ImportIdMaps maps) {
        for (Long newNpcId : maps.npcMap.values()) {
            npcRepo.findById(newNpcId).ifPresent(n -> {
                n.setRelatedPageIds(IdRemapper.remapStringList(maps.pageMap, n.getRelatedPageIds()));
                npcRepo.save(n);
            });
        }
    }

    /** Scene.relatedPageIds + enemyIds(map Enemy) + branches.targetSceneId(map Scene). */
    private void remapSceneRefs(ImportIdMaps maps) {
        for (Long newSceneId : maps.sceneMap.values()) {
            sceneRepo.findById(newSceneId).ifPresent(s -> {
                s.setRelatedPageIds(IdRemapper.remapStringList(maps.pageMap, s.getRelatedPageIds()));
                s.setEnemyIds(IdRemapper.remapStringList(maps.enemyMap, s.getEnemyIds()));
                s.setBranches(IdRemapper.remapBranches(maps.sceneMap, s.getBranches()));
                sceneRepo.save(s);
            });
        }
    }

    /**
     * Quest : prereqs(QuestCompleted -> questMap), nodes(CHAPTER->chapterMap / SCENE->sceneMap),
     * relatedPageIds(pageMap). En 2e passe car sceneMap n'est prêt qu'ici.
     */
    private void remapQuestRefs(ImportIdMaps maps) {
        for (Long newQuestId : maps.questMap.values()) {
            questRepo.findById(newQuestId).ifPresent(q -> {
                q.setPrerequisites(IdRemapper.remapPrerequisites(maps.questMap, q.getPrerequisites()));
                q.setNodes(IdRemapper.remapQuestNodes(maps.chapterMap, maps.sceneMap, q.getNodes()));
                q.setRelatedPageIds(IdRemapper.remapStringList(maps.pageMap, q.getRelatedPageIds()));
                questRepo.save(q);
            });
        }
    }

    /**
     * Clock : triggerRef d'une horloge QUEST_COMPLETED remappé vers la quête importée
     * (FLAG_SET = nom de fait, SESSION_ENDED = sans ref : rien à remapper).
     */
    private void remapClockTriggerRefs(ImportIdMaps maps) {
        for (Long newClockId : maps.clockMap.values()) {
            clockRepo.findById(newClockId).ifPresent(c -> {
                if (c.getTriggerType() == ClockTrigger.QUEST_COMPLETED) {
                    c.setTriggerRef(IdRemapper.remapStringId(maps.questMap, c.getTriggerRef()));
                    clockRepo.save(c);
                }
            });
        }
    }
}
