package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.ports.ArcRepository;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.campaigncontext.ports.ChapterRepository;
import com.loremind.domain.campaigncontext.ports.QuestRepository;
import com.loremind.domain.campaigncontext.ports.SceneRepository;
import com.loremind.domain.campaigncontext.quest.Quest;
import com.loremind.domain.campaigncontext.readiness.ReadinessEntityType;
import com.loremind.domain.campaigncontext.structure.Arc;
import com.loremind.domain.campaigncontext.structure.ArcType;
import com.loremind.domain.campaigncontext.structure.Chapter;
import com.loremind.domain.campaigncontext.structure.Scene;
import com.loremind.domain.campaigncontext.structure.SceneBranch;
import com.loremind.domain.campaigncontext.structure.SceneType;
import com.loremind.domain.campaigncontext.writingreview.WritingRemarkCategory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Relecture d'éditeur (Mentor d'écriture, phase M2 — voir docs/conception-mentor-ecriture.md) :
 * conseils d'auteur sur la QUALITÉ de l'écriture (vrais choix, rythme, enjeux, narration),
 * là où le readiness ({@link CampaignReadinessService}) ne juge que la complétude.
 *
 * <p><b>Déterministe, sans IA, sans persistance</b> ; même chargement en une passe que le
 * readiness (arcs → chapitres → scènes). Purement indicatif : une remarque est un conseil,
 * jamais un blocage. Le texte est composé côté front à partir de {@code ruleId} + {@code params}.</p>
 *
 * <p>L'arc SYSTEM (« Quêtes libres ») est de la plomberie invisible : ses chapitres-conteneurs
 * ne reçoivent pas de remarque de niveau chapitre, mais leurs scènes sont relues comme les autres.</p>
 */
@Service
public class WritingReviewService {

    /** WRITE-001 : à partir de combien de scènes un chapitre sans aucun choix devient un couloir. */
    static final int CORRIDOR_MIN_SCENES = 3;
    /** WRITE-003 : nombre minimal de chemins menant à une révélation (en deçà : un seul chemin). */
    static final int REVELATION_MIN_INCOMING = 2;
    /** WRITE-004 : longueur d'une suite de scènes du même genre jugée monotone. */
    static final int MONOTONOUS_RUN = 3;
    /** WRITE-006 : au-delà, la narration dépasse ~1 min de lecture à voix haute (~900 car./min). */
    static final int LONG_NARRATION_CHARS = 1200;
    /** WRITE-010 : nombre minimal de scènes pour juger la part de scènes non typées. */
    static final int UNTYPED_MIN_SCENES = 5;
    /** WRITE-010 : part de scènes « génériques » au-delà de laquelle on conseille de typer. */
    static final double UNTYPED_RATIO = 0.7;

    /** Genre « combat » pour WRITE-004 (les autres genres = nom du {@link SceneType}). */
    static final String KIND_COMBAT = "COMBAT";

    /**
     * WRITE-007 : la narration décide à la place des personnages (action ou émotion imposée).
     * Volontairement restreint aux tournures sans ambiguïté, pour éviter les faux positifs.
     */
    private static final Pattern NARRATION_DECIDES = Pattern.compile(
            "\\b(vous (?:décidez|choisissez|ressentez|avez (?:très |si )?peur"
                    + "|êtes (?:terrifié|effrayé|terrorisé|paniqué|soulagé|ému)e?s?"
                    + "|entrez|vous précipitez|dégainez|attaquez|fuyez)"
                    + "|you (?:decide|choose|feel (?:afraid|scared|uneasy|relieved|guilty)"
                    + "|are (?:afraid|scared|terrified|relieved|panicking)"
                    + "|enter|rush|draw your|attack|flee))\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);

    private final CampaignRepository campaignRepository;
    private final ArcRepository arcRepository;
    private final ChapterRepository chapterRepository;
    private final SceneRepository sceneRepository;
    private final QuestRepository questRepository;

    public WritingReviewService(CampaignRepository campaignRepository,
                                ArcRepository arcRepository,
                                ChapterRepository chapterRepository,
                                SceneRepository sceneRepository,
                                QuestRepository questRepository) {
        this.campaignRepository = campaignRepository;
        this.arcRepository = arcRepository;
        this.chapterRepository = chapterRepository;
        this.sceneRepository = sceneRepository;
        this.questRepository = questRepository;
    }

    /** Relit toute la campagne et renvoie les remarques dans l'ordre de l'arbre. */
    public WritingReviewReport review(String campaignId) {
        List<WritingRemark> campaignRemarks = new ArrayList<>();
        List<WritingRemark> treeRemarks = new ArrayList<>();

        Set<String> arcsWithQuests = questRepository.findByCampaignId(campaignId).stream()
                .map(Quest::getArcId).filter(id -> !isBlank(id)).collect(Collectors.toSet());

        List<Arc> arcs = new ArrayList<>(arcRepository.findByCampaignId(campaignId));
        arcs.sort(Comparator.comparingInt(Arc::getOrder));

        int totalScenes = 0;
        int genericScenes = 0;
        for (Arc arc : arcs) {
            List<Chapter> chapters = new ArrayList<>(chapterRepository.findByArcId(arc.getId()));
            chapters.sort(Comparator.comparingInt(Chapter::getOrder));
            boolean visibleArc = arc.getType() != ArcType.SYSTEM;

            // WRITE-005 (arc) : seulement sur un arc qui a déjà du contenu.
            if (visibleArc && isBlank(arc.getStakes())
                    && (!chapters.isEmpty() || arcsWithQuests.contains(arc.getId()))) {
                treeRemarks.add(arcRemark(arc, "WRITE-005-NO-STAKES", WritingRemarkCategory.STAKES, Map.of()));
            }

            List<Scene> lastChapterScenes = List.of();
            for (Chapter chapter : chapters) {
                List<Scene> scenes = new ArrayList<>(sceneRepository.findByChapterId(chapter.getId()));
                scenes.sort(Comparator.comparingInt(Scene::getOrder));
                totalScenes += scenes.size();
                genericScenes += (int) scenes.stream().filter(s -> typeOf(s) == SceneType.GENERIC).count();

                if (visibleArc) {
                    reviewChapter(arc, chapter, scenes, treeRemarks);
                }
                for (Scene scene : scenes) {
                    reviewScene(arc, chapter, scene, scenes, treeRemarks);
                }
                if (!scenes.isEmpty()) {
                    lastChapterScenes = scenes;
                }
            }

            if (arc.getType() == ArcType.LINEAR && !lastChapterScenes.isEmpty()) {
                checkSingleEnding(arc, lastChapterScenes, treeRemarks);
            }
        }

        // WRITE-010 — trop de scènes non typées pour juger le rythme.
        if (totalScenes >= UNTYPED_MIN_SCENES && (double) genericScenes / totalScenes > UNTYPED_RATIO) {
            String campaignName = campaignRepository.findById(campaignId).map(Campaign::getName).orElse(null);
            int pct = (int) Math.round(100.0 * genericScenes / totalScenes);
            campaignRemarks.add(new WritingRemark(ReadinessEntityType.CAMPAIGN, campaignId, campaignName,
                    "WRITE-010-UNTYPED-SCENES", WritingRemarkCategory.PACING,
                    Map.of("percent", String.valueOf(pct), "count", String.valueOf(genericScenes)),
                    null, null));
        }

        List<WritingRemark> remarks = new ArrayList<>(campaignRemarks);
        remarks.addAll(treeRemarks);
        return new WritingReviewReport(campaignId, remarks);
    }

    // --- Niveau chapitre ---------------------------------------------------------------

    private void reviewChapter(Arc arc, Chapter chapter, List<Scene> scenes, List<WritingRemark> out) {
        if (scenes.isEmpty()) return; // Chapitre vide : c'est l'affaire du readiness (CHAP-001).

        // WRITE-001 — couloir : plusieurs scènes, aucune sortie ni aucun choix décrit.
        boolean anyBranch = scenes.stream().anyMatch(WritingReviewService::hasBranches);
        boolean anyChoice = scenes.stream().anyMatch(s -> !isBlank(s.getChoicesConsequences()));
        if (scenes.size() >= CORRIDOR_MIN_SCENES && !anyBranch && !anyChoice) {
            out.add(chapterRemark(arc, chapter, "WRITE-001-CORRIDOR", WritingRemarkCategory.AGENCY,
                    Map.of("count", String.valueOf(scenes.size()))));
        }

        // WRITE-004 — rythme monotone : N scènes d'affilée du même genre.
        MonotonousRun run = findMonotonousRun(scenes);
        if (run != null) {
            out.add(chapterRemark(arc, chapter, "WRITE-004-MONOTONOUS-PACING", WritingRemarkCategory.PACING,
                    Map.of("count", String.valueOf(run.length()), "kind", run.kind())));
        }

        // WRITE-005 (chapitre) — aucun enjeu narratif écrit.
        if (isBlank(chapter.getNarrativeStakes())) {
            out.add(chapterRemark(arc, chapter, "WRITE-005-NO-STAKES", WritingRemarkCategory.STAKES, Map.of()));
        }
    }

    private record MonotonousRun(String kind, int length) {}

    /** Première suite d'au moins {@link #MONOTONOUS_RUN} scènes consécutives du même genre, sinon null. */
    private static MonotonousRun findMonotonousRun(List<Scene> scenes) {
        String currentKind = null;
        int length = 0;
        MonotonousRun found = null;
        for (Scene scene : scenes) {
            String kind = pacingKind(scene);
            if (kind != null && kind.equals(currentKind)) {
                length++;
            } else {
                if (found != null) return found;
                currentKind = kind;
                length = kind == null ? 0 : 1;
            }
            if (length >= MONOTONOUS_RUN) {
                found = new MonotonousRun(currentKind, length);
            }
        }
        return found;
    }

    /** Genre d'une scène pour le rythme : COMBAT, sinon son type s'il est renseigné, sinon null. */
    private static String pacingKind(Scene scene) {
        if (isCombat(scene)) return KIND_COMBAT;
        SceneType type = typeOf(scene);
        return type == SceneType.GENERIC ? null : type.name();
    }

    // --- Niveau scène -------------------------------------------------------------------

    private void reviewScene(Arc arc, Chapter chapter, Scene scene, List<Scene> chapterScenes,
                             List<WritingRemark> out) {
        checkFakeChoice(arc, chapter, scene, chapterScenes, out);
        checkSingleClue(arc, chapter, scene, chapterScenes, out);
        checkNarration(arc, chapter, scene, out);
        checkCombatPurpose(arc, chapter, scene, out);
    }

    /** WRITE-002 — faux carrefour : plusieurs sorties qui mènent toutes au même endroit. */
    private void checkFakeChoice(Arc arc, Chapter chapter, Scene scene, List<Scene> chapterScenes,
                                 List<WritingRemark> out) {
        List<SceneBranch> branches = scene.getBranches();
        if (branches == null || branches.size() < 2) return;
        Set<String> targets = branches.stream().map(SceneBranch::targetSceneId).collect(Collectors.toSet());
        if (targets.size() != 1) return;
        String target = targets.iterator().next();
        if (isBlank(target)) return; // Branche cassée : c'est l'affaire du readiness (SCENE-010).
        String targetName = chapterScenes.stream()
                .filter(s -> target.equals(s.getId())).map(Scene::getName)
                .filter(n -> !isBlank(n)).findFirst().orElse("?");
        out.add(sceneRemark(arc, chapter, scene, "WRITE-002-FAKE-CHOICE", WritingRemarkCategory.AGENCY,
                Map.of("count", String.valueOf(branches.size()), "target", targetName)));
    }

    /**
     * WRITE-003 — révélation à chemin unique (règle des 3 indices). Seulement dans un
     * chapitre qui utilise des liens : sans aucun lien, c'est WRITE-001 qui parle.
     */
    private void checkSingleClue(Arc arc, Chapter chapter, Scene scene, List<Scene> chapterScenes,
                                 List<WritingRemark> out) {
        if (typeOf(scene) != SceneType.REVELATION || isBlank(scene.getId())) return;
        if (chapterScenes.stream().noneMatch(WritingReviewService::hasBranches)) return;
        long incoming = chapterScenes.stream()
                .filter(s -> !scene.getId().equals(s.getId()) && s.getBranches() != null)
                .flatMap(s -> s.getBranches().stream().map(SceneBranch::targetSceneId).distinct())
                .filter(scene.getId()::equals)
                .count();
        if (incoming < REVELATION_MIN_INCOMING) {
            out.add(sceneRemark(arc, chapter, scene, "WRITE-003-SINGLE-CLUE", WritingRemarkCategory.AGENCY,
                    Map.of("incoming", String.valueOf(incoming))));
        }
    }

    /** WRITE-006 — narration trop longue ; WRITE-007 — narration qui décide à la place des PJ. */
    private void checkNarration(Arc arc, Chapter chapter, Scene scene, List<WritingRemark> out) {
        String narration = scene.getPlayerNarration();
        if (isBlank(narration)) return;
        int length = narration.strip().length();
        if (length > LONG_NARRATION_CHARS) {
            out.add(sceneRemark(arc, chapter, scene, "WRITE-006-LONG-NARRATION", WritingRemarkCategory.NARRATION,
                    Map.of("chars", String.valueOf(length))));
        }
        Matcher m = NARRATION_DECIDES.matcher(narration);
        if (m.find()) {
            out.add(sceneRemark(arc, chapter, scene, "WRITE-007-NARRATION-DECIDES", WritingRemarkCategory.NARRATION,
                    Map.of("phrase", m.group(1))));
        }
    }

    /** WRITE-008 — combat sans but : ni choix décrits, ni notes sur les motivations des ennemis. */
    private void checkCombatPurpose(Arc arc, Chapter chapter, Scene scene, List<WritingRemark> out) {
        if (isCombat(scene) && isBlank(scene.getChoicesConsequences()) && isBlank(scene.getGmSecretNotes())) {
            out.add(sceneRemark(arc, chapter, scene, "WRITE-008-COMBAT-NO-PURPOSE", WritingRemarkCategory.STAKES,
                    Map.of()));
        }
    }

    // --- Niveau arc -----------------------------------------------------------------------

    /** WRITE-009 — fin unique : la dernière scène de l'arc ne prévoit ni sortie ni alternative. */
    private void checkSingleEnding(Arc arc, List<Scene> lastChapterScenes, List<WritingRemark> out) {
        Scene finale = lastChapterScenes.get(lastChapterScenes.size() - 1);
        if (!hasBranches(finale) && isBlank(finale.getChoicesConsequences())) {
            out.add(new WritingRemark(ReadinessEntityType.SCENE, finale.getId(), labelOr(finale.getName(), "Scène"),
                    "WRITE-009-SINGLE-ENDING", WritingRemarkCategory.AGENCY,
                    Map.of("arc", labelOr(arc.getName(), "Arc")), arc.getId(), finale.getChapterId()));
        }
    }

    // --- Helpers ----------------------------------------------------------------------------

    private static boolean isCombat(Scene scene) {
        return typeOf(scene) == SceneType.ENCOUNTER
                || !isBlank(scene.getCombatDifficulty())
                || !isBlank(scene.getEnemies())
                || (scene.getEnemyIds() != null && scene.getEnemyIds().stream().anyMatch(id -> !isBlank(id)));
    }

    private static boolean hasBranches(Scene scene) {
        return scene.getBranches() != null && !scene.getBranches().isEmpty();
    }

    private static SceneType typeOf(Scene scene) {
        return Objects.requireNonNullElse(scene.getType(), SceneType.GENERIC);
    }

    private static WritingRemark sceneRemark(Arc arc, Chapter chapter, Scene scene, String ruleId,
                                             WritingRemarkCategory category, Map<String, String> params) {
        return new WritingRemark(ReadinessEntityType.SCENE, scene.getId(), labelOr(scene.getName(), "Scène"),
                ruleId, category, params, arc.getId(), chapter.getId());
    }

    private static WritingRemark chapterRemark(Arc arc, Chapter chapter, String ruleId,
                                               WritingRemarkCategory category, Map<String, String> params) {
        return new WritingRemark(ReadinessEntityType.CHAPTER, chapter.getId(), labelOr(chapter.getName(), "Chapitre"),
                ruleId, category, params, arc.getId(), chapter.getId());
    }

    private static WritingRemark arcRemark(Arc arc, String ruleId, WritingRemarkCategory category,
                                           Map<String, String> params) {
        return new WritingRemark(ReadinessEntityType.ARC, arc.getId(), labelOr(arc.getName(), "Arc"),
                ruleId, category, params, arc.getId(), null);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String labelOr(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }
}
