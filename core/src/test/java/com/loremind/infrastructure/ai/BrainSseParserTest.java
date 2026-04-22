package com.loremind.infrastructure.ai;

import com.loremind.domain.generationcontext.ChatUsage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests unitaires pour BrainSseParser.
 * Parser minimaliste (sans Jackson) : on verifie les cas nominaux et
 * TOUS les edge cases — c'est precisement ce genre de code artisanal
 * qui casse silencieusement si on n'a pas de tests.
 */
class BrainSseParserTest {

    private final BrainSseParser parser = new BrainSseParser();

    // ---------- parseUsage --------------------------------------------------

    @Test
    void parseUsage_parsesCompletePayload() {
        String json = "{\"system\":1200,\"history\":3400,\"current\":150,\"max\":8192}";
        ChatUsage usage = parser.parseUsage(json);

        assertNotNull(usage);
        assertEquals(1200, usage.system());
        assertEquals(3400, usage.history());
        assertEquals(150, usage.current());
        assertEquals(8192, usage.max());
    }

    @Test
    void parseUsage_returnsNull_whenJsonIsNull() {
        assertNull(parser.parseUsage(null));
    }

    @Test
    void parseUsage_treatsMissingFieldAsZero() {
        // Un champ manquant ne doit pas planter : l'extractIntField renvoie 0.
        String json = "{\"system\":100,\"history\":200}";
        ChatUsage usage = parser.parseUsage(json);

        assertNotNull(usage);
        assertEquals(100, usage.system());
        assertEquals(200, usage.history());
        assertEquals(0, usage.current());
        assertEquals(0, usage.max());
    }

    @Test
    void parseUsage_supportsNegativeValues() {
        // L'API ne devrait jamais envoyer de negatifs mais le parser ne doit
        // pas les confondre avec du JSON invalide.
        String json = "{\"system\":-1,\"history\":0,\"current\":0,\"max\":0}";
        ChatUsage usage = parser.parseUsage(json);
        assertEquals(-1, usage.system());
    }

    @Test
    void parseUsage_toleratesWhitespaceAroundColon() {
        String json = "{\"system\" :  100, \"history\":200,\"current\":50,\"max\":4096}";
        ChatUsage usage = parser.parseUsage(json);
        assertEquals(100, usage.system());
        assertEquals(200, usage.history());
    }

    @Test
    void parseUsage_treatsNonIntegerFieldAsZero() {
        // Comportement defensif : le parser scanne caractere par caractere et
        // s'arrete des qu'il ne voit plus de chiffre. Pour un champ contenant
        // une chaine (ex: "abc"), il ne lit aucun chiffre -> renvoie 0. Pas
        // d'exception propagee : le chat continue, la jauge affiche juste 0.
        String json = "{\"system\":\"abc\",\"history\":0,\"current\":0,\"max\":0}";
        ChatUsage usage = parser.parseUsage(json);
        assertNotNull(usage);
        assertEquals(0, usage.system());
    }

    // ---------- parseToken --------------------------------------------------

    @Test
    void parseToken_extractsSimpleToken() {
        assertEquals("hello", parser.parseToken("{\"token\":\"hello\"}"));
    }

    @Test
    void parseToken_returnsNull_whenJsonIsNull() {
        assertNull(parser.parseToken(null));
    }

    @Test
    void parseToken_returnsNull_whenTokenFieldMissing() {
        assertNull(parser.parseToken("{\"other\":\"value\"}"));
    }

    @Test
    void parseToken_unescapesNewlines() {
        assertEquals("line1\nline2", parser.parseToken("{\"token\":\"line1\\nline2\"}"));
    }

    @Test
    void parseToken_unescapesDoubleQuotes() {
        // Attention : lastIndexOf('"') trouve le guillemet fermant final du JSON,
        // donc les guillemets echappes internes sont bien inclus dans la valeur.
        assertEquals("il dit \"salut\"", parser.parseToken("{\"token\":\"il dit \\\"salut\\\"\"}"));
    }

    @Test
    void parseToken_unescapesBackslash() {
        assertEquals("path\\file", parser.parseToken("{\"token\":\"path\\\\file\"}"));
    }

    @Test
    void parseToken_handlesEmptyStringToken() {
        assertEquals("", parser.parseToken("{\"token\":\"\"}"));
    }
}
