package com.loremind.infrastructure.persistence.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Convertit une Map<String, List<Map<String, String>>> en JSON et inversement.
 * <p>
 * Utilise pour Page.tableValues : pour chaque champ TABLE du template, stocke
 * la liste ordonnee des LIGNES du tableau, chaque ligne etant une map
 * colonne -> cellule. Exemple :
 *   {"Inventaire": [{"Objet":"Potion","Prix":"50 po"}, {"Objet":"Corde","Prix":"1 po"}]}
 * <p>
 * Adaptateur technique pur : le domaine ignore ce converter.
 */
@Converter
public class StringRowListMapJsonConverter
        implements AttributeConverter<Map<String, List<Map<String, String>>>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, List<Map<String, String>>>> TYPE_REF =
            new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, List<Map<String, String>>> attribute) {
        if (attribute == null || attribute.isEmpty()) return "{}";
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Erreur serialisation Map<String, List<Map<String,String>>> -> JSON", e);
        }
    }

    @Override
    public Map<String, List<Map<String, String>>> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return Collections.emptyMap();
        try {
            return MAPPER.readValue(dbData, TYPE_REF);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Erreur deserialisation JSON -> Map<String, List<Map<String,String>>>", e);
        }
    }
}
