package com.loremind.infrastructure.persistence.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Convertit une Map<String, List<String>> du domaine en chaine JSON et inversement.
 * <p>
 * Utilise pour Page.imageValues : pour chaque champ IMAGE du template
 * (ex: "Portrait"), la map stocke la liste ordonnee des IDs d'images uploadees.
 * <p>
 * Exemple de JSON produit :
 *   {"Portrait": ["42","17"], "Carte": ["99"]}
 * <p>
 * Adaptateur technique d'infrastructure : le domaine ne connait jamais ce converter.
 */
@Converter
public class StringListMapJsonConverter
        implements AttributeConverter<Map<String, List<String>>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Map<String, List<String>> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "{}";
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Erreur serialisation Map<String, List<String>> -> JSON", e);
        }
    }

    @Override
    public Map<String, List<String>> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return MAPPER.readValue(dbData, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Erreur deserialisation JSON -> Map<String, List<String>>", e);
        }
    }
}
