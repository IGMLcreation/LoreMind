package com.loremind.infrastructure.persistence.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.campaigncontext.NodeType;
import com.loremind.domain.campaigncontext.QuestNodeRef;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Convertit une {@code List<QuestNodeRef>} (VO du domaine) en JSON stocké en
 * colonne TEXT, et inversement.
 * <p>
 * Même approche que {@code PrerequisiteListJsonConverter} : conversion manuelle
 * map ↔ record pour garder le domaine pur (zéro annotation Jackson) et un format
 * on-disk stable, indépendant des noms de classes Java.
 * <p>
 * Format JSON :
 *   [ {"nodeType": "CHAPTER", "nodeId": "42", "order": 0}, ... ]
 */
@Converter
public class QuestNodeListJsonConverter implements AttributeConverter<List<QuestNodeRef>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<QuestNodeRef> attribute) {
        if (attribute == null || attribute.isEmpty()) return "[]";
        try {
            List<Map<String, Object>> raw = new ArrayList<>(attribute.size());
            for (QuestNodeRef n : attribute) {
                Map<String, Object> m = new HashMap<>();
                m.put("nodeType", n.nodeType().name());
                m.put("nodeId", n.nodeId());
                m.put("order", n.order());
                raw.add(m);
            }
            return MAPPER.writeValueAsString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Erreur sérialisation List<QuestNodeRef> → JSON", e);
        }
    }

    @Override
    public List<QuestNodeRef> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return new ArrayList<>();
        try {
            List<Map<String, Object>> raw = MAPPER.readValue(dbData, new TypeReference<>() {});
            List<QuestNodeRef> result = new ArrayList<>(raw.size());
            for (Map<String, Object> m : raw) {
                NodeType type = NodeType.valueOf(String.valueOf(m.get("nodeType")));
                String nodeId = String.valueOf(m.get("nodeId"));
                Object o = m.get("order");
                int order = (o instanceof Number num) ? num.intValue() : 0;
                result.add(new QuestNodeRef(type, nodeId, order));
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Erreur désérialisation JSON → List<QuestNodeRef>", e);
        }
    }
}
