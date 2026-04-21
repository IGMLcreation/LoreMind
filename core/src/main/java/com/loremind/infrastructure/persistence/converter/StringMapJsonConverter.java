package com.loremind.infrastructure.persistence.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Collections;
import java.util.Map;

/**
 * Convertit une Map<String,String> du domaine en chaîne JSON et inversement.
 * Utilisé pour les maps clé-valeur simples (ex: Page.values — stocke les valeurs
 * des champs dynamiques définis par le Template).
 */
@Converter
public class StringMapJsonConverter implements AttributeConverter<Map<String, String>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Map<String, String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "{}";
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("Erreur sérialisation Map<String,String> → JSON", e);
        }
    }

    @Override
    public Map<String, String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return MAPPER.readValue(dbData, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Erreur désérialisation JSON → Map<String,String>", e);
        }
    }
}
