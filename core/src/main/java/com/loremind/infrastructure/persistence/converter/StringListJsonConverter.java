package com.loremind.infrastructure.persistence.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Collections;
import java.util.List;

/**
 * Convertit une List<String> du domaine en chaîne JSON stockée en base, et
 * inversement. Utilisé pour les listes simples (ex: Template.fields).
 * <p>
 * Ceci est un adaptateur technique d'infrastructure : il permet au domaine de
 * rester pur (juste une List<String>) pendant que JPA parle JSON à PostgreSQL.
 */
@Converter
public class StringListJsonConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("Erreur sérialisation List<String> → JSON", e);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(dbData, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Erreur désérialisation JSON → List<String>", e);
        }
    }
}
