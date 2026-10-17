package com.loremind.infrastructure.persistence.converter;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.campaigncontext.CampaignPublication;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertit la fiche {@link CampaignPublication} en JSON stocké dans une colonne TEXT,
 * et inversement. Fiche vide → NULL en base. Champs inconnus ignorés (fiche écrite par
 * une version plus récente).
 */
@Converter
public class CampaignPublicationJsonConverter implements AttributeConverter<CampaignPublication, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Override
    public String convertToDatabaseColumn(CampaignPublication attribute) {
        if (attribute == null || !attribute.hasContent()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("Erreur sérialisation CampaignPublication → JSON", e);
        }
    }

    @Override
    public CampaignPublication convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(dbData, CampaignPublication.class);
        } catch (Exception e) {
            throw new IllegalStateException("Erreur désérialisation JSON → CampaignPublication", e);
        }
    }
}
