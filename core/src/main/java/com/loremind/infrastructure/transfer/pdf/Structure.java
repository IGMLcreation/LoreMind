package com.loremind.infrastructure.transfer.pdf;

import com.loremind.infrastructure.persistence.entity.*;

import java.util.*;

/**
 * Structure narrative de la campagne chargee UNE fois : arcs/chapitres/scenes/quetes
 * tries, index de noms, et liens quete -&gt; chapitre-conteneur (fusion de l'arbre).
 * Peuplee par {@link PdfStructureLoader}, lue par PdfExportService lors du rendu.
 */
final class Structure {
    List<ArcJpaEntity> arcs = List.of();
    /** Arcs affiches en narration (l'arc technique SYSTEM est de la plomberie). */
    List<ArcJpaEntity> visibleArcs = List.of();
    final Map<Long, List<ChapterJpaEntity>> chaptersByArc = new LinkedHashMap<>();
    final Map<Long, List<SceneJpaEntity>> scenesByChapter = new LinkedHashMap<>();
    List<QuestJpaEntity> quests = List.of();
    /** Chapitre-conteneur -&gt; quete fusionnee dessus (jumeau hub ou conteneur SYSTEM). */
    final Map<Long, QuestJpaEntity> questByContainerChapter = new LinkedHashMap<>();
    /** Quetes SANS conteneur dans la narration visible : rendues dans la partie « Quetes ». */
    final List<QuestJpaEntity> standaloneQuests = new ArrayList<>();
    final Map<String, String> chapterNames = new HashMap<>();
    final Map<String, String> sceneNames = new HashMap<>();
    final Map<String, String> questNames = new HashMap<>();
    /** Noms des fiches du bestiaire — resout les {@code enemyIds} des scenes/pieces. */
    final Map<String, String> enemyNames = new HashMap<>();

    /** Ids (String) des chapitres-conteneurs de cette quete. */
    Set<String> containerChapterIds(QuestJpaEntity q) {
        Set<String> out = new HashSet<>();
        for (Map.Entry<Long, QuestJpaEntity> e : questByContainerChapter.entrySet()) {
            if (Objects.equals(e.getValue().getId(), q.getId())) out.add(String.valueOf(e.getKey()));
        }
        return out;
    }
}
