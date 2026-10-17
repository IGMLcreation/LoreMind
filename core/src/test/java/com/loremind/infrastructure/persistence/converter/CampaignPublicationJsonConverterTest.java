package com.loremind.infrastructure.persistence.converter;

import com.loremind.domain.campaigncontext.CampaignPublication;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CampaignPublicationJsonConverterTest {

    private final CampaignPublicationJsonConverter converter = new CampaignPublicationJsonConverter();

    @Test
    void roundTrip() {
        CampaignPublication p = new CampaignPublication("Sous-titre", "Autrice", "D&D 5e", "1-3", "4",
                "3 h", "Argument", "Contexte", "- A\n- B", null, "Crédits", "Licence", "1.0", "42");

        String json = converter.convertToDatabaseColumn(p);

        assertEquals(p, converter.convertToEntityAttribute(json));
    }

    @Test
    void emptyIsNull_andUnknownFieldsAreIgnored() {
        assertNull(converter.convertToDatabaseColumn(CampaignPublication.empty()));
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
        assertNull(converter.convertToEntityAttribute("  "));
        // Fiche écrite par une version plus récente : champ inconnu toléré.
        assertEquals("T", converter.convertToEntityAttribute("{\"subtitle\":\"T\",\"futureField\":1}").subtitle());
    }
}
