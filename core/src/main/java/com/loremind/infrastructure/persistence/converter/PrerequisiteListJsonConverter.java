package com.loremind.infrastructure.persistence.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.campaigncontext.Prerequisite;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Convertit une List<Prerequisite> (sealed type du domaine) en JSON stocké en base.
 * <p>
 * On utilise un discriminant {@code kind} explicite pour éviter de polluer le domaine
 * avec des annotations Jackson (@JsonTypeInfo / @JsonSubTypes). Le format on-disk est
 * stable et indépendant des noms de classes Java.
 * <p>
 * Format JSON :
 *   [
 *     {"kind": "QUEST_COMPLETED", "questId": "42"},
 *     {"kind": "SESSION_REACHED", "minSessionNumber": 3},
 *     {"kind": "FLAG_SET",        "flagName": "forgeron_rencontre"}
 *   ]
 */
@Converter
public class PrerequisiteListJsonConverter implements AttributeConverter<List<Prerequisite>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String KIND = "kind";
    private static final String KIND_QUEST_COMPLETED = "QUEST_COMPLETED";
    private static final String KIND_SESSION_REACHED = "SESSION_REACHED";
    private static final String KIND_FLAG_SET = "FLAG_SET";

    @Override
    public String convertToDatabaseColumn(List<Prerequisite> attribute) {
        if (attribute == null || attribute.isEmpty()) return "[]";
        try {
            List<Map<String, Object>> raw = new ArrayList<>(attribute.size());
            for (Prerequisite p : attribute) {
                raw.add(toMap(p));
            }
            return MAPPER.writeValueAsString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Erreur sérialisation List<Prerequisite> → JSON", e);
        }
    }

    @Override
    public List<Prerequisite> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return new ArrayList<>();
        try {
            List<Map<String, Object>> raw = MAPPER.readValue(dbData, new TypeReference<>() {});
            List<Prerequisite> result = new ArrayList<>(raw.size());
            for (Map<String, Object> m : raw) {
                result.add(fromMap(m));
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Erreur désérialisation JSON → List<Prerequisite>", e);
        }
    }

    private Map<String, Object> toMap(Prerequisite p) {
        Map<String, Object> m = new HashMap<>();
        if (p instanceof Prerequisite.QuestCompleted q) {
            m.put(KIND, KIND_QUEST_COMPLETED);
            m.put("questId", q.questId());
        } else if (p instanceof Prerequisite.SessionReached s) {
            m.put(KIND, KIND_SESSION_REACHED);
            m.put("minSessionNumber", s.minSessionNumber());
        } else if (p instanceof Prerequisite.FlagSet f) {
            m.put(KIND, KIND_FLAG_SET);
            m.put("flagName", f.flagName());
        } else {
            throw new IllegalStateException("Prerequisite non géré : " + p.getClass().getName());
        }
        return m;
    }

    private Prerequisite fromMap(Map<String, Object> m) {
        String kind = String.valueOf(m.get(KIND));
        switch (kind) {
            case KIND_QUEST_COMPLETED:
                return new Prerequisite.QuestCompleted(String.valueOf(m.get("questId")));
            case KIND_SESSION_REACHED:
                Object n = m.get("minSessionNumber");
                return new Prerequisite.SessionReached(((Number) n).intValue());
            case KIND_FLAG_SET:
                return new Prerequisite.FlagSet(String.valueOf(m.get("flagName")));
            default:
                throw new IllegalStateException("Kind Prerequisite inconnu : " + kind);
        }
    }
}
