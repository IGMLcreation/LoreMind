package com.loremind.infrastructure.persistence.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.lorecontext.ImageFraming;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Collections;
import java.util.Map;

/**
 * Convertit une {@code Map<String, Map<String, ImageFraming>>} en JSON et inversement.
 * <p>
 * Utilisé pour {@code Page.imageFraming} : pour chaque champ IMAGE (clé = id du bloc),
 * un cadrage (pan/zoom) par image. Exemple :
 *   {"blk-illu": {"img-42": {"x":50.0,"y":30.0,"scale":1.4}}}
 * <p>
 * Tolérant aux propriétés inconnues (évolution de {@link ImageFraming}).
 * Adaptateur technique pur : le domaine ignore ce converter.
 */
@Converter
public class ImageFramingMapJsonConverter
        implements AttributeConverter<Map<String, Map<String, ImageFraming>>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final TypeReference<Map<String, Map<String, ImageFraming>>> TYPE_REF =
            new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, Map<String, ImageFraming>> attribute) {
        if (attribute == null || attribute.isEmpty()) return "{}";
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Erreur serialisation Map<String, Map<String,ImageFraming>> -> JSON", e);
        }
    }

    @Override
    public Map<String, Map<String, ImageFraming>> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return Collections.emptyMap();
        try {
            return MAPPER.readValue(dbData, TYPE_REF);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Erreur deserialisation JSON -> Map<String, Map<String,ImageFraming>>", e);
        }
    }
}
