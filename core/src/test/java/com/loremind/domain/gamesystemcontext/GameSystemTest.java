package com.loremind.domain.gamesystemcontext;

import com.loremind.domain.shared.template.FieldType;
import com.loremind.domain.shared.template.ImageLayout;
import com.loremind.domain.shared.template.TemplateField;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires du domaine GameSystem ciblant la gestion des templates PJ/PNJ.
 * Le ruleset markdown est testé ailleurs via GameSystemContextSelector.
 */
class GameSystemTest {

    // --- addCharacterField --------------------------------------------------

    @Test
    void addCharacterField_appendsField() {
        GameSystem gs = GameSystem.builder().build();

        gs.addCharacterField(TemplateField.text("Histoire"));
        gs.addCharacterField(TemplateField.image("Portrait", ImageLayout.HERO));

        assertEquals(2, gs.getCharacterTemplate().size());
        assertEquals("Histoire", gs.getCharacterTemplate().get(0).getName());
        assertEquals(FieldType.IMAGE, gs.getCharacterTemplate().get(1).getType());
    }

    @Test
    void addCharacterField_rejectsDuplicateNameCaseInsensitive() {
        GameSystem gs = GameSystem.builder().build();
        gs.addCharacterField(TemplateField.text("Histoire"));

        // Doublon de cle dans Character.values garanti casse-insensible :
        // "Histoire" et "histoire" produiraient la meme cle JSON.
        assertThrows(IllegalArgumentException.class,
                () -> gs.addCharacterField(TemplateField.number("HISTOIRE")));
    }

    @Test
    void addCharacterField_rejectsBlankName() {
        GameSystem gs = GameSystem.builder().build();
        assertThrows(IllegalArgumentException.class,
                () -> gs.addCharacterField(new TemplateField("  ", FieldType.TEXT)));
    }

    // --- removeCharacterField ----------------------------------------------

    @Test
    void removeCharacterField_removesByNameCaseInsensitive() {
        GameSystem gs = GameSystem.builder()
                .characterTemplate(new ArrayList<>(List.of(
                        TemplateField.text("Histoire"),
                        TemplateField.text("Notes")
                )))
                .build();

        gs.removeCharacterField("HISTOIRE");

        assertEquals(1, gs.getCharacterTemplate().size());
        assertEquals("Notes", gs.getCharacterTemplate().get(0).getName());
    }

    @Test
    void removeCharacterField_silentNoOpWhenMissing() {
        GameSystem gs = GameSystem.builder()
                .characterTemplate(new ArrayList<>(List.of(TemplateField.text("Histoire"))))
                .build();

        gs.removeCharacterField("absent");

        assertEquals(1, gs.getCharacterTemplate().size());
    }

    // --- replaceCharacterTemplate ------------------------------------------

    @Test
    void replaceCharacterTemplate_overwritesEntireList() {
        GameSystem gs = GameSystem.builder()
                .characterTemplate(new ArrayList<>(List.of(TemplateField.text("Old"))))
                .build();

        gs.replaceCharacterTemplate(List.of(
                TemplateField.text("A"),
                TemplateField.number("B")));

        assertEquals(2, gs.getCharacterTemplate().size());
        assertEquals("A", gs.getCharacterTemplate().get(0).getName());
        assertEquals("B", gs.getCharacterTemplate().get(1).getName());
    }

    @Test
    void replaceCharacterTemplate_rejectsDuplicates() {
        GameSystem gs = GameSystem.builder().build();
        assertThrows(IllegalArgumentException.class,
                () -> gs.replaceCharacterTemplate(List.of(
                        TemplateField.text("a"),
                        TemplateField.text("A"))));
    }

    @Test
    void replaceCharacterTemplate_nullBecomesEmptyList() {
        GameSystem gs = GameSystem.builder().build();
        gs.replaceCharacterTemplate(null);
        assertTrue(gs.getCharacterTemplate().isEmpty());
    }

    @Test
    void replaceCharacterTemplate_isolatesInternalListFromCallerMutations() {
        // Garantie d'encapsulation : muter la liste passee ne doit pas affecter le GameSystem.
        List<TemplateField> external = new ArrayList<>(List.of(TemplateField.text("A")));
        GameSystem gs = GameSystem.builder().build();

        gs.replaceCharacterTemplate(external);
        external.add(TemplateField.text("B"));

        assertEquals(1, gs.getCharacterTemplate().size());
    }

    // --- Templates NPC : meme logique, sanity check minimal ----------------

    @Test
    void npcTemplate_followsSameRulesAsCharacterTemplate() {
        GameSystem gs = GameSystem.builder().build();

        gs.addNpcField(TemplateField.text("Motivation"));
        assertThrows(IllegalArgumentException.class,
                () -> gs.addNpcField(TemplateField.text("motivation")));

        gs.removeNpcField("Motivation");
        assertTrue(gs.getNpcTemplate().isEmpty());
    }
}
