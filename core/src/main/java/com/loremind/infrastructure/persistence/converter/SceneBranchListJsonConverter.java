package com.loremind.infrastructure.persistence.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.campaigncontext.SceneBranch;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Collections;
import java.util.List;

/**
 * Convertit une List<SceneBranch> du domaine en chaîne JSON stockée en base,
 * et inversement. Même pattern que StringListJsonConverter mais typé sur
 * le Value Object SceneBranch.
 * <p>
 * Adaptateur d'infrastructure : le domaine reste pur (List<SceneBranch>)
 * pendant que PostgreSQL reçoit un TEXT JSON.
 */
@Converter
public class SceneBranchListJsonConverter implements AttributeConverter<List<SceneBranch>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<SceneBranch> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("Erreur sérialisation List<SceneBranch> → JSON", e);
        }
    }

    @Override
    public List<SceneBranch> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(dbData, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Erreur désérialisation JSON → List<SceneBranch>", e);
        }
    }
}
