package com.loremind.infrastructure.persistence.converter;

import com.loremind.domain.shared.template.FieldType;
import com.loremind.domain.shared.template.ImageLayout;
import com.loremind.domain.shared.template.TemplateField;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests pour TemplateFieldListJsonConverter.
 * Le converter le plus important : il gere la RETROCOMPATIBILITE entre le
 * format legacy (liste de strings) et le nouveau format (liste d'objets
 * {name, type, layout}). Chaque test documente un cas de migration implicite.
 */
class TemplateFieldListJsonConverterTest {

    private final TemplateFieldListJsonConverter converter = new TemplateFieldListJsonConverter();

    // ---------- toDb : ecrit toujours le nouveau format --------------------

    @Test
    void toDb_nullList_yieldsEmptyArray() {
        assertEquals("[]", converter.convertToDatabaseColumn(null));
    }

    @Test
    void toDb_emptyList_yieldsEmptyArray() {
        assertEquals("[]", converter.convertToDatabaseColumn(List.of()));
    }

    @Test
    void toDb_writesObjectFormat_notLegacyStrings() {
        // Test cle : meme avec un TemplateField "simple" (TEXT), on ecrit
        // l'objet complet, jamais la chaine. C'est ce qui permet la
        // migration implicite a la 1ere sauvegarde.
        String json = converter.convertToDatabaseColumn(List.of(TemplateField.text("histoire")));
        assertTrue(json.contains("\"name\":\"histoire\""));
        assertTrue(json.contains("\"type\":\"TEXT\""));
    }

    // ---------- fromDb : format legacy (chaines) ---------------------------

    @Test
    void fromDb_legacyFormat_readsStringsAsTextFields() {
        List<TemplateField> result = converter.convertToEntityAttribute(
                "[\"Nom\",\"Histoire\",\"Portrait\"]");

        assertEquals(3, result.size());
        for (TemplateField f : result) {
            assertEquals(FieldType.TEXT, f.getType(),
                    "Format legacy -> tous interpretes comme TEXT");
            assertNull(f.getLayout(), "TEXT n'a pas de layout");
        }
        assertEquals("Nom", result.get(0).getName());
        assertEquals("Portrait", result.get(2).getName());
    }

    // ---------- fromDb : nouveau format ------------------------------------

    @Test
    void fromDb_newFormat_readsTextField() {
        List<TemplateField> result = converter.convertToEntityAttribute(
                "[{\"name\":\"histoire\",\"type\":\"TEXT\"}]");
        assertEquals(1, result.size());
        assertEquals("histoire", result.get(0).getName());
        assertEquals(FieldType.TEXT, result.get(0).getType());
        assertNull(result.get(0).getLayout());
    }

    @Test
    void fromDb_newFormat_readsImageFieldWithLayout() {
        List<TemplateField> result = converter.convertToEntityAttribute(
                "[{\"name\":\"portrait\",\"type\":\"IMAGE\",\"layout\":\"HERO\"}]");
        assertEquals(1, result.size());
        assertEquals(FieldType.IMAGE, result.get(0).getType());
        assertEquals(ImageLayout.HERO, result.get(0).getLayout());
    }

    @Test
    void fromDb_newFormat_imageFieldWithoutLayout_keepsNull() {
        // layout null cote domaine -> rendu GALLERY par defaut cote UI.
        List<TemplateField> result = converter.convertToEntityAttribute(
                "[{\"name\":\"gallery\",\"type\":\"IMAGE\"}]");
        assertEquals(FieldType.IMAGE, result.get(0).getType());
        assertNull(result.get(0).getLayout());
    }

    @Test
    void fromDb_newFormat_imageFieldWithBlankLayout_keepsNull() {
        List<TemplateField> result = converter.convertToEntityAttribute(
                "[{\"name\":\"gallery\",\"type\":\"IMAGE\",\"layout\":\"\"}]");
        assertNull(result.get(0).getLayout());
    }

    // ---------- fromDb : tolerance aux types/layouts inconnus --------------

    @Test
    void fromDb_unknownType_fallsBackToText() {
        // Tolerance cross-version : si une version future ajoute RICH_TEXT et
        // qu'on redescend vers cette version, on ne plante pas, on degrade.
        List<TemplateField> result = converter.convertToEntityAttribute(
                "[{\"name\":\"nouveau\",\"type\":\"RICH_TEXT\"}]");
        assertEquals(1, result.size());
        assertEquals(FieldType.TEXT, result.get(0).getType());
    }

    @Test
    void fromDb_unknownLayout_keepsNull() {
        List<TemplateField> result = converter.convertToEntityAttribute(
                "[{\"name\":\"img\",\"type\":\"IMAGE\",\"layout\":\"SPIRAL\"}]");
        assertEquals(FieldType.IMAGE, result.get(0).getType());
        assertNull(result.get(0).getLayout(), "Layout inconnu -> null -> GALLERY cote UI");
    }

    // ---------- fromDb : filtrage d'entrees invalides ---------------------

    @Test
    void fromDb_objectWithoutName_isSilentlyIgnored() {
        List<TemplateField> result = converter.convertToEntityAttribute(
                "[{\"type\":\"TEXT\"},{\"name\":\"valide\",\"type\":\"TEXT\"}]");
        assertEquals(1, result.size());
        assertEquals("valide", result.get(0).getName());
    }

    @Test
    void fromDb_objectWithBlankName_isSilentlyIgnored() {
        List<TemplateField> result = converter.convertToEntityAttribute(
                "[{\"name\":\"  \",\"type\":\"TEXT\"}]");
        assertTrue(result.isEmpty());
    }

    @Test
    void fromDb_nonObjectNonStringItem_isSilentlyIgnored() {
        // Ex: nombre ou boolean dans le tableau (jamais produit par nos ecritures
        // mais on est tolerant).
        List<TemplateField> result = converter.convertToEntityAttribute(
                "[42, true, \"Nom\"]");
        assertEquals(1, result.size());
        assertEquals("Nom", result.get(0).getName());
    }

    // ---------- fromDb : non-arrays et erreurs -----------------------------

    @Test
    void fromDb_nonArrayRoot_yieldsEmptyList() {
        // Si le JSON n'est pas un tableau (corruption ou migration ratee),
        // on renvoie une liste vide plutot que de planter.
        assertTrue(converter.convertToEntityAttribute("{\"oops\":true}").isEmpty());
    }

    @Test
    void fromDb_nullString_yieldsEmptyList() {
        assertTrue(converter.convertToEntityAttribute(null).isEmpty());
    }

    @Test
    void fromDb_blankString_yieldsEmptyList() {
        assertTrue(converter.convertToEntityAttribute("  ").isEmpty());
    }

    @Test
    void fromDb_malformedJson_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class,
                () -> converter.convertToEntityAttribute("[{not json}]"));
    }

    // ---------- Round-trip + migration -------------------------------------

    @Test
    void roundTrip_preservesMixedTextAndImageFields() {
        List<TemplateField> source = List.of(
                TemplateField.text("histoire"),
                TemplateField.image("portraits", ImageLayout.MASONRY),
                TemplateField.text("motto"),
                TemplateField.image("cartes", ImageLayout.CAROUSEL)
        );

        String json = converter.convertToDatabaseColumn(source);
        List<TemplateField> back = converter.convertToEntityAttribute(json);

        assertEquals(4, back.size());
        assertEquals("histoire", back.get(0).getName());
        assertEquals(FieldType.TEXT, back.get(0).getType());
        assertEquals("portraits", back.get(1).getName());
        assertEquals(FieldType.IMAGE, back.get(1).getType());
        assertEquals(ImageLayout.MASONRY, back.get(1).getLayout());
        assertEquals(ImageLayout.CAROUSEL, back.get(3).getLayout());
    }

    @Test
    void legacyToNew_migration_isIdempotentAfterFirstWrite() {
        // Un template persiste au format legacy est relu comme une liste de
        // TemplateField TEXT. La prochaine ecriture produit le nouveau format
        // -> la deuxieme relecture donne le meme resultat.
        List<TemplateField> pass1 = converter.convertToEntityAttribute("[\"A\",\"B\"]");
        String rewritten = converter.convertToDatabaseColumn(pass1);
        List<TemplateField> pass2 = converter.convertToEntityAttribute(rewritten);

        assertEquals(pass1.size(), pass2.size());
        for (int i = 0; i < pass1.size(); i++) {
            assertEquals(pass1.get(i).getName(), pass2.get(i).getName());
            assertEquals(pass1.get(i).getType(), pass2.get(i).getType());
        }
    }
}
