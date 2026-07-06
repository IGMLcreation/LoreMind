package com.loremind.infrastructure.persistence.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.HashMap;
import java.util.Map;

/**
 * Converter JPA pour convertir Map<String, Object> en String (JSON).
 * Compatible avec PostgreSQL (JSONB peut stocker du JSON dans TEXT).
 * Utilisé pour le champ structure de Template, mais peut servir pour tout champ JSON.
 * C'est un converter générique réutilisable.
 */
@Converter(autoApply = false)
public class MapJsonConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Erreur lors de la conversion Map vers JSON String", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        // Map vide (mutable) plutôt que null : les consommateurs itèrent/écrivent
        // sans avoir à se défendre contre le null (même contrat que les autres
        // converters de ce package).
        if (dbData == null || dbData.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(dbData, Map.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Erreur lors de la conversion JSON String vers Map", e);
        }
    }
}
