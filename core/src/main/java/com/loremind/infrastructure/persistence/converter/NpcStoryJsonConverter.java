package com.loremind.infrastructure.persistence.converter;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.campaigncontext.bestiary.NpcStory;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertit la fiche {@link NpcStory} (rôle dans l'histoire d'un PNJ) en JSON stocké dans une colonne TEXT,
 * et inversement. Fiche vide → NULL en base. Champs inconnus ignorés (fiche écrite par
 * une version plus récente).
 */
@Converter
public class NpcStoryJsonConverter implements AttributeConverter<NpcStory, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Override
    public String convertToDatabaseColumn(NpcStory attribute) {
        if (attribute == null || !attribute.hasContent()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("Erreur sérialisation NpcStory → JSON", e);
        }
    }

    @Override
    public NpcStory convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(dbData, NpcStory.class);
        } catch (Exception e) {
            throw new IllegalStateException("Erreur désérialisation JSON → NpcStory", e);
        }
    }
}
