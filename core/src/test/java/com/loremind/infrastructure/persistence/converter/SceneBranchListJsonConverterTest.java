package com.loremind.infrastructure.persistence.converter;

import com.loremind.domain.campaigncontext.SceneBranch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests pour SceneBranchListJsonConverter.
 * SceneBranch est immuable (@Value + @Jacksonized), donc Jackson utilise le
 * builder pour la deserialisation. Le round-trip est le test critique :
 * il casserait silencieusement si quelqu'un retirait @Jacksonized.
 */
class SceneBranchListJsonConverterTest {

    private final SceneBranchListJsonConverter converter = new SceneBranchListJsonConverter();

    @Test
    void toDb_nullList_yieldsEmptyJsonArray() {
        assertEquals("[]", converter.convertToDatabaseColumn(null));
    }

    @Test
    void toDb_emptyList_yieldsEmptyJsonArray() {
        assertEquals("[]", converter.convertToDatabaseColumn(List.of()));
    }

    @Test
    void fromDb_nullString_yieldsEmptyList() {
        assertTrue(converter.convertToEntityAttribute(null).isEmpty());
    }

    @Test
    void fromDb_blankString_yieldsEmptyList() {
        assertTrue(converter.convertToEntityAttribute("  ").isEmpty());
    }

    @Test
    void fromDb_malformedJson_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class,
                () -> converter.convertToEntityAttribute("not json"));
    }

    @Test
    void roundTrip_preservesAllBranchFields() {
        // Test critique : Jackson doit reconstruire SceneBranch (record) via
        // son constructeur canonique sans aucune annotation.
        List<SceneBranch> source = List.of(
                new SceneBranch("si les joueurs attaquent", "sc-combat", "initiative > 15"),
                SceneBranch.of("si les joueurs fuient", "sc-poursuite")
        );

        String json = converter.convertToDatabaseColumn(source);
        List<SceneBranch> back = converter.convertToEntityAttribute(json);

        assertEquals(2, back.size());
        assertEquals("si les joueurs attaquent", back.get(0).label());
        assertEquals("sc-combat", back.get(0).targetSceneId());
        assertEquals("initiative > 15", back.get(0).condition());
        assertEquals("sc-poursuite", back.get(1).targetSceneId());
        assertNull(back.get(1).condition(), "condition absente doit rester null apres round-trip");
    }
}
