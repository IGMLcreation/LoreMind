package com.loremind.infrastructure.persistence.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.campaigncontext.structure.Room;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Collections;
import java.util.List;

/**
 * Convertit la liste de pièces explorables d'une Scene en JSON pour la persistance.
 *
 * <p>Une Room contient elle-même une liste de {@link com.loremind.domain.campaigncontext.structure.RoomBranch}
 * (record Java) ; Jackson 2.12+ sait sérialiser/désérialiser les records nativement
 * via le constructeur canonique, donc rien de spécial à faire pour les branches.</p>
 */
@Converter
public class RoomListJsonConverter implements AttributeConverter<List<Room>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<Room> attribute) {
        if (attribute == null || attribute.isEmpty()) return "[]";
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("Erreur sérialisation List<Room> → JSON", e);
        }
    }

    @Override
    public List<Room> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return Collections.emptyList();
        try {
            return MAPPER.readValue(dbData, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Erreur désérialisation JSON → List<Room>", e);
        }
    }
}
