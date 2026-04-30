package com.loremind.infrastructure.persistence.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Collections;
import java.util.Map;

/**
 * Convertit une Map<String, Map<String, String>> en JSON et inversement.
 * <p>
 * Utilise pour Character/Npc.keyValueValues : pour chaque champ KEY_VALUE_LIST
 * du template, stocke une map label -> value. Exemple :
 *   {"Caracteristiques": {"FOR":"16","DEX":"12","CON":"14"}}
 * <p>
 * Adaptateur technique pur : le domaine ignore ce converter.
 */
@Converter
public class StringMapMapJsonConverter
        implements AttributeConverter<Map<String, Map<String, String>>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Map<String, String>>> TYPE_REF =
            new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, Map<String, String>> attribute) {
        if (attribute == null || attribute.isEmpty()) return "{}";
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Erreur serialisation Map<String, Map<String,String>> -> JSON", e);
        }
    }

    @Override
    public Map<String, Map<String, String>> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return Collections.emptyMap();
        try {
            return MAPPER.readValue(dbData, TYPE_REF);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Erreur deserialisation JSON -> Map<String, Map<String,String>>", e);
        }
    }
}
