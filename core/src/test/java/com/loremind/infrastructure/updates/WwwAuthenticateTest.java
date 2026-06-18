package com.loremind.infrastructure.updates;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests du parseur de challenge {@code WWW-Authenticate} ({@link WwwAuthenticate}),
 * jusque-là non couvert quand il était privé dans UpdateCheckService.
 */
class WwwAuthenticateTest {

    @Test
    void parse_challengeGhcrComplet() {
        // Forme typique renvoyée par ghcr.io / Docker Hub.
        Map<String, String> p = WwwAuthenticate.parseParams(
                "realm=\"https://ghcr.io/token\",service=\"ghcr.io\",scope=\"repository:org/img:pull\"");
        assertEquals("https://ghcr.io/token", p.get("realm"));
        assertEquals("ghcr.io", p.get("service"));
        assertEquals("repository:org/img:pull", p.get("scope"));
    }

    @Test
    void parse_valeursNonQuotees() {
        Map<String, String> p = WwwAuthenticate.parseParams("realm=https://r/token, service=reg");
        assertEquals("https://r/token", p.get("realm"));
        assertEquals("reg", p.get("service"));
    }

    @Test
    void parse_chaineVide_donneMapVide() {
        assertTrue(WwwAuthenticate.parseParams("").isEmpty());
    }

    @Test
    void parse_guillemetNonFerme_sArreteProprement() {
        // Valeur ouverte sans guillemet fermant : on s'arrête sans planter.
        Map<String, String> p = WwwAuthenticate.parseParams("realm=\"https://r/token");
        assertNull(p.get("realm"));
    }
}
