package com.loremind.infrastructure.persistence.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.campaigncontext.SceneBattlemap;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Collections;
import java.util.List;

/**
 * Convertit une List<SceneBattlemap> du domaine en chaîne JSON stockée en base,
 * et inversement. Même pattern que SceneBranchListJsonConverter.
 */
@Converter
public class SceneBattlemapListJsonConverter implements AttributeConverter<List<SceneBattlemap>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<SceneBattlemap> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("Erreur sérialisation List<SceneBattlemap> → JSON", e);
        }
    }

    @Override
    public List<SceneBattlemap> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(dbData, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Erreur désérialisation JSON → List<SceneBattlemap>", e);
        }
    }
}
