package com.loremind.domain.shared.template;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests unitaires du domaine pour TemplateField.
 * Valide les fabriques statiques (text/image/image-with-layout) et le
 * constructeur de retrocompat a 2 arguments.
 */
class TemplateFieldTest {

    // --- Factory : text ----------------------------------------------------

    @Test
    void text_createsTextFieldWithoutLayout() {
        TemplateField field = TemplateField.text("histoire");
        assertEquals("histoire", field.getName());
        assertEquals(FieldType.TEXT, field.getType());
        assertNull(field.getLayout(), "layout doit etre null pour un champ TEXT");
    }

    // --- Factory : image ---------------------------------------------------

    @Test
    void image_createsImageFieldWithDefaultGalleryLayout() {
        TemplateField field = TemplateField.image("portraits");
        assertEquals("portraits", field.getName());
        assertEquals(FieldType.IMAGE, field.getType());
        assertEquals(ImageLayout.GALLERY, field.getLayout(), "image(name) doit utiliser GALLERY par defaut");
    }

    @Test
    void image_createsImageFieldWithCustomLayout() {
        TemplateField field = TemplateField.image("banniere", ImageLayout.HERO);
        assertEquals(FieldType.IMAGE, field.getType());
        assertEquals(ImageLayout.HERO, field.getLayout());
    }

    // --- Constructeur retrocompat (2 args) ---------------------------------

    @Test
    void twoArgsConstructor_leavesLayoutNull() {
        // Constructeur legacy (name, type) — garde la compat avec le code anterieur
        // a l'ajout du champ `layout`.
        TemplateField field = new TemplateField("nom", FieldType.TEXT);
        assertEquals("nom", field.getName());
        assertEquals(FieldType.TEXT, field.getType());
        assertNull(field.getLayout());
    }

    // --- Builder ------------------------------------------------------------

    @Test
    void builder_allowsFullCustomization() {
        TemplateField field = TemplateField.builder()
                .name("galerie-moodboard")
                .type(FieldType.IMAGE)
                .layout(ImageLayout.MASONRY)
                .build();

        assertEquals("galerie-moodboard", field.getName());
        assertEquals(FieldType.IMAGE, field.getType());
        assertEquals(ImageLayout.MASONRY, field.getLayout());
    }
}
