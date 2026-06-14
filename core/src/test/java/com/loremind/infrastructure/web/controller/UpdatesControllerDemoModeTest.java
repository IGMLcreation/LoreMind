package com.loremind.infrastructure.web.controller;

import com.loremind.infrastructure.updates.UpdateCheckService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifie le verrouillage de {@link UpdatesController} quand {@code app.demo-mode=true} :
 * check / check-beta / apply doivent renvoyer 403 (garde demo), jamais 401/200.
 * <p>
 * Les requetes sont authentifiees en ADMIN (sinon la securite renverrait 401 avant
 * d'atteindre le garde demo). Le {@code GlobalExceptionHandler} doit propager le
 * statut du {@link org.springframework.web.server.ResponseStatusException} (403).
 * Contexte distinct (fichier separe) car la valeur demo-mode est injectee a la
 * construction du controleur — meme convention que SettingsControllerDemoModeTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.demo-mode=true")
class UpdatesControllerDemoModeTest {

    private static final String ADMIN_AUTH = "Basic " + Base64.getEncoder()
            .encodeToString("test-admin:test-admin-password".getBytes(StandardCharsets.UTF_8));

    @Autowired private MockMvc mockMvc;
    @MockBean private UpdateCheckService updates;

    @Test
    void check_returns403_inDemoMode() throws Exception {
        mockMvc.perform(get("/api/admin/updates/check").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH))
                .andExpect(status().isForbidden());
    }

    @Test
    void checkBeta_returns403_inDemoMode() throws Exception {
        mockMvc.perform(get("/api/admin/updates/check-beta").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH))
                .andExpect(status().isForbidden());
    }

    @Test
    void apply_returns403_inDemoMode() throws Exception {
        mockMvc.perform(post("/api/admin/updates/apply").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH))
                .andExpect(status().isForbidden());
    }
}
