package com.loremind.application.generationcontext;

import com.loremind.domain.campaigncontext.Arc;
import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.Chapter;
import com.loremind.domain.campaigncontext.Character;
import com.loremind.domain.campaigncontext.Scene;
import com.loremind.domain.campaigncontext.ports.ArcRepository;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.campaigncontext.ports.ChapterRepository;
import com.loremind.domain.campaigncontext.ports.CharacterRepository;
import com.loremind.domain.campaigncontext.ports.SceneRepository;
import com.loremind.domain.generationcontext.CampaignStructuralContext;
import com.loremind.domain.generationcontext.CampaignStructuralContext.ArcSummary;
import com.loremind.domain.generationcontext.CampaignStructuralContext.BranchHint;
import com.loremind.domain.generationcontext.CampaignStructuralContext.ChapterSummary;
import com.loremind.domain.generationcontext.CampaignStructuralContext.CharacterSummary;
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

    public CampaignStructuralContextBuilder(
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

    /** Longueur max du snippet de PJ injecté dans le contexte (coût tokens maîtrisé). */
    private static final int CHARACTER_SNIPPET_MAX_LEN = 160;

    /**
     * Construit la carte narrative d'une Campagne (arcs → chapitres → scènes,
     * nom + description courte à chaque niveau).
     * @throws IllegalArgumentException si la Campagne est introuvable
     */
    public CampaignStructuralContext build(String campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Campagne non trouvée avec l'ID: " + campaignId));

        List<ArcSummary> arcs = arcRepository.findByCampaignId(campaignId).stream()
                .sorted(Comparator.comparingInt(Arc::getOrder))
                .map(this::toArcSummary)
                .collect(Collectors.toList());

        List<CharacterSummary> characters = characterRepository.findByCampaignId(campaignId).stream()
                .sorted(Comparator.comparingInt(Character::getOrder))
                .map(this::toCharacterSummary)
                .collect(Collectors.toList());

        return CampaignStructuralContext.builder()
                .campaignName(campaign.getName())
                .campaignDescription(campaign.getDescription())
                .arcs(arcs)
                .characters(characters)
                .build();
    }

    /**
     * Projette un PJ vers un résumé court : nom + 1re ligne "signifiante" du
     * markdown (ni vide, ni un titre). Permet à l'IA de savoir "qui est Thorin"
     * sans injecter toute sa fiche.
     */
    private CharacterSummary toCharacterSummary(Character c) {
        return CharacterSummary.builder()
                .name(c.getName())
                .snippet(extractSnippet(c.getMarkdownContent()))
                .build();
    }

    private static String extractSnippet(String markdown) {
        if (markdown == null || markdown.isBlank()) return "";
        String firstLine = markdown.lines()
                .map(String::strip)
                .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                .findFirst()
                .orElse("");
        if (firstLine.length() <= CHARACTER_SNIPPET_MAX_LEN) return firstLine;
        return firstLine.substring(0, CHARACTER_SNIPPET_MAX_LEN - 1).stripTrailing() + "…";
    }

    private ArcSummary toArcSummary(Arc arc) {
        List<ChapterSummary> chapters = chapterRepository.findByArcId(arc.getId()).stream()
                .sorted(Comparator.comparingInt(Chapter::getOrder))
                .map(this::toChapterSummary)
                .collect(Collectors.toList());
        return ArcSummary.builder()
                .name(arc.getName())
                .description(arc.getDescription())
                .illustrationCount(countImages(arc.getIllustrationImageIds()))
                .chapters(chapters)
                .build();
    }

    private ChapterSummary toChapterSummary(Chapter chapter) {
        List<Scene> scenes = sceneRepository.findByChapterId(chapter.getId()).stream()
                .sorted(Comparator.comparingInt(Scene::getOrder))
                .toList();

        // Map id -> nom construite en une seule passe pour resoudre les
        // targetSceneId des branches sans re-interroger le repo (evite N+1).
        Map<String, String> nameById = scenes.stream()
                .collect(Collectors.toMap(Scene::getId, Scene::getName));

        List<SceneSummary> summaries = scenes.stream()
                .map(s -> toSceneSummary(s, nameById))
                .collect(Collectors.toList());

        return ChapterSummary.builder()
                .name(chapter.getName())
                .description(chapter.getDescription())
                .illustrationCount(countImages(chapter.getIllustrationImageIds()))
                .scenes(summaries)
                .build();
    }

    private SceneSummary toSceneSummary(Scene scene, Map<String, String> nameById) {
        List<BranchHint> hints = scene.getBranches() == null
                ? List.of()
                : scene.getBranches().stream()
                    .map(b -> BranchHint.builder()
                            .label(b.getLabel())
                            .targetSceneName(nameById.getOrDefault(
                                    b.getTargetSceneId(), "(scène inconnue)"))
                            .condition(b.getCondition())
                            .build())
                    .collect(Collectors.toList());

        return SceneSummary.builder()
                .name(scene.getName())
                .description(scene.getDescription())
                .illustrationCount(countImages(scene.getIllustrationImageIds()))
                .branches(hints)
                .build();
    }

    /** Helper defensif : compte les illustrations attachees (null-safe). */
    private static int countImages(List<String> ids) {
        return ids == null ? 0 : ids.size();
    }
}
