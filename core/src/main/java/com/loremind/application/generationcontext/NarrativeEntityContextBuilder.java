package com.loremind.application.generationcontext;

import com.loremind.domain.campaigncontext.Arc;
import com.loremind.domain.campaigncontext.Chapter;
import com.loremind.domain.campaigncontext.Character;
import com.loremind.domain.campaigncontext.Npc;
import com.loremind.domain.campaigncontext.Scene;
import com.loremind.domain.campaigncontext.ports.ArcRepository;
import com.loremind.domain.campaigncontext.ports.ChapterRepository;
import com.loremind.domain.campaigncontext.ports.CharacterRepository;
import com.loremind.domain.campaigncontext.ports.NpcRepository;
import com.loremind.domain.campaigncontext.ports.SceneRepository;
import com.loremind.domain.generationcontext.NarrativeEntityContext;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service applicatif qui construit un {@link NarrativeEntityContext}
 * depuis une entité Arc / Chapter / Scene du Campaign Context.
 * <p>
 * Responsabilité unique : mapper les champs textuels spécifiques de chaque
 * type vers la map uniforme `fields` du VO. Utilise LinkedHashMap pour
 * préserver l'ordre des champs dans le prompt (lisibilité).
 */
@Component
public class NarrativeEntityContextBuilder {

    private final ArcRepository arcRepository;
    private final ChapterRepository chapterRepository;
    private final SceneRepository sceneRepository;
    private final CharacterRepository characterRepository;
    private final NpcRepository npcRepository;

    public NarrativeEntityContextBuilder(
            ArcRepository arcRepository,
            ChapterRepository chapterRepository,
            SceneRepository sceneRepository,
            CharacterRepository characterRepository,
            NpcRepository npcRepository) {
        this.arcRepository = arcRepository;
        this.chapterRepository = chapterRepository;
        this.sceneRepository = sceneRepository;
        this.characterRepository = characterRepository;
        this.npcRepository = npcRepository;
    }

    /**
     * Charge l'entité narrative ciblée et la projette vers un VO du GenerationContext.
     *
     * @param entityType "arc", "chapter", "scene", "character" ou "npc" (insensible à la casse)
     * @param entityId   l'ID de l'entité
     * @throws IllegalArgumentException si le type est inconnu ou l'entité introuvable
     */
    public NarrativeEntityContext build(String entityType, String entityId) {
        String normalized = entityType == null ? "" : entityType.trim().toLowerCase();
        return switch (normalized) {
            case "arc" -> fromArc(loadArc(entityId));
            case "chapter" -> fromChapter(loadChapter(entityId));
            case "scene" -> fromScene(loadScene(entityId));
            case "character" -> fromCharacter(loadCharacter(entityId));
            case "npc" -> fromNpc(loadNpc(entityId));
            default -> throw new IllegalArgumentException("Type d'entité narrative inconnu: " + entityType);
        };
    }

    // --- Chargement ---------------------------------------------------------

    private Arc loadArc(String id) {
        return arcRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Arc non trouvé: " + id));
    }

    private Chapter loadChapter(String id) {
        return chapterRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Chapitre non trouvé: " + id));
    }

    private Scene loadScene(String id) {
        return sceneRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Scène non trouvée: " + id));
    }

    private Character loadCharacter(String id) {
        return characterRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Personnage non trouvé: " + id));
    }

    private Npc loadNpc(String id) {
        return npcRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("PNJ non trouvé: " + id));
    }

    // --- Mapping entité → VO ------------------------------------------------

    private NarrativeEntityContext fromArc(Arc a) {
        Map<String, String> fields = new LinkedHashMap<>();
        putField(fields, "description (synopsis)", a.getDescription());
        putField(fields, "themes",     a.getThemes());
        putField(fields, "stakes",     a.getStakes());
        putField(fields, "rewards",    a.getRewards());
        putField(fields, "resolution", a.getResolution());
        putField(fields, "gmNotes",    a.getGmNotes());
        return new NarrativeEntityContext("arc", a.getName(), fields);
    }

    private NarrativeEntityContext fromChapter(Chapter c) {
        Map<String, String> fields = new LinkedHashMap<>();
        putField(fields, "description (synopsis)", c.getDescription());
        putField(fields, "playerObjectives", c.getPlayerObjectives());
        putField(fields, "narrativeStakes", c.getNarrativeStakes());
        putField(fields, "gmNotes",          c.getGmNotes());
        return new NarrativeEntityContext("chapter", c.getName(), fields);
    }

    private NarrativeEntityContext fromScene(Scene s) {
        Map<String, String> fields = new LinkedHashMap<>();
        putField(fields, "description",          s.getDescription());
        putField(fields, "location",             s.getLocation());
        putField(fields, "timing",               s.getTiming());
        putField(fields, "atmosphere",           s.getAtmosphere());
        putField(fields, "playerNarration",      s.getPlayerNarration());
        putField(fields, "choicesConsequences",  s.getChoicesConsequences());
        putField(fields, "combatDifficulty",     s.getCombatDifficulty());
        putField(fields, "enemies",              s.getEnemies());
        putField(fields, "gmSecretNotes",        s.getGmSecretNotes());
        return new NarrativeEntityContext("scene", s.getName(), fields);
    }

    private NarrativeEntityContext fromCharacter(Character c) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (c.getValues() != null) {
            // Champs templates exposes individuellement — meilleur pour le LLM que
            // l'ancien blob markdown monolithique.
            c.getValues().forEach((k, v) -> putField(fields, k, v));
        }
        return new NarrativeEntityContext("character", c.getName(), fields);
    }

    private NarrativeEntityContext fromNpc(Npc n) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (n.getValues() != null) {
            n.getValues().forEach((k, v) -> putField(fields, k, v));
        }
        return new NarrativeEntityContext("npc", n.getName(), fields);
    }

    /** Null/blank devient chaîne vide — uniforme côté prompt, pas de NPE côté LLM. */
    private static void putField(Map<String, String> target, String key, String value) {
        target.put(key, value == null ? "" : value);
    }
}
