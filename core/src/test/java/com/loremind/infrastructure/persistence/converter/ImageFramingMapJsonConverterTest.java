package com.loremind.infrastructure.persistence.converter;

import com.loremind.domain.lorecontext.ImageFraming;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests du converter de cadrage d'images (Map<String, Map<String, ImageFraming>>).
 * Vérifie notamment que le record {@link ImageFraming} fait bien l'aller-retour
 * JSON (Jackson supporte les records nativement) — sinon les valeurs seraient
 * perdues silencieusement.
 */
class ImageFramingMapJsonConverterTest {

    private final ImageFramingMapJsonConverter converter = new ImageFramingMapJsonConverter();

    @Test
    void toDb_nullOrEmpty_yieldsEmptyObject() {
        assertEquals("{}", converter.convertToDatabaseColumn(null));
        assertEquals("{}", converter.convertToDatabaseColumn(Map.of()));
    }

    @Test
    void fromDb_nullOrBlank_yieldsEmptyMap() {
        assertTrue(converter.convertToEntityAttribute(null).isEmpty());
        assertTrue(converter.convertToEntityAttribute("  ").isEmpty());
    }

    @Test
    void roundTrip_preservesFramingPerImage() {
        Map<String, Map<String, ImageFraming>> source = Map.of(
                "blk-illu", Map.of(
                        "img-42", new ImageFraming(30.0, 70.0, 1.5),
                        "img-7", new ImageFraming(50.0, 50.0, 1.0)));

        Map<String, Map<String, ImageFraming>> back =
                converter.convertToEntityAttribute(converter.convertToDatabaseColumn(source));

        ImageFraming f = back.get("blk-illu").get("img-42");
        assertEquals(30.0, f.x());
        assertEquals(70.0, f.y());
        assertEquals(1.5, f.scale());
        assertEquals(1.0, back.get("blk-illu").get("img-7").scale());
    }

    @Test
    void fromDb_readsExplicitJson() {
        Map<String, Map<String, ImageFraming>> back = converter.convertToEntityAttribute(
                "{\"blk\":{\"img\":{\"x\":10.0,\"y\":20.0,\"scale\":2.0}}}");
        ImageFraming f = back.get("blk").get("img");
        assertEquals(10.0, f.x());
        assertEquals(20.0, f.y());
        assertEquals(2.0, f.scale());
    }
}
