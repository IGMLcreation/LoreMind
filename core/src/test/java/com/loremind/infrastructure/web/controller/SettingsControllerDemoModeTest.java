package com.loremind.infrastructure.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifie le blocage des endpoints sensibles de {@link SettingsController} quand
 * {@code app.demo-mode=true} : lecture/ecriture des settings et gestion des modeles
 * Ollama (pull/delete) doivent renvoyer 403, sans jamais toucher le Brain.
 * <p>
 * Les requetes sont authentifiees en ADMIN (sinon la securite renverrait 401 avant
 * d'atteindre le garde demo). Ce test valide donc aussi que le
 * {@code GlobalExceptionHandler} propage le statut declare par le
 * {@link org.springframework.web.server.ResponseStatusException} (403), sans l'ecraser en 500.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.demo-mode=true")
class SettingsControllerDemoModeTest {

    private static final String ADMIN_AUTH = "Basic " + Base64.getEncoder()
            .encodeToString("test-admin:test-admin-password".getBytes(StandardCharsets.UTF_8));

    @Autowired private MockMvc mockMvc;

    @Test
    void getSettings_returns403() throws Exception {
        mockMvc.perform(get("/api/settings").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateSettings_returns403() throws Exception {
        mockMvc.perform(put("/api/settings")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\":\"light\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void pullOllamaModel_returns403() throws Exception {
        mockMvc.perform(post("/api/settings/models/ollama/pull")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"llama3\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteOllamaModel_returns403() throws Exception {
        mockMvc.perform(delete("/api/settings/models/ollama/{name}", "llama3")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH))
                .andExpect(status().isForbidden());
    }
}
