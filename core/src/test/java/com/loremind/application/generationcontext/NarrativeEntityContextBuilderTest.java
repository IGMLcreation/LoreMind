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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test unitaire pour NarrativeEntityContextBuilder.
 * Vérifie la projection Arc/Chapter/Scene → NarrativeEntityContext pour
 * les 3 types, la normalisation du type (casse/whitespace), la gestion des
 * champs null (remplacés par ""), et les erreurs (type inconnu, entité absente).
 */
@ExtendWith(MockitoExtension.class)
public class NarrativeEntityContextBuilderTest {

    @Mock private ArcRepository arcRepository;
    @Mock private ChapterRepository chapterRepository;
    @Mock private SceneRepository sceneRepository;
    @Mock private CharacterRepository characterRepository;
    @Mock private NpcRepository npcRepository;

    @InjectMocks private NarrativeEntityContextBuilder builder;

    @Test
    void testBuild_Arc() {
        Arc arc = Arc.builder()
                .id("arc-1").name("L'arc sombre").description("synopsis")
                .themes("trahison").stakes("vie ou mort").rewards("pouvoir")
                .resolution("le roi meurt").gmNotes("secret")
                .build();
        when(arcRepository.findById("arc-1")).thenReturn(Optional.of(arc));

        NarrativeEntityContext ctx = builder.build("arc", "arc-1");

        assertEquals("arc", ctx.entityType());
        assertEquals("L'arc sombre", ctx.title());
        assertEquals("synopsis", ctx.fields().get("description (synopsis)"));
        assertEquals("trahison", ctx.fields().get("themes"));
        assertEquals("vie ou mort", ctx.fields().get("stakes"));
        assertEquals("pouvoir", ctx.fields().get("rewards"));
        assertEquals("le roi meurt", ctx.fields().get("resolution"));
        assertEquals("secret", ctx.fields().get("gmNotes"));
    }

    @Test
    void testBuild_Chapter_WithNullFieldsReplacedByEmptyString() {
        Chapter ch = Chapter.builder()
                .id("ch-1").name("Chapitre 1").description(null)
                .playerObjectives(null).narrativeStakes("haut").gmNotes(null)
                .build();
        when(chapterRepository.findById("ch-1")).thenReturn(Optional.of(ch));

        NarrativeEntityContext ctx = builder.build("chapter", "ch-1");

        assertEquals("chapter", ctx.entityType());
        assertEquals("Chapitre 1", ctx.title());
        assertEquals("", ctx.fields().get("description (synopsis)"));
        assertEquals("", ctx.fields().get("playerObjectives"));
        assertEquals("haut", ctx.fields().get("narrativeStakes"));
        assertEquals("", ctx.fields().get("gmNotes"));
    }

    @Test
    void testBuild_Scene_AllFieldsMapped() {
        Scene sc = Scene.builder()
                .id("s-1").name("L'auberge").description("lieu calme")
                .location("Taverne").timing("Soir").atmosphere("tendue")
                .playerNarration("Vous entrez...").choicesConsequences("option A...")
                .combatDifficulty("moyen").enemies("3 bandits")
                .gmSecretNotes("trésor caché")
                .build();
        when(sceneRepository.findById("s-1")).thenReturn(Optional.of(sc));

        NarrativeEntityContext ctx = builder.build("scene", "s-1");

        assertEquals("scene", ctx.entityType());
        assertEquals("L'auberge", ctx.title());
        assertEquals("lieu calme", ctx.fields().get("description"));
        assertEquals("Taverne", ctx.fields().get("location"));
        assertEquals("Soir", ctx.fields().get("timing"));
        assertEquals("tendue", ctx.fields().get("atmosphere"));
        assertEquals("Vous entrez...", ctx.fields().get("playerNarration"));
        assertEquals("option A...", ctx.fields().get("choicesConsequences"));
        assertEquals("moyen", ctx.fields().get("combatDifficulty"));
        assertEquals("3 bandits", ctx.fields().get("enemies"));
        assertEquals("trésor caché", ctx.fields().get("gmSecretNotes"));
    }

    @Test
    void testBuild_NormalizesTypeCaseAndWhitespace() {
        Arc arc = Arc.builder().id("arc-1").name("A").build();
        when(arcRepository.findById("arc-1")).thenReturn(Optional.of(arc));

        NarrativeEntityContext ctx = builder.build("  ARC  ", "arc-1");
        assertEquals("arc", ctx.entityType());
    }

    @Test
    void testBuild_Character_MarkdownProjected() {
        Character c = Character.builder()
                .id("c-1").name("Aragorn").markdownContent("# Aragorn\nRôdeur")
                .build();
        when(characterRepository.findById("c-1")).thenReturn(Optional.of(c));

        NarrativeEntityContext ctx = builder.build("character", "c-1");

        assertEquals("character", ctx.entityType());
        assertEquals("Aragorn", ctx.title());
        assertEquals("# Aragorn\nRôdeur", ctx.fields().get("fiche complète (markdown)"));
    }

    @Test
    void testBuild_Npc_MarkdownProjected() {
        Npc n = Npc.builder()
                .id("n-1").name("Borin le forgeron")
                .markdownContent("# Borin\n**Faction :** Clan Feuillefer")
                .build();
        when(npcRepository.findById("n-1")).thenReturn(Optional.of(n));

        NarrativeEntityContext ctx = builder.build("npc", "n-1");

        assertEquals("npc", ctx.entityType());
        assertEquals("Borin le forgeron", ctx.title());
        assertEquals("# Borin\n**Faction :** Clan Feuillefer",
                ctx.fields().get("fiche complète (markdown)"));
    }

    @Test
    void testBuild_Npc_NormalizesCase() {
        Npc n = Npc.builder().id("n-1").name("Elara").markdownContent("desc").build();
        when(npcRepository.findById("n-1")).thenReturn(Optional.of(n));

        NarrativeEntityContext ctx = builder.build("  NPC  ", "n-1");
        assertEquals("npc", ctx.entityType());
    }

    @Test
    void testBuild_NpcNotFoundThrows() {
        when(npcRepository.findById("missing")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> builder.build("npc", "missing"));
        assertTrue(ex.getMessage().contains("missing"));
    }

    @Test
    void testBuild_UnknownTypeThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> builder.build("alien", "id"));
        assertTrue(ex.getMessage().contains("alien"));
    }

    @Test
    void testBuild_NullTypeThrows() {
        assertThrows(IllegalArgumentException.class, () -> builder.build(null, "id"));
    }

    @Test
    void testBuild_EntityNotFound() {
        when(sceneRepository.findById("missing")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> builder.build("scene", "missing"));
        assertTrue(ex.getMessage().contains("missing"));
    }
}
