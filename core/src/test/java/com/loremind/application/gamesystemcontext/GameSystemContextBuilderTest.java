package com.loremind.application.gamesystemcontext;

import com.loremind.domain.gamesystemcontext.ports.GameSystemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test unitaire pour {@link GameSystemContextBuilder#parseH2Sections}. Couvre en
 * particulier les titres avec espaces de fin, qui motivaient l'ajustement du regex
 * (retrait du {@code \s*} final, redondant avec le {@code .trim()} déjà appliqué).
 */
@ExtendWith(MockitoExtension.class)
class GameSystemContextBuilderTest {

    @Mock
    private GameSystemRepository gameSystemRepository;

    @Mock
    private com.loremind.domain.shared.ports.CurrentLanguage currentLanguage;

    @InjectMocks
    private GameSystemContextBuilder builder;

    @Test
    void parseH2Sections_nullOrBlank_returnsEmptyMap() {
        assertTrue(builder.parseH2Sections(null).isEmpty());
        assertTrue(builder.parseH2Sections("   ").isEmpty());
    }

    @Test
    void parseH2Sections_ignoresPreambleBeforeFirstHeader() {
        String md = "Préambule libre\nignoré.\n## Combat\nRègles de combat.";

        Map<String, String> sections = builder.parseH2Sections(md);

        assertEquals(1, sections.size());
        assertEquals("Règles de combat.", sections.get("Combat"));
    }

    @Test
    void parseH2Sections_multipleSections_preservesOrderAndContent() {
        String md = "## Combat\nRègles de combat.\n## Magie\nRègles de magie.";

        Map<String, String> sections = builder.parseH2Sections(md);

        assertEquals(java.util.List.of("Combat", "Magie"), java.util.List.copyOf(sections.keySet()));
        assertEquals("Règles de combat.", sections.get("Combat"));
        assertEquals("Règles de magie.", sections.get("Magie"));
    }

    @Test
    void parseH2Sections_trailingSpacesOnHeaderLine_titleIsTrimmed() {
        String md = "##   Combat   \nRègles de combat.";

        Map<String, String> sections = builder.parseH2Sections(md);

        assertEquals(1, sections.size());
        assertTrue(sections.containsKey("Combat"));
    }

    @Test
    void parseH2Sections_trimsContentWhitespace() {
        String md = "## Combat\n\n  Règles de combat.  \n\n## Magie\nRègles de magie.";

        Map<String, String> sections = builder.parseH2Sections(md);

        assertEquals("Règles de combat.", sections.get("Combat"));
    }

    @Test
    void build_usesTheRulesOfTheInterfaceLanguage_withFallback() {
        com.loremind.domain.gamesystemcontext.GameSystem gs = com.loremind.domain.gamesystemcontext.GameSystem.builder()
                .id("gs").name("Nimble").rulesMarkdown("## Combat\nJet de d20").rulesMarkdownEn("## Fighting\nRoll a d20").build();
        org.mockito.Mockito.when(gameSystemRepository.findById("gs")).thenReturn(java.util.Optional.of(gs));

        org.mockito.Mockito.when(currentLanguage.get()).thenReturn("en");
        assertTrue(builder.buildOptional("gs", com.loremind.domain.gamesystemcontext.GenerationIntent.GENERIC)
                .orElseThrow().toString().contains("Roll a d20"));

        org.mockito.Mockito.when(currentLanguage.get()).thenReturn("fr");
        assertTrue(builder.buildOptional("gs", com.loremind.domain.gamesystemcontext.GenerationIntent.GENERIC)
                .orElseThrow().toString().contains("Jet de d20"));

        gs.setRulesMarkdownEn(null); // pas de version anglaise : repli sur le français
        org.mockito.Mockito.when(currentLanguage.get()).thenReturn("en");
        assertTrue(builder.buildOptional("gs", com.loremind.domain.gamesystemcontext.GenerationIntent.GENERIC)
                .orElseThrow().toString().contains("Jet de d20"));
    }
}
