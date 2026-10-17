package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.ports.ArcRepository;
import com.loremind.domain.campaigncontext.ports.ChapterRepository;
import com.loremind.domain.campaigncontext.ports.SceneRepository;
import com.loremind.domain.campaigncontext.structure.Arc;
import com.loremind.domain.campaigncontext.structure.ArcType;
import com.loremind.domain.campaigncontext.structure.Chapter;
import com.loremind.domain.campaigncontext.structure.LinkType;
import com.loremind.domain.campaigncontext.structure.Scene;
import com.loremind.domain.campaigncontext.structure.SceneBranch;
import com.loremind.domain.campaigncontext.structure.SceneType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Persiste d'un bloc le squelette d'une méthode guidée (Mentor d'écriture, phase M3) :
 * arc (existant ou nouveau) → chapitre → scènes typées, éventuellement reliées en chaîne.
 *
 * <p>Ne fait qu'ajouter : rien d'existant n'est modifié, à part l'ordre naturel (le nouvel
 * arc et le nouveau chapitre se placent à la suite). Transactionnel : tout ou rien.</p>
 */
@Service
public class GuidedSkeletonService {

    private final ArcRepository arcRepository;
    private final ChapterRepository chapterRepository;
    private final SceneRepository sceneRepository;
    private final ArcService arcService;
    private final ChapterService chapterService;
    private final SceneService sceneService;

    public GuidedSkeletonService(ArcRepository arcRepository,
                                 ChapterRepository chapterRepository,
                                 SceneRepository sceneRepository,
                                 ArcService arcService,
                                 ChapterService chapterService,
                                 SceneService sceneService) {
        this.arcRepository = arcRepository;
        this.chapterRepository = chapterRepository;
        this.sceneRepository = sceneRepository;
        this.arcService = arcService;
        this.chapterService = chapterService;
        this.sceneService = sceneService;
    }

    /**
     * @throws IllegalArgumentException squelette invalide (placement ambigu, chapitre ou scènes sans nom,
     *                                  arc d'une autre campagne ou non linéaire)
     */
    @Transactional
    public GuidedSkeleton.Result create(String campaignId, GuidedSkeleton skeleton) {
        validate(skeleton);
        List<GuidedSkeleton.SceneSpec> specs = skeleton.scenes().stream()
                .filter(s -> s != null && !isBlank(s.name()))
                .toList();

        String arcId = resolveArc(campaignId, skeleton);
        Chapter chapter = createChapter(arcId, skeleton.chapter());

        List<Scene> scenes = new ArrayList<>();
        int order = 0;
        for (GuidedSkeleton.SceneSpec spec : specs) {
            scenes.add(sceneService.createScene(Scene.builder()
                    .name(spec.name().trim())
                    .description(blankToNull(spec.description()))
                    .type(Objects.requireNonNullElse(spec.type(), SceneType.GENERIC))
                    .location(blankToNull(spec.location()))
                    .choicesConsequences(blankToNull(spec.choicesConsequences()))
                    .gmSecretNotes(blankToNull(spec.gmSecretNotes()))
                    .enemies(blankToNull(spec.enemies()))
                    .chapterId(chapter.getId())
                    .order(order++)
                    .build()));
        }

        if (skeleton.linkSequentially()) {
            linkInChain(scenes, isBlank(skeleton.linkLabel()) ? "→" : skeleton.linkLabel().trim());
        }

        return new GuidedSkeleton.Result(arcId, chapter.getId(), scenes.stream().map(Scene::getId).toList());
    }

    private static void validate(GuidedSkeleton skeleton) {
        if (skeleton == null) {
            throw new IllegalArgumentException("Squelette manquant.");
        }
        boolean hasArc = !isBlank(skeleton.arcId());
        boolean hasNewArc = !isBlank(skeleton.newArcName());
        if (hasArc == hasNewArc) {
            throw new IllegalArgumentException("Indiquer soit un arc existant, soit le nom d'un nouvel arc.");
        }
        if (skeleton.chapter() == null || isBlank(skeleton.chapter().name())) {
            throw new IllegalArgumentException("Le chapitre doit avoir un nom.");
        }
        if (skeleton.scenes() == null || skeleton.scenes().stream().noneMatch(s -> s != null && !isBlank(s.name()))) {
            throw new IllegalArgumentException("Il faut au moins une scène nommée.");
        }
    }

    /** Arc existant (vérifié : même campagne, linéaire) ou nouvel arc linéaire placé à la suite. */
    private String resolveArc(String campaignId, GuidedSkeleton skeleton) {
        List<Arc> arcs = arcRepository.findByCampaignId(campaignId);
        if (!isBlank(skeleton.arcId())) {
            Arc arc = arcs.stream().filter(a -> skeleton.arcId().equals(a.getId())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Arc introuvable dans cette campagne."));
            if (arc.getType() != ArcType.LINEAR) {
                throw new IllegalArgumentException("Une méthode guidée s'ajoute à un arc linéaire.");
            }
            return arc.getId();
        }
        // L'arc SYSTEM (« Quêtes libres », ordre 9999) reste en dernier : on ne le compte pas.
        int nextOrder = arcs.stream().filter(a -> a.getType() != ArcType.SYSTEM)
                .mapToInt(Arc::getOrder).max().orElse(-1) + 1;
        return arcService.createArc(Arc.builder()
                .name(skeleton.newArcName().trim())
                .campaignId(campaignId)
                .type(ArcType.LINEAR)
                .order(nextOrder)
                .build()).getId();
    }

    private Chapter createChapter(String arcId, GuidedSkeleton.ChapterSpec spec) {
        int nextOrder = chapterRepository.findByArcId(arcId).stream()
                .mapToInt(Chapter::getOrder).max().orElse(-1) + 1;
        return chapterService.createChapter(Chapter.builder()
                .name(spec.name().trim())
                .description(blankToNull(spec.description()))
                .playerObjectives(blankToNull(spec.playerObjectives()))
                .narrativeStakes(blankToNull(spec.narrativeStakes()))
                .arcId(arcId)
                .order(nextOrder)
                .build());
    }

    /** Chaque scène reçoit une sortie vers la suivante (la dernière n'en a pas). */
    private void linkInChain(List<Scene> scenes, String label) {
        for (int i = 0; i < scenes.size() - 1; i++) {
            Scene scene = scenes.get(i);
            scene.setBranches(new ArrayList<>(List.of(
                    new SceneBranch(label, scenes.get(i + 1).getId(), null, LinkType.EXIT))));
            sceneRepository.save(scene);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String blankToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }
}
