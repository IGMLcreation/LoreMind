package com.loremind.application.generationcontext;

import com.loremind.domain.campaigncontext.Arc;
import com.loremind.domain.campaigncontext.ArcType;
import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.Chapter;
import com.loremind.domain.campaigncontext.Character;
import com.loremind.domain.campaigncontext.Npc;
import com.loremind.domain.campaigncontext.Scene;
import com.loremind.domain.campaigncontext.ports.ArcRepository;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.campaigncontext.ports.ChapterRepository;
import com.loremind.domain.campaigncontext.ports.CharacterRepository;
import com.loremind.domain.campaigncontext.ports.EnemyRepository;
import com.loremind.domain.campaigncontext.ports.NpcRepository;
import com.loremind.domain.campaigncontext.ports.SceneRepository;
import com.loremind.domain.generationcontext.CampaignStructuralContext;
import com.loremind.domain.generationcontext.CampaignStructuralContext.ArcSummary;
import com.loremind.domain.generationcontext.CampaignStructuralContext.BranchHint;
import com.loremind.domain.generationcontext.CampaignStructuralContext.ChapterSummary;
import com.loremind.domain.generationcontext.CampaignStructuralContext.CharacterSummary;
import com.loremind.domain.generationcontext.CampaignStructuralContext.NpcSummary;
import com.loremind.domain.generationcontext.CampaignStructuralContext.RoomBranchHint;
import com.loremind.domain.generationcontext.CampaignStructuralContext.RoomSummary;
import com.loremind.domain.generationcontext.CampaignStructuralContext.SceneSummary;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service applicatif qui construit un {@link CampaignStructuralContext}
 * depuis le Campaign Context (projection Campaign → GenerationContext).
 * <p>
 * Traverse l'arbre arcs → chapitres → scènes en respectant l'ordre narratif
 * (tri sur le champ `order` de chaque entité). Charge le NOM + le SYNOPSIS
 * (description courte) de chaque niveau : l'IA sait donc de quoi parle
 * chaque scène/chapitre/arc sans qu'on lui passe les notes MJ ou la
 * narration détaillée — celles-ci restent réservées à l'entité focus via
 * NarrativeEntityContext.
 */
@Component
public class CampaignStructuralContextBuilder {

    private final CampaignRepository campaignRepository;
    private final ArcRepository arcRepository;
    private final ChapterRepository chapterRepository;
    private final SceneRepository sceneRepository;
    private final CharacterRepository characterRepository;
    private final NpcRepository npcRepository;
    private final EnemyRepository enemyRepository;

    public CampaignStructuralContextBuilder(
            CampaignRepository campaignRepository,
            ArcRepository arcRepository,
            ChapterRepository chapterRepository,
            SceneRepository sceneRepository,
            CharacterRepository characterRepository,
            NpcRepository npcRepository,
            EnemyRepository enemyRepository) {
        this.campaignRepository = campaignRepository;
        this.arcRepository = arcRepository;
        this.chapterRepository = chapterRepository;
        this.sceneRepository = sceneRepository;
        this.characterRepository = characterRepository;
        this.npcRepository = npcRepository;
        this.enemyRepository = enemyRepository;
    }

    /** Longueur max du snippet de PJ/PNJ injecté dans le contexte (coût tokens maîtrisé). */
    private static final int CHARACTER_SNIPPET_MAX_LEN = 160;

    /**
     * Construit la carte narrative d'une Campagne. Sans playthroughId, les PJ
     * sont omis (ils sont propres à une Partie).
     */
    public CampaignStructuralContext build(String campaignId) {
        return build(campaignId, null);
    }

    /**
     * Variante avec playthroughId : injecte les PJ de la Partie indiquée.
     * Les PNJ restent campagne-scope (donnée de scénario).
     */
    public CampaignStructuralContext build(String campaignId, String playthroughId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Campagne non trouvée avec l'ID: " + campaignId));

        // Libellés du bestiaire (« Nom (niveau) ») chargés UNE fois pour résoudre
        // les enemyIds des pièces sans N+1 sur le repo.
        Map<String, String> enemyLabelById = enemyRepository.findByCampaignId(campaignId).stream()
                .collect(Collectors.toMap(
                        com.loremind.domain.campaigncontext.Enemy::getId,
                        CampaignStructuralContextBuilder::enemyLabel,
                        (a, b) -> a));

        List<ArcSummary> arcs = arcRepository.findByCampaignId(campaignId).stream()
                .sorted(Comparator.comparingInt(Arc::getOrder))
                .map(arc -> toArcSummary(arc, enemyLabelById))
                .collect(Collectors.toList());

        List<CharacterSummary> characters = (playthroughId == null || playthroughId.isBlank())
                ? List.of()
                : characterRepository.findByPlaythroughId(playthroughId).stream()
                        .sorted(Comparator.comparingInt(Character::getOrder))
                        .map(this::toCharacterSummary)
                        .collect(Collectors.toList());

        List<NpcSummary> npcs = npcRepository.findByCampaignId(campaignId).stream()
                .sorted(Comparator.comparingInt(Npc::getOrder))
                .map(this::toNpcSummary)
                .collect(Collectors.toList());

        return new CampaignStructuralContext(
                campaign.getName(),
                campaign.getDescription(),
                arcs,
                characters,
                npcs);
    }

    /**
     * Projette un PJ vers un résumé court : nom + 1re ligne "signifiante" du
     * markdown (ni vide, ni un titre). Permet à l'IA de savoir "qui est Thorin"
     * sans injecter toute sa fiche.
     */
    private CharacterSummary toCharacterSummary(Character c) {
        return new CharacterSummary(c.getName(), extractSnippet(c.getValues()));
    }

    /** Symétrique à {@link #toCharacterSummary} pour les PNJ. */
    private NpcSummary toNpcSummary(Npc n) {
        return new NpcSummary(n.getName(), extractSnippet(n.getValues()));
    }

    /**
     * Snippet pour le resume IA : 1re ligne signifiante de la 1re valeur non vide
     * du template (refonte 2026-04-30 — remplace l'ancien parsing markdown).
     */
    private static String extractSnippet(java.util.Map<String, String> values) {
        if (values == null || values.isEmpty()) return "";
        for (String value : values.values()) {
            if (value == null || value.isBlank()) continue;
            String firstLine = value.lines()
                    .map(String::strip)
                    .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .findFirst()
                    .orElse("");
            if (firstLine.isEmpty()) continue;
            if (firstLine.length() <= CHARACTER_SNIPPET_MAX_LEN) return firstLine;
            return firstLine.substring(0, CHARACTER_SNIPPET_MAX_LEN - 1).stripTrailing() + "…";
        }
        return "";
    }

    private ArcSummary toArcSummary(Arc arc, Map<String, String> enemyLabelById) {
        List<ChapterSummary> chapters = chapterRepository.findByArcId(arc.getId()).stream()
                .sorted(Comparator.comparingInt(Chapter::getOrder))
                .map(chapter -> toChapterSummary(chapter, enemyLabelById))
                .collect(Collectors.toList());
        return new ArcSummary(
                arc.getName(),
                arc.getDescription(),
                arc.getType() == ArcType.HUB,
                countImages(arc.getIllustrationImageIds()),
                chapters);
    }

    private ChapterSummary toChapterSummary(Chapter chapter, Map<String, String> enemyLabelById) {
        List<Scene> scenes = sceneRepository.findByChapterId(chapter.getId()).stream()
                .sorted(Comparator.comparingInt(Scene::getOrder))
                .toList();

        // Map id -> nom construite en une seule passe pour resoudre les
        // targetSceneId des branches sans re-interroger le repo (evite N+1).
        Map<String, String> nameById = scenes.stream()
                .collect(Collectors.toMap(Scene::getId, Scene::getName));

        List<SceneSummary> summaries = scenes.stream()
                .map(s -> toSceneSummary(s, nameById, enemyLabelById))
                .collect(Collectors.toList());

        return new ChapterSummary(
                chapter.getName(),
                chapter.getDescription(),
                countImages(chapter.getIllustrationImageIds()),
                summaries);
    }

    private SceneSummary toSceneSummary(
            Scene scene, Map<String, String> nameById, Map<String, String> enemyLabelById) {
        List<BranchHint> hints = scene.getBranches() == null
                ? List.of()
                : scene.getBranches().stream()
                    .map(b -> new BranchHint(
                            b.label(),
                            nameById.getOrDefault(b.targetSceneId(), "(scène inconnue)"),
                            b.condition()))
                    .collect(Collectors.toList());

        List<RoomSummary> rooms = toRoomSummaries(scene, enemyLabelById);

        return new SceneSummary(
                scene.getName(),
                scene.getDescription(),
                countImages(scene.getIllustrationImageIds()),
                hints,
                rooms);
    }

    /**
     * Projette les pièces d'une scène en RoomSummary pour le contexte IA.
     * Pas de notes MJ, pas de loot ni pièges : le prompt reste lisible. L'IA
     * connaît la structure du lieu (nom des pièces, ennemis, sorties) — c'est
     * suffisant pour proposer de la narration ou anticiper les choix.
     */
    private List<RoomSummary> toRoomSummaries(Scene scene, Map<String, String> enemyLabelById) {
        if (scene.getRooms() == null || scene.getRooms().isEmpty()) return List.of();
        Map<String, String> nameById = scene.getRooms().stream()
                .collect(Collectors.toMap(
                        com.loremind.domain.campaigncontext.Room::getId,
                        com.loremind.domain.campaigncontext.Room::getName,
                        (a, b) -> a));
        return scene.getRooms().stream()
                .map(r -> {
                    List<RoomBranchHint> hints = r.getBranches() == null
                            ? List.of()
                            : r.getBranches().stream()
                                .map(b -> new RoomBranchHint(
                                        b.label(),
                                        nameById.getOrDefault(b.targetRoomId(), "(pièce inconnue)"),
                                        b.condition()))
                                .collect(Collectors.toList());
                    return new RoomSummary(
                            r.getName(), r.getFloor(), r.getDescription(),
                            roomEnemiesText(r, enemyLabelById), hints);
                })
                .collect(Collectors.toList());
    }

    /**
     * Texte « ennemis » d'une pièce pour le prompt : fiches du bestiaire
     * référencées (libellés résolus, IDs orphelins ignorés) suivies du texte
     * libre. L'un ou l'autre peut être vide.
     */
    private static String roomEnemiesText(
            com.loremind.domain.campaigncontext.Room room, Map<String, String> enemyLabelById) {
        String linked = room.getEnemyIds() == null ? "" : room.getEnemyIds().stream()
                .map(enemyLabelById::get)
                .filter(l -> l != null && !l.isBlank())
                .collect(Collectors.joining(", "));
        String freeText = room.getEnemies() == null ? "" : room.getEnemies().strip();
        if (linked.isEmpty()) return freeText;
        if (freeText.isEmpty()) return linked;
        return linked + " — " + freeText;
    }

    /** Libellé court d'une fiche du bestiaire : « Nom (niveau) » ou « Nom ». */
    private static String enemyLabel(com.loremind.domain.campaigncontext.Enemy enemy) {
        String level = enemy.getLevel() == null ? "" : enemy.getLevel().strip();
        return level.isEmpty() ? enemy.getName() : enemy.getName() + " (" + level + ")";
    }

    /** Helper defensif : compte les illustrations attachees (null-safe). */
    private static int countImages(List<String> ids) {
        return ids == null ? 0 : ids.size();
    }
}
