package com.loremind.infrastructure.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'integration pour {@link VersionController}.
 * <p>
 * Endpoint trivial : GET /api/version renvoie {"version": "..."}.
 * En test, {@code BuildProperties} peut etre absent (pas de build Maven) :
 * le controleur retombe alors sur "dev". On verifie seulement que la cle
 * "version" est presente et non vide.
 */
@SpringBootTest
@AutoConfigureMockMvc
class VersionControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void getVersion_returns200_withVersionKey() throws Exception {
        mockMvc.perform(get("/api/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").exists())
                .andExpect(jsonPath("$.version").isNotEmpty());
    }
}
