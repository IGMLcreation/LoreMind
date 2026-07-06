package com.loremind.application.generationcontext;

import com.loremind.domain.campaigncontext.structure.Arc;
import com.loremind.domain.campaigncontext.structure.Chapter;
import com.loremind.domain.campaigncontext.structure.Scene;
import com.loremind.domain.campaigncontext.ports.ArcRepository;
import com.loremind.domain.campaigncontext.ports.ChapterRepository;
import com.loremind.domain.campaigncontext.ports.SceneRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * Catalogue des champs ÉTOFFABLES par l'IA (Pilier A), par type d'entité narrative.
 * SOURCE DE VÉRITÉ unique côté génération : ordre + clés + libellés + valeurs actuelles.
 *
 * <p>Les clés correspondent aux setters des services (patch) et aux contrôles de
 * formulaire côté front. Le champ {@code name}/{@code type} n'est jamais étoffable
 * (identité / enum). La liaison clé → setter reste dans chaque service (persistance).</p>
 */
@Component
public class NarrativeFieldCatalog {

    /** Définition d'un champ : clé technique + libellé (guide le prompt du Brain). */
    public record FieldDef(String key, String label) {}

    /** Instantané d'une entité pour l'étoffage : titre + valeurs actuelles + champs. */
    public record Snapshot(String entityType, String title,
                           LinkedHashMap<String, String> current, List<FieldDef> defs) {}

    private static final String FIELD_DESCRIPTION = "description";
    private static final String FIELD_GM_NOTES = "gmNotes";

    private static final List<FieldDef> ARC_DEFS = List.of(
            new FieldDef(FIELD_DESCRIPTION, "description / synopsis de l'arc"),
            new FieldDef("themes", "thèmes explorés"),
            new FieldDef("stakes", "enjeux globaux pour les personnages"),
            new FieldDef("rewards", "récompenses et progression"),
            new FieldDef("resolution", "dénouement prévu"),
            new FieldDef(FIELD_GM_NOTES, "notes privées du MJ"));

    private static final List<FieldDef> CHAPTER_DEFS = List.of(
            new FieldDef(FIELD_DESCRIPTION, "synopsis du chapitre"),
            new FieldDef("playerObjectives", "objectifs des joueurs"),
            new FieldDef("narrativeStakes", "enjeux narratifs dramatiques"),
            new FieldDef(FIELD_GM_NOTES, "notes privées du MJ"));

    private static final List<FieldDef> SCENE_DEFS = List.of(
            new FieldDef(FIELD_DESCRIPTION, "description courte de la scène"),
            new FieldDef("location", "lieu où se déroule la scène"),
            new FieldDef("timing", "moment / temporalité"),
            new FieldDef("atmosphere", "ambiance (sons, odeurs, émotions, lumière)"),
            new FieldDef("playerNarration", "texte de mise en scène lu aux joueurs"),
            new FieldDef("choicesConsequences", "choix offerts aux joueurs et leurs conséquences"),
            new FieldDef("combatDifficulty", "difficulté de combat estimée"),
            new FieldDef("enemies", "ennemis / créatures présentes (texte libre)"),
            new FieldDef("gmSecretNotes", "notes secrètes du MJ (cachées des joueurs)"));

    private final ArcRepository arcRepository;
    private final ChapterRepository chapterRepository;
    private final SceneRepository sceneRepository;

    public NarrativeFieldCatalog(ArcRepository arcRepository,
                                 ChapterRepository chapterRepository,
                                 SceneRepository sceneRepository) {
        this.arcRepository = arcRepository;
        this.chapterRepository = chapterRepository;
        this.sceneRepository = sceneRepository;
    }

    /** Charge l'entité et son instantané d'étoffage. @throws IllegalArgumentException si type/entité inconnu. */
    public Snapshot read(String entityType, String entityId) {
        return switch (normalize(entityType)) {
            case "arc" -> arcSnapshot(entityId);
            case "chapter" -> chapterSnapshot(entityId);
            case "scene" -> sceneSnapshot(entityId);
            default -> throw new IllegalArgumentException("Type d'entité narrative inconnu: " + entityType);
        };
    }

    private Snapshot arcSnapshot(String id) {
        Arc a = arcRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Arc non trouvé: " + id));
        LinkedHashMap<String, String> cur = new LinkedHashMap<>();
        cur.put(FIELD_DESCRIPTION, nz(a.getDescription()));
        cur.put("themes", nz(a.getThemes()));
        cur.put("stakes", nz(a.getStakes()));
        cur.put("rewards", nz(a.getRewards()));
        cur.put("resolution", nz(a.getResolution()));
        cur.put(FIELD_GM_NOTES, nz(a.getGmNotes()));
        return new Snapshot("arc", a.getName(), cur, ARC_DEFS);
    }

    private Snapshot chapterSnapshot(String id) {
        Chapter c = chapterRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Chapitre non trouvé: " + id));
        LinkedHashMap<String, String> cur = new LinkedHashMap<>();
        cur.put(FIELD_DESCRIPTION, nz(c.getDescription()));
        cur.put("playerObjectives", nz(c.getPlayerObjectives()));
        cur.put("narrativeStakes", nz(c.getNarrativeStakes()));
        cur.put(FIELD_GM_NOTES, nz(c.getGmNotes()));
        return new Snapshot("chapter", c.getName(), cur, CHAPTER_DEFS);
    }

    private Snapshot sceneSnapshot(String id) {
        Scene s = sceneRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Scène non trouvée: " + id));
        LinkedHashMap<String, String> cur = new LinkedHashMap<>();
        cur.put(FIELD_DESCRIPTION, nz(s.getDescription()));
        cur.put("location", nz(s.getLocation()));
        cur.put("timing", nz(s.getTiming()));
        cur.put("atmosphere", nz(s.getAtmosphere()));
        cur.put("playerNarration", nz(s.getPlayerNarration()));
        cur.put("choicesConsequences", nz(s.getChoicesConsequences()));
        cur.put("combatDifficulty", nz(s.getCombatDifficulty()));
        cur.put("enemies", nz(s.getEnemies()));
        cur.put("gmSecretNotes", nz(s.getGmSecretNotes()));
        return new Snapshot("scene", s.getName(), cur, SCENE_DEFS);
    }

    private static String normalize(String entityType) {
        return entityType == null ? "" : entityType.trim().toLowerCase();
    }

    private static String nz(String v) {
        return v == null ? "" : v;
    }
}
