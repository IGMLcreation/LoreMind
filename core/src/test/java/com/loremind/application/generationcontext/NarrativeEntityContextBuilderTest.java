package com.loremind.application.generationcontext;

import com.loremind.domain.campaigncontext.Arc;
import com.loremind.domain.campaigncontext.Chapter;
import com.loremind.domain.campaigncontext.Scene;
import com.loremind.domain.campaigncontext.ports.ArcRepository;
import com.loremind.domain.campaigncontext.ports.ChapterRepository;
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

        assertEquals("arc", ctx.getEntityType());
        assertEquals("L'arc sombre", ctx.getTitle());
        assertEquals("synopsis", ctx.getFields().get("description (synopsis)"));
        assertEquals("trahison", ctx.getFields().get("themes"));
        assertEquals("vie ou mort", ctx.getFields().get("stakes"));
        assertEquals("pouvoir", ctx.getFields().get("rewards"));
        assertEquals("le roi meurt", ctx.getFields().get("resolution"));
        assertEquals("secret", ctx.getFields().get("gmNotes"));
    }

    @Test
    void testBuild_Chapter_WithNullFieldsReplacedByEmptyString() {
        Chapter ch = Chapter.builder()
                .id("ch-1").name("Chapitre 1").description(null)
                .playerObjectives(null).narrativeStakes("haut").gmNotes(null)
                .build();
        when(chapterRepository.findById("ch-1")).thenReturn(Optional.of(ch));

        NarrativeEntityContext ctx = builder.build("chapter", "ch-1");

        assertEquals("chapter", ctx.getEntityType());
        assertEquals("Chapitre 1", ctx.getTitle());
        assertEquals("", ctx.getFields().get("description (synopsis)"));
        assertEquals("", ctx.getFields().get("playerObjectives"));
        assertEquals("haut", ctx.getFields().get("narrativeStakes"));
        assertEquals("", ctx.getFields().get("gmNotes"));
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

        assertEquals("scene", ctx.getEntityType());
        assertEquals("L'auberge", ctx.getTitle());
        assertEquals("lieu calme", ctx.getFields().get("description"));
        assertEquals("Taverne", ctx.getFields().get("location"));
        assertEquals("Soir", ctx.getFields().get("timing"));
        assertEquals("tendue", ctx.getFields().get("atmosphere"));
        assertEquals("Vous entrez...", ctx.getFields().get("playerNarration"));
        assertEquals("option A...", ctx.getFields().get("choicesConsequences"));
        assertEquals("moyen", ctx.getFields().get("combatDifficulty"));
        assertEquals("3 bandits", ctx.getFields().get("enemies"));
        assertEquals("trésor caché", ctx.getFields().get("gmSecretNotes"));
    }

    @Test
    void testBuild_NormalizesTypeCaseAndWhitespace() {
        Arc arc = Arc.builder().id("arc-1").name("A").build();
        when(arcRepository.findById("arc-1")).thenReturn(Optional.of(arc));

        NarrativeEntityContext ctx = builder.build("  ARC  ", "arc-1");
        assertEquals("arc", ctx.getEntityType());
    }

    @Test
    void testBuild_UnknownTypeThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> builder.build("npc", "id"));
        assertTrue(ex.getMessage().contains("npc"));
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
