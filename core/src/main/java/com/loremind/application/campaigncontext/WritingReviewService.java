package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.ports.ArcRepository;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.campaigncontext.ports.ChapterRepository;
import com.loremind.domain.campaigncontext.bestiary.Npc;
import com.loremind.domain.campaigncontext.bestiary.NpcStory;
import com.loremind.domain.campaigncontext.ports.NpcRepository;
import com.loremind.domain.campaigncontext.ports.QuestRepository;
import com.loremind.domain.campaigncontext.ports.SceneRepository;
import com.loremind.domain.campaigncontext.quest.Quest;
import com.loremind.domain.campaigncontext.readiness.ReadinessEntityType;
import com.loremind.domain.campaigncontext.structure.Arc;
import com.loremind.domain.campaigncontext.structure.ArcType;
import com.loremind.domain.campaigncontext.structure.Chapter;
import com.loremind.domain.campaigncontext.structure.Room;
import com.loremind.domain.campaigncontext.structure.RoomBranch;
import com.loremind.domain.campaigncontext.structure.Scene;
import com.loremind.domain.campaigncontext.structure.SceneBranch;
import com.loremind.domain.campaigncontext.structure.SceneType;
import com.loremind.domain.campaigncontext.writingreview.WritingRemarkCategory;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
    /** WRITE-003 : nombre minimal de scènes menant à une révélation (règle des 3 indices). */
    static final int REVELATION_MIN_INCOMING = 3;
    /** WRITE-011 / 012 / 014 : un chapitre « qui utilise le graphe » a au moins ce nombre de scènes reliées. */
    static final int LINKED_MIN_SCENES = 2;
    /** WRITE-012 : taille minimale d'un chapitre pour juger un passage obligé. */
    static final int CHOKEPOINT_MIN_SCENES = 4;
    /** WRITE-013 : nombre minimal de pièces pour juger la forme d'un donjon. */
    static final int DUNGEON_MIN_ROOMS = 4;
    /** WRITE-014 : taille minimale d'une enquête pour attendre une scène de relance. */
    static final int PROACTIVE_MIN_SCENES = 3;
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
    private final NpcRepository npcRepository;

    public WritingReviewService(CampaignRepository campaignRepository,
                                ArcRepository arcRepository,
                                ChapterRepository chapterRepository,
                                SceneRepository sceneRepository,
                                QuestRepository questRepository,
                                NpcRepository npcRepository) {
        this.campaignRepository = campaignRepository;
        this.arcRepository = arcRepository;
        this.chapterRepository = chapterRepository;
        this.sceneRepository = sceneRepository;
        this.questRepository = questRepository;
        this.npcRepository = npcRepository;
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

            // WRITE-017 — une seule accroche : les personnages n'ont qu'une raison de s'en mêler.
            if (visibleArc && countLines(arc.getHooks()) == 1) {
                treeRemarks.add(arcRemark(arc, "WRITE-017-SINGLE-HOOK", WritingRemarkCategory.AGENCY, Map.of()));
            }

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
                reviewGraph(arc, chapter, scenes, treeRemarks);
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
        remarks.addAll(reviewNpcs(campaignId));
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

        // WRITE-014 — enquête sans relance : des révélations, mais aucun événement que le MJ
        // peut déclencher si les joueurs tournent en rond.
        boolean investigation = scenes.stream().anyMatch(s -> typeOf(s) == SceneType.REVELATION);
        boolean proactive = scenes.stream().anyMatch(s -> typeOf(s) == SceneType.EVENT);
        if (investigation && !proactive && scenes.size() >= PROACTIVE_MIN_SCENES && usesLinks(scenes)) {
            out.add(chapterRemark(arc, chapter, "WRITE-014-NO-PROACTIVE", WritingRemarkCategory.PACING, Map.of()));
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
        checkLinearDungeon(arc, chapter, scene, out);
    }

    /**
     * WRITE-013 — donjon en couloir : des pièces reliées entre elles, mais sans aucune boucle
     * (graphe des liens = arbre). Les joueurs ne choisissent pas leur route et ne peuvent ni
     * contourner un obstacle ni revenir par un autre chemin. Muet si les pièces ne sont pas reliées.
     */
    private void checkLinearDungeon(Arc arc, Chapter chapter, Scene scene, List<WritingRemark> out) {
        List<Room> rooms = scene.getRooms();
        if (rooms == null || rooms.size() < DUNGEON_MIN_ROOMS) return;
        Set<String> ids = rooms.stream().map(Room::getId).filter(id -> !isBlank(id)).collect(Collectors.toSet());
        // Arêtes non orientées distinctes (A→B et B→A = un seul passage).
        Set<String> edges = new HashSet<>();
        for (Room r : rooms) {
            if (r.getBranches() == null) continue;
            for (RoomBranch b : r.getBranches()) {
                String to = b.targetRoomId();
                if (isBlank(r.getId()) || !ids.contains(to) || r.getId().equals(to)) continue;
                edges.add(r.getId().compareTo(to) < 0 ? r.getId() + "|" + to : to + "|" + r.getId());
            }
        }
        if (edges.isEmpty()) return;
        int components = countComponents(ids, edges);
        int loops = edges.size() - ids.size() + components;
        if (loops == 0 && components == 1) {
            out.add(sceneRemark(arc, chapter, scene, "WRITE-013-LINEAR-DUNGEON", WritingRemarkCategory.AGENCY,
                    Map.of("rooms", String.valueOf(ids.size()))));
        }
    }

    private static int countComponents(Set<String> nodes, Set<String> edges) {
        Map<String, Set<String>> adj = new HashMap<>();
        for (String n : nodes) adj.put(n, new HashSet<>());
        for (String e : edges) {
            String[] ab = e.split("\\|", 2);
            adj.get(ab[0]).add(ab[1]);
            adj.get(ab[1]).add(ab[0]);
        }
        Set<String> seen = new HashSet<>();
        int components = 0;
        for (String start : nodes) {
            if (!seen.add(start)) continue;
            components++;
            Deque<String> stack = new ArrayDeque<>(List.of(start));
            while (!stack.isEmpty()) {
                for (String next : adj.get(stack.pop())) {
                    if (seen.add(next)) stack.push(next);
                }
            }
        }
        return components;
    }

    // --- Graphe du chapitre -------------------------------------------------------------

    /**
     * Règles sur la forme du graphe des scènes d'un chapitre, seulement s'il utilise des liens
     * (sans aucun lien, c'est WRITE-001 qui parle) : WRITE-011 scène isolée, WRITE-012 passage
     * obligé. Les liens sont internes au chapitre (règle de SceneService).
     */
    private void reviewGraph(Arc arc, Chapter chapter, List<Scene> scenes, List<WritingRemark> out) {
        if (!usesLinks(scenes)) return;
        Map<String, Set<String>> next = successors(scenes);
        Set<String> withIncoming = next.values().stream().flatMap(Set::stream).collect(Collectors.toSet());

        // WRITE-011 — scène isolée : ni lien entrant ni lien sortant (hors scène d'ouverture).
        for (int i = 1; i < scenes.size(); i++) {
            Scene s = scenes.get(i);
            if (next.get(s.getId()).isEmpty() && !withIncoming.contains(s.getId())) {
                out.add(sceneRemark(arc, chapter, s, "WRITE-011-ISOLATED-SCENE", WritingRemarkCategory.PACING, Map.of()));
            }
        }

        checkChokepoint(arc, chapter, scenes, next, out);
    }

    /**
     * WRITE-012 — passage obligé : depuis la scène d'ouverture, tous les chemins vers les
     * fins du chapitre traversent une même scène. Si les joueurs y échouent ou passent à côté,
     * tout s'arrête. Une seule remarque par chapitre (le premier passage obligé).
     */
    private void checkChokepoint(Arc arc, Chapter chapter, List<Scene> scenes, Map<String, Set<String>> next,
                                 List<WritingRemark> out) {
        if (scenes.size() < CHOKEPOINT_MIN_SCENES) return;
        String entry = scenes.get(0).getId();
        if (next.get(entry).isEmpty()) return;
        Set<String> reachable = reach(entry, next, null);
        Set<String> ends = reachable.stream().filter(id -> next.get(id).isEmpty()).collect(Collectors.toSet());
        if (ends.isEmpty()) return; // Boucle sans fin : rien à juger.

        List<Scene> chokepoints = new ArrayList<>();
        for (Scene s : scenes) {
            String id = s.getId();
            if (id.equals(entry) || ends.contains(id) || !reachable.contains(id)) continue;
            Set<String> without = reach(entry, next, id);
            if (ends.stream().noneMatch(without::contains)) chokepoints.add(s);
        }
        if (chokepoints.isEmpty()) return;
        out.add(sceneRemark(arc, chapter, chokepoints.get(0), "WRITE-012-CHOKEPOINT", WritingRemarkCategory.AGENCY,
                Map.of("count", String.valueOf(chokepoints.size()))));
    }

    /** Successeurs de chaque scène du chapitre (liens vers des scènes du même chapitre seulement). */
    private static Map<String, Set<String>> successors(List<Scene> scenes) {
        Set<String> ids = scenes.stream().map(Scene::getId).collect(Collectors.toSet());
        Map<String, Set<String>> next = new HashMap<>();
        for (Scene s : scenes) {
            Set<String> targets = new LinkedHashSet<>();
            if (s.getBranches() != null) {
                for (SceneBranch b : s.getBranches()) {
                    String to = b.targetSceneId();
                    if (ids.contains(to) && !to.equals(s.getId())) targets.add(to);
                }
            }
            next.put(s.getId(), targets);
        }
        return next;
    }

    /** Scènes atteignables depuis {@code from} en suivant les liens, sans passer par {@code removed}. */
    private static Set<String> reach(String from, Map<String, Set<String>> next, String removed) {
        Set<String> seen = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>(List.of(from));
        seen.add(from);
        while (!stack.isEmpty()) {
            for (String n : next.getOrDefault(stack.pop(), Set.of())) {
                if (!n.equals(removed) && seen.add(n)) stack.push(n);
            }
        }
        return seen;
    }

    /** Le chapitre utilise le graphe : au moins {@link #LINKED_MIN_SCENES} scènes portent des liens. */
    private static boolean usesLinks(List<Scene> scenes) {
        return scenes.stream().filter(WritingReviewService::hasBranches).count() >= LINKED_MIN_SCENES;
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
        List<Scene> sources = chapterScenes.stream()
                .filter(s -> !scene.getId().equals(s.getId()) && s.getBranches() != null)
                .filter(s -> s.getBranches().stream().anyMatch(b -> scene.getId().equals(b.targetSceneId())))
                .toList();
        if (sources.size() < REVELATION_MIN_INCOMING) {
            out.add(sceneRemark(arc, chapter, scene, "WRITE-003-SINGLE-CLUE", WritingRemarkCategory.AGENCY,
                    Map.of("incoming", String.valueOf(sources.size()))));
            return;
        }
        // WRITE-015 — assez de chemins, mais tous du même genre (que des interrogatoires…) :
        // un groupe qui n'aime pas ce genre de scène passe à côté de tous les indices.
        Set<SceneType> kinds = sources.stream().map(WritingReviewService::typeOf).collect(Collectors.toSet());
        if (kinds.size() == 1 && !kinds.contains(SceneType.GENERIC)) {
            out.add(sceneRemark(arc, chapter, scene, "WRITE-015-SAME-SOURCE-CLUES", WritingRemarkCategory.AGENCY,
                    Map.of("kind", kinds.iterator().next().name(), "count", String.valueOf(sources.size()))));
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
        if (isCombat(scene) && isBlank(scene.getChoicesConsequences()) && isBlank(scene.getGmSecretNotes())
                && isBlank(scene.getTactics())) {
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

    // --- PNJ ----------------------------------------------------------------------------------

    /**
     * WRITE-016 — opposant ou allié sans motivation : un PNJ qui pèse sur l'intrigue doit
     * vouloir quelque chose, sinon le MJ ne sait pas comment le faire réagir. Les figurants
     * et les PNJ sans rôle précisé ne sont pas jugés (pas de bruit sur les seconds rôles).
     */
    private List<WritingRemark> reviewNpcs(String campaignId) {
        List<WritingRemark> out = new ArrayList<>();
        for (Npc npc : npcRepository.findByCampaignId(campaignId)) {
            NpcStory story = npc.getStory();
            if (story != null && story.drivesThePlot() && story.normalized().motivation() == null) {
                out.add(new WritingRemark(ReadinessEntityType.NPC, npc.getId(), labelOr(npc.getName(), "PNJ"),
                        "WRITE-016-NPC-NO-MOTIVATION", WritingRemarkCategory.STAKES,
                        Map.of("role", story.normalized().role()), null, null));
            }
        }
        return out;
    }

    // --- Helpers ----------------------------------------------------------------------------

    /** Nombre de lignes non vides (une accroche par ligne, puces « - » comprises). */
    private static int countLines(String text) {
        if (isBlank(text)) return 0;
        return (int) text.lines().filter(l -> !l.isBlank()).count();
    }

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
