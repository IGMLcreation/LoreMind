package com.loremind.infrastructure.transfer.pdf;

import com.loremind.domain.campaigncontext.quest.NodeType;
import com.loremind.domain.campaigncontext.quest.QuestNodeRef;
import com.loremind.domain.campaigncontext.structure.ArcType;
import com.loremind.infrastructure.persistence.entity.*;
import com.loremind.infrastructure.persistence.jpa.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.ToIntFunction;

/**
 * Charge la {@link Structure} narrative d'une campagne en UNE passe : arcs/chapitres/scenes
 * et quetes tries par ordre manuel, index de noms, et resolution de la FUSION quete/conteneur
 * de l'arbre (cf. QuestService) — l'arc SYSTEM « Quetes libres » et les chapitres-conteneurs
 * sont identifies ici pour que le rendu ne duplique pas les quetes.
 */
@Component
class PdfStructureLoader {

    private final ArcJpaRepository arcRepo;
    private final ChapterJpaRepository chapterRepo;
    private final SceneJpaRepository sceneRepo;
    private final QuestJpaRepository questRepo;
    private final EnemyJpaRepository enemyRepo;

    PdfStructureLoader(ArcJpaRepository arcRepo, ChapterJpaRepository chapterRepo,
                       SceneJpaRepository sceneRepo, QuestJpaRepository questRepo,
                       EnemyJpaRepository enemyRepo) {
        this.arcRepo = arcRepo;
        this.chapterRepo = chapterRepo;
        this.sceneRepo = sceneRepo;
        this.questRepo = questRepo;
        this.enemyRepo = enemyRepo;
    }

    Structure load(CampaignJpaEntity campaign) {
        Structure st = new Structure();
        Set<Long> systemArcIds = loadArcs(st, campaign);
        Map<Long, Long> arcOfChapter = loadChaptersAndScenes(st);
        loadEnemyNames(st, campaign);
        loadQuests(st, campaign);
        linkQuestsToContainers(st, arcOfChapter, systemArcIds);
        return st;
    }

    /** Arcs tries + separation visible/systeme ; alimente st.arcs et st.visibleArcs. */
    private Set<Long> loadArcs(Structure st, CampaignJpaEntity campaign) {
        st.arcs = sortByOrder(arcRepo.findByCampaignId(campaign.getId()), ArcJpaEntity::getOrder);
        List<ArcJpaEntity> visible = new ArrayList<>();
        Set<Long> systemArcIds = new HashSet<>();
        for (ArcJpaEntity arc : st.arcs) {
            if (arc.getType() == ArcType.SYSTEM) systemArcIds.add(arc.getId());
            else visible.add(arc);
        }
        st.visibleArcs = visible;
        return systemArcIds;
    }

    /** Chapitres (par arc) + scenes (par chapitre), tries + index de noms. @return arc de chaque chapitre. */
    private Map<Long, Long> loadChaptersAndScenes(Structure st) {
        Map<Long, Long> arcOfChapter = new HashMap<>();
        for (ArcJpaEntity arc : st.arcs) {
            List<ChapterJpaEntity> chapters = sortByOrder(chapterRepo.findByArcId(arc.getId()), ChapterJpaEntity::getOrder);
            st.chaptersByArc.put(arc.getId(), chapters);
            for (ChapterJpaEntity ch : chapters) {
                arcOfChapter.put(ch.getId(), arc.getId());
                st.chapterNames.put(String.valueOf(ch.getId()), ch.getName());
                List<SceneJpaEntity> scenes = sortByOrder(sceneRepo.findByChapterId(ch.getId()), SceneJpaEntity::getOrder);
                st.scenesByChapter.put(ch.getId(), scenes);
                for (SceneJpaEntity sc : scenes) st.sceneNames.put(String.valueOf(sc.getId()), sc.getName());
            }
        }
        return arcOfChapter;
    }

    private void loadEnemyNames(Structure st, CampaignJpaEntity campaign) {
        for (EnemyJpaEntity e : enemyRepo.findByCampaignIdOrderByOrderAsc(campaign.getId())) {
            st.enemyNames.put(String.valueOf(e.getId()), e.getName());
        }
    }

    private void loadQuests(Structure st, CampaignJpaEntity campaign) {
        st.quests = sortByOrder(questRepo.findByCampaignId(campaign.getId()), QuestJpaEntity::getOrder);
        for (QuestJpaEntity q : st.quests) st.questNames.put(String.valueOf(q.getId()), q.getName());
    }

    /**
     * Meme regle que QuestService.isContainerOf : un chapitre reference est le CONTENEUR
     * de la quete s'il vit dans l'arc de la quete (jumeau hub) ou dans l'arc SYSTEM.
     */
    private void linkQuestsToContainers(Structure st, Map<Long, Long> arcOfChapter, Set<Long> systemArcIds) {
        for (QuestJpaEntity q : st.quests) {
            if (!isFusedInNarrative(st, q, arcOfChapter, systemArcIds)) {
                st.standaloneQuests.add(q);
            }
        }
    }

    private boolean isFusedInNarrative(Structure st, QuestJpaEntity q,
                                       Map<Long, Long> arcOfChapter, Set<Long> systemArcIds) {
        if (q.getNodes() == null) return false;
        boolean fused = false;
        for (QuestNodeRef n : q.getNodes()) {
            Long cid = containerChapterId(n, q, arcOfChapter, systemArcIds);
            if (cid == null) continue;
            st.questByContainerChapter.putIfAbsent(cid, q);
            if (st.questByContainerChapter.get(cid) == q && !systemArcIds.contains(arcOfChapter.get(cid))) {
                fused = true;
            }
        }
        return fused;
    }

    /** Id du chapitre-conteneur si ce noeud EN est un pour cette quete, sinon null. */
    private static Long containerChapterId(QuestNodeRef n, QuestJpaEntity q,
                                           Map<Long, Long> arcOfChapter, Set<Long> systemArcIds) {
        if (n.nodeType() != NodeType.CHAPTER) return null;
        Long cid;
        try { cid = Long.parseLong(n.nodeId()); } catch (NumberFormatException ex) { return null; }
        Long arcId = arcOfChapter.get(cid);
        if (arcId == null) return null;
        boolean container = (q.getArcId() != null && q.getArcId().equals(arcId)) || systemArcIds.contains(arcId);
        return container ? cid : null;
    }

    /** Tri par ORDRE manuel (glisser-déposer) — cohérent avec l'arbre et les cartes. */
    private static <T> List<T> sortByOrder(List<T> list, ToIntFunction<T> order) {
        List<T> copy = new ArrayList<>(list);
        copy.sort(Comparator.comparingInt(order));
        return copy;
    }
}
