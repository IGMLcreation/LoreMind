package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.structure.Arc;
import com.loremind.domain.campaigncontext.structure.ArcType;
import com.loremind.domain.campaigncontext.structure.Chapter;
import com.loremind.domain.campaigncontext.quest.NodeType;
import com.loremind.domain.campaigncontext.quest.Quest;
import com.loremind.domain.campaigncontext.quest.QuestNodeRef;
import com.loremind.domain.campaigncontext.structure.Scene;
import com.loremind.domain.campaigncontext.ports.ArcRepository;
import com.loremind.domain.campaigncontext.ports.ChapterRepository;
import com.loremind.domain.campaigncontext.ports.QuestRepository;
import com.loremind.domain.campaigncontext.ports.SceneRepository;
import com.loremind.domain.playcontext.ports.QuestProgressionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test unitaire pour QuestService — cycle de vie d'une quête d'arc HUB et de son
 * CONTENEUR de scènes (chapitre jumeau) : provisioning à la création, suivi du
 * renommage, nettoyage à la suppression.
 */
@ExtendWith(MockitoExtension.class)
class QuestServiceTest {

    @Mock private QuestRepository questRepository;
    @Mock private QuestProgressionRepository progressionRepository;
    @Mock private ChapterRepository chapterRepository;
    @Mock private SceneRepository sceneRepository;
    @Mock private ArcRepository arcRepository;

    @InjectMocks private QuestService service;

    // ─────────────── createQuest : provisioning du conteneur ───────────────

    @Test
    void createQuest_attachedToHubArc_provisionsContainerChapter() {
        when(chapterRepository.findByArcId("arc-h")).thenReturn(List.of(
                Chapter.builder().id("chap-1").arcId("arc-h").order(2).build())); // max order = 2
        when(chapterRepository.save(any(Chapter.class))).thenAnswer(inv -> {
            Chapter ch = inv.getArgument(0);
            ch.setId("chap-new");
            return ch;
        });
        when(questRepository.save(any(Quest.class))).thenAnswer(inv -> inv.getArgument(0));

        Quest created = service.createQuest(Quest.builder()
                .campaignId("camp").arcId("arc-h").name("Enquête").order(0).build());

        // Conteneur créé dans l'arc, même nom, ordre à la suite, et référencé en nœud CHAPTER.
        verify(chapterRepository).save(any(Chapter.class));
        assertEquals(1, created.getNodes().size());
        QuestNodeRef node = created.getNodes().get(0);
        assertEquals(NodeType.CHAPTER, node.nodeType());
        assertEquals("chap-new", node.nodeId());
    }

    @Test
    void createQuest_free_provisionsContainerInNewSystemArc() {
        // Aucun arc SYSTEM : il est créé au premier besoin, puis le conteneur dedans.
        when(arcRepository.findByCampaignId("camp")).thenReturn(List.of(
                Arc.builder().id("arc-lin").campaignId("camp").name("Arc").type(ArcType.LINEAR).build()));
        when(arcRepository.save(any(Arc.class))).thenAnswer(inv -> {
            Arc a = inv.getArgument(0);
            a.setId("arc-sys");
            return a;
        });
        when(chapterRepository.findByArcId("arc-sys")).thenReturn(List.of());
        when(chapterRepository.save(any(Chapter.class))).thenAnswer(inv -> {
            Chapter ch = inv.getArgument(0);
            ch.setId("chap-free");
            return ch;
        });
        when(questRepository.save(any(Quest.class))).thenAnswer(inv -> inv.getArgument(0));

        Quest created = service.createQuest(Quest.builder()
                .campaignId("camp").name("Libre").order(0).build());

        verify(arcRepository).save(any(Arc.class)); // arc SYSTEM créé
        assertEquals(1, created.getNodes().size());
        assertEquals("chap-free", created.getNodes().get(0).nodeId());
    }

    @Test
    void createQuest_free_reusesExistingSystemArc() {
        when(arcRepository.findByCampaignId("camp")).thenReturn(List.of(
                Arc.builder().id("arc-sys").campaignId("camp").name("Quêtes libres").type(ArcType.SYSTEM).build()));
        when(chapterRepository.findByArcId("arc-sys")).thenReturn(List.of());
        when(chapterRepository.save(any(Chapter.class))).thenAnswer(inv -> {
            Chapter ch = inv.getArgument(0);
            ch.setId("chap-free");
            return ch;
        });
        when(questRepository.save(any(Quest.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createQuest(Quest.builder().campaignId("camp").name("Libre").order(0).build());

        verify(arcRepository, never()).save(any()); // arc SYSTEM existant réutilisé
        verify(chapterRepository).save(any(Chapter.class));
    }

    @Test
    void createQuest_attachedWithExistingNodes_noProvisioning() {
        when(questRepository.save(any(Quest.class))).thenAnswer(inv -> inv.getArgument(0));

        Quest created = service.createQuest(Quest.builder()
                .campaignId("camp").arcId("arc-h").name("Q").order(0)
                .nodes(List.of(new QuestNodeRef(NodeType.SCENE, "scene-1", 0))).build());

        verify(chapterRepository, never()).save(any());
        assertEquals(1, created.getNodes().size());
    }

    // ─────────────── updateQuest : le conteneur suit le renommage ───────────────

    @Test
    void updateQuest_rename_renamesMatchingContainer() {
        Quest existing = Quest.builder().id("q-1").campaignId("camp").arcId("arc-h").name("Ancien nom")
                .nodes(List.of(new QuestNodeRef(NodeType.CHAPTER, "chap-1", 0))).build();
        when(questRepository.findById("q-1")).thenReturn(Optional.of(existing));
        when(questRepository.save(any(Quest.class))).thenAnswer(inv -> inv.getArgument(0));
        Chapter container = Chapter.builder().id("chap-1").arcId("arc-h").name("Ancien nom").build();
        when(chapterRepository.findById("chap-1")).thenReturn(Optional.of(container));
        when(chapterRepository.save(any(Chapter.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateQuest("q-1", Quest.builder()
                .campaignId("camp").arcId("arc-h").name("Nouveau nom")
                .nodes(List.of(new QuestNodeRef(NodeType.CHAPTER, "chap-1", 0))).build());

        assertEquals("Nouveau nom", container.getName());
        verify(chapterRepository).save(container);
    }

    @Test
    void updateQuest_legacyQuestWithoutNodes_selfHealsWithContainer() {
        // Quête libre créée AVANT le provisioning systématique : réparée à la sauvegarde.
        Quest existing = Quest.builder().id("q-1").campaignId("camp").name("Legacy").build();
        when(questRepository.findById("q-1")).thenReturn(Optional.of(existing));
        when(questRepository.save(any(Quest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(arcRepository.findByCampaignId("camp")).thenReturn(List.of(
                Arc.builder().id("arc-sys").campaignId("camp").name("Quêtes libres").type(ArcType.SYSTEM).build()));
        when(chapterRepository.findByArcId("arc-sys")).thenReturn(List.of());
        when(chapterRepository.save(any(Chapter.class))).thenAnswer(inv -> {
            Chapter ch = inv.getArgument(0);
            ch.setId("chap-heal");
            return ch;
        });

        Quest saved = service.updateQuest("q-1", Quest.builder()
                .campaignId("camp").name("Legacy").build());

        assertEquals(1, saved.getNodes().size());
        assertEquals("chap-heal", saved.getNodes().get(0).nodeId());
    }

    // ─────────────── deleteQuest : nettoyage du conteneur ───────────────

    @Test
    void deleteQuest_removesEmptyUnreferencedContainer() {
        Quest quest = Quest.builder().id("q-1").campaignId("camp").arcId("arc-h").name("Q")
                .nodes(List.of(new QuestNodeRef(NodeType.CHAPTER, "chap-1", 0))).build();
        when(questRepository.findById("q-1")).thenReturn(Optional.of(quest));
        when(questRepository.findByCampaignId("camp")).thenReturn(List.of()); // plus aucune quête
        when(chapterRepository.findById("chap-1"))
                .thenReturn(Optional.of(Chapter.builder().id("chap-1").arcId("arc-h").name("Q").build()));
        when(sceneRepository.findByChapterId("chap-1")).thenReturn(List.of()); // conteneur vide

        service.deleteQuest("q-1");

        verify(chapterRepository).deleteById("chap-1");   // pas de « chapitre vide » fantôme
        verify(progressionRepository).deleteByQuestId("q-1");
        verify(questRepository).deleteById("q-1");
    }

    @Test
    void deleteQuest_keepsContainerWithScenes() {
        Quest quest = Quest.builder().id("q-1").campaignId("camp").arcId("arc-h").name("Q")
                .nodes(List.of(new QuestNodeRef(NodeType.CHAPTER, "chap-1", 0))).build();
        when(questRepository.findById("q-1")).thenReturn(Optional.of(quest));
        when(questRepository.findByCampaignId("camp")).thenReturn(List.of());
        when(chapterRepository.findById("chap-1"))
                .thenReturn(Optional.of(Chapter.builder().id("chap-1").arcId("arc-h").name("Q").build()));
        when(sceneRepository.findByChapterId("chap-1")).thenReturn(List.of(
                Scene.builder().id("s-1").chapterId("chap-1").name("S").build()));

        service.deleteQuest("q-1");

        // Contenu préservé : le conteneur redevient un chapitre visible de l'arc.
        verify(chapterRepository, never()).deleteById(anyString());
    }

    @Test
    void deleteQuest_freeQuest_removesEmptyContainerFromSystemArc() {
        Quest quest = Quest.builder().id("q-1").campaignId("camp").name("Libre") // arcId null = libre
                .nodes(List.of(new QuestNodeRef(NodeType.CHAPTER, "chap-free", 0))).build();
        when(questRepository.findById("q-1")).thenReturn(Optional.of(quest));
        when(questRepository.findByCampaignId("camp")).thenReturn(List.of());
        when(chapterRepository.findById("chap-free"))
                .thenReturn(Optional.of(Chapter.builder().id("chap-free").arcId("arc-sys").name("Libre").build()));
        when(arcRepository.findById("arc-sys"))
                .thenReturn(Optional.of(Arc.builder().id("arc-sys").type(ArcType.SYSTEM).build()));
        when(sceneRepository.findByChapterId("chap-free")).thenReturn(List.of());

        service.deleteQuest("q-1");

        verify(chapterRepository).deleteById("chap-free");
    }

    @Test
    void deleteQuest_transverseLink_neverDeletesLinkedRealChapter() {
        // Quête libre LIANT un chapitre réel d'un arc LINÉAIRE (même vide) : intouchable.
        Quest quest = Quest.builder().id("q-1").campaignId("camp").name("Transverse")
                .nodes(List.of(new QuestNodeRef(NodeType.CHAPTER, "chap-real", 0))).build();
        when(questRepository.findById("q-1")).thenReturn(Optional.of(quest));
        when(questRepository.findByCampaignId("camp")).thenReturn(List.of());
        when(chapterRepository.findById("chap-real"))
                .thenReturn(Optional.of(Chapter.builder().id("chap-real").arcId("arc-lin").name("Réel").build()));
        when(arcRepository.findById("arc-lin"))
                .thenReturn(Optional.of(Arc.builder().id("arc-lin").type(ArcType.LINEAR).build()));
        // NB : pas de stub sceneRepository — un chapitre non-conteneur est écarté avant toute lecture des scènes.

        service.deleteQuest("q-1");

        verify(chapterRepository, never()).deleteById(anyString());
    }

    @Test
    void deleteQuest_freeQuest_cascadesScenesOfSystemContainer() {
        // Conteneur d'arc SYSTEM : INVISIBLE une fois la quête supprimée -> il part avec
        // ses scènes (sinon il pourrit en fantôme inaccessible qui ressort dans les exports).
        Quest quest = Quest.builder().id("q-1").campaignId("camp").name("Libre")
                .nodes(List.of(new QuestNodeRef(NodeType.CHAPTER, "chap-free", 0))).build();
        when(questRepository.findById("q-1")).thenReturn(Optional.of(quest));
        when(questRepository.findByCampaignId("camp")).thenReturn(List.of());
        when(chapterRepository.findById("chap-free"))
                .thenReturn(Optional.of(Chapter.builder().id("chap-free").arcId("arc-sys").name("Libre").build()));
        when(arcRepository.findById("arc-sys"))
                .thenReturn(Optional.of(Arc.builder().id("arc-sys").type(ArcType.SYSTEM).build()));
        when(sceneRepository.findByChapterId("chap-free")).thenReturn(List.of(
                Scene.builder().id("s-1").chapterId("chap-free").name("S1").build(),
                Scene.builder().id("s-2").chapterId("chap-free").name("S2").build()));

        service.deleteQuest("q-1");

        verify(sceneRepository).deleteById("s-1");
        verify(sceneRepository).deleteById("s-2");
        verify(chapterRepository).deleteById("chap-free");
    }

    @Test
    void deletionImpact_freeQuestWithScenes_countsThem() {
        Quest quest = Quest.builder().id("q-1").campaignId("camp").name("Libre")
                .nodes(List.of(new QuestNodeRef(NodeType.CHAPTER, "chap-free", 0))).build();
        when(questRepository.findById("q-1")).thenReturn(Optional.of(quest));
        when(questRepository.findByCampaignId("camp")).thenReturn(List.of(quest)); // filtrée par id
        when(chapterRepository.findById("chap-free"))
                .thenReturn(Optional.of(Chapter.builder().id("chap-free").arcId("arc-sys").name("Libre").build()));
        when(arcRepository.findById("arc-sys"))
                .thenReturn(Optional.of(Arc.builder().id("arc-sys").type(ArcType.SYSTEM).build()));
        when(sceneRepository.findByChapterId("chap-free")).thenReturn(List.of(
                Scene.builder().id("s-1").chapterId("chap-free").name("S1").build(),
                Scene.builder().id("s-2").chapterId("chap-free").name("S2").build()));

        assertEquals(2, service.getDeletionImpact("q-1").scenes());
    }

    @Test
    void deletionImpact_unknownQuest_returnsZero() {
        when(questRepository.findById("inconnue")).thenReturn(Optional.empty());

        assertEquals(0, service.getDeletionImpact("inconnue").scenes());
    }

    @Test
    void deletionImpact_systemContainerReferencedByAnotherQuest_reportsZero() {
        // Conteneur PARTAGÉ (autre quête le référence) : il survivra -> impact 0.
        Quest quest = Quest.builder().id("q-1").campaignId("camp").name("Libre")
                .nodes(List.of(new QuestNodeRef(NodeType.CHAPTER, "chap-free", 0))).build();
        Quest other = Quest.builder().id("q-2").campaignId("camp").name("Autre")
                .nodes(List.of(new QuestNodeRef(NodeType.CHAPTER, "chap-free", 0))).build();
        when(questRepository.findById("q-1")).thenReturn(Optional.of(quest));
        when(questRepository.findByCampaignId("camp")).thenReturn(List.of(quest, other));
        when(chapterRepository.findById("chap-free"))
                .thenReturn(Optional.of(Chapter.builder().id("chap-free").arcId("arc-sys").name("Libre").build()));
        when(arcRepository.findById("arc-sys"))
                .thenReturn(Optional.of(Arc.builder().id("arc-sys").type(ArcType.SYSTEM).build()));

        assertEquals(0, service.getDeletionImpact("q-1").scenes());
    }

    @Test
    void deleteQuest_freeQuest_systemContainerReferencedElsewhere_keptWithScenes() {
        // Même partagé en arc SYSTEM, un conteneur encore référencé n'est JAMAIS cascadé.
        Quest quest = Quest.builder().id("q-1").campaignId("camp").name("Libre")
                .nodes(List.of(new QuestNodeRef(NodeType.CHAPTER, "chap-free", 0))).build();
        Quest other = Quest.builder().id("q-2").campaignId("camp").name("Autre")
                .nodes(List.of(new QuestNodeRef(NodeType.CHAPTER, "chap-free", 0))).build();
        when(questRepository.findById("q-1")).thenReturn(Optional.of(quest));
        when(questRepository.findByCampaignId("camp")).thenReturn(List.of(other));
        when(chapterRepository.findById("chap-free"))
                .thenReturn(Optional.of(Chapter.builder().id("chap-free").arcId("arc-sys").name("Libre").build()));
        when(arcRepository.findById("arc-sys"))
                .thenReturn(Optional.of(Arc.builder().id("arc-sys").type(ArcType.SYSTEM).build()));

        service.deleteQuest("q-1");

        verify(chapterRepository, never()).deleteById(anyString());
        verify(sceneRepository, never()).deleteById(anyString());
    }

    @Test
    void deletionImpact_hubQuest_reportsZero() {
        // Jumeau de hub : GARDÉ à la suppression (il reste visible dans l'arc) -> impact 0.
        Quest quest = Quest.builder().id("q-1").campaignId("camp").arcId("arc-h").name("Q")
                .nodes(List.of(new QuestNodeRef(NodeType.CHAPTER, "chap-1", 0))).build();
        when(questRepository.findById("q-1")).thenReturn(Optional.of(quest));
        when(questRepository.findByCampaignId("camp")).thenReturn(List.of(quest));
        when(chapterRepository.findById("chap-1"))
                .thenReturn(Optional.of(Chapter.builder().id("chap-1").arcId("arc-h").name("Q").build()));
        when(arcRepository.findById("arc-h"))
                .thenReturn(Optional.of(Arc.builder().id("arc-h").type(ArcType.HUB).build()));

        assertEquals(0, service.getDeletionImpact("q-1").scenes());
    }

    @Test
    void deleteQuest_keepsContainerReferencedByAnotherQuest() {
        Quest quest = Quest.builder().id("q-1").campaignId("camp").arcId("arc-h").name("Q")
                .nodes(List.of(new QuestNodeRef(NodeType.CHAPTER, "chap-1", 0))).build();
        Quest other = Quest.builder().id("q-2").campaignId("camp").arcId("arc-h").name("Autre")
                .nodes(List.of(new QuestNodeRef(NodeType.CHAPTER, "chap-1", 0))).build();
        when(questRepository.findById("q-1")).thenReturn(Optional.of(quest));
        when(questRepository.findByCampaignId("camp")).thenReturn(List.of(other));
        when(chapterRepository.findById("chap-1"))
                .thenReturn(Optional.of(Chapter.builder().id("chap-1").arcId("arc-h").name("Q").build()));
        // NB : pas de stub sceneRepository — un conteneur encore référencé est écarté avant toute lecture.

        service.deleteQuest("q-1");

        verify(chapterRepository, never()).deleteById(anyString());
    }
}
