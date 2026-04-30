package com.loremind.domain.lorecontext;

import com.loremind.domain.shared.template.ImageLayout;
import com.loremind.domain.shared.template.TemplateField;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires du domaine pour Template.
 * Focus sur les deux methodes metier : {@code fieldCount()} et
 * {@code textFieldNames()} — cette derniere est critique car c'est elle qui
 * pilote ce qui est envoye a l'IA pour generation (seuls les champs TEXT).
 */
class TemplateTest {

    // --- fieldCount ---------------------------------------------------------

    @Test
    void fieldCount_returnsZero_whenFieldsIsNull() {
        Template tpl = Template.builder().fields(null).build();
        assertEquals(0, tpl.fieldCount());
    }

    @Test
    void fieldCount_returnsZero_whenFieldsIsEmpty() {
        Template tpl = Template.builder().fields(List.of()).build();
        assertEquals(0, tpl.fieldCount());
    }

    @Test
    void fieldCount_countsAllFieldsRegardlessOfType() {
        Template tpl = Template.builder()
                .fields(List.of(
                        TemplateField.text("histoire"),
                        TemplateField.text("famille"),
                        TemplateField.image("portraits")
                ))
                .build();

        assertEquals(3, tpl.fieldCount());
    }

    // --- textFieldNames : filtrage critique pour la generation IA -----------

    @Test
    void textFieldNames_returnsEmptyList_whenFieldsIsNull() {
        Template tpl = Template.builder().fields(null).build();
        assertTrue(tpl.textFieldNames().isEmpty());
    }

    @Test
    void textFieldNames_excludesImageFields() {
        // L'IA ne doit JAMAIS recevoir les champs IMAGE comme cibles de generation.
        Template tpl = Template.builder()
                .fields(List.of(
                        TemplateField.text("histoire"),
                        TemplateField.image("portraits"),
                        TemplateField.text("motto"),
                        TemplateField.image("cartes", ImageLayout.HERO)
                ))
                .build();

        List<String> names = tpl.textFieldNames();
        assertEquals(2, names.size());
        assertTrue(names.contains("histoire"));
        assertTrue(names.contains("motto"));
        assertTrue(!names.contains("portraits"));
        assertTrue(!names.contains("cartes"));
    }

    @Test
    void textFieldNames_preservesOrder() {
        // L'ordre des champs est significatif dans l'UI et dans le prompt IA.
        Template tpl = Template.builder()
                .fields(List.of(
                        TemplateField.text("zebre"),
                        TemplateField.text("alpha"),
                        TemplateField.text("mousse")
                ))
                .build();

        assertEquals(List.of("zebre", "alpha", "mousse"), tpl.textFieldNames());
    }
}
