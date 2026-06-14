package com.loremind.infrastructure.web.controller;

import com.loremind.infrastructure.updates.UpdateCheckService;
import com.loremind.infrastructure.updates.UpdateCheckService.BetaStatus;
import com.loremind.infrastructure.updates.UpdateCheckService.ImageStatus;
import com.loremind.infrastructure.updates.UpdateCheckService.ImageStatusKind;
import com.loremind.infrastructure.updates.UpdateCheckService.UpdateStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'integration pour {@link UpdatesController} (endpoints admin).
 * <p>
 * Le {@link UpdateCheckService} est mocke : c'est un port externe (registry +
 * Watchtower). Aucun appel reseau reel. La classe externe couvre le mode normal
 * (demo off) ; la classe imbriquee {@link DemoMode} force {@code app.demo-mode=true}
 * pour verifier le verrouillage 403.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UpdatesControllerTest {

    /** Identifiants admin definis dans src/test/resources/application.properties. */
    private static final String ADMIN_AUTH = "Basic " + Base64.getEncoder()
            .encodeToString("test-admin:test-admin-password".getBytes(StandardCharsets.UTF_8));

    @Autowired private MockMvc mockMvc;
    @MockBean private UpdateCheckService updates;

    private UpdateStatus sampleUpdate() {
        return new UpdateStatus(true, true, false, "1.0.0",
                List.of(new ImageStatus("img", "1.0.0", "1.1.0", ImageStatusKind.UPDATE_AVAILABLE)),
                Instant.now());
    }

    private BetaStatus sampleBeta() {
        return new BetaStatus(true, false, false, List.of(), Instant.now(), null);
    }

    // --- GET /check ---------------------------------------------------------

    @Test
    void check_returns200() throws Exception {
        when(updates.check()).thenReturn(sampleUpdate());
        mockMvc.perform(get("/api/admin/updates/check").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.updateAvailable").value(true));
    }

    // --- GET /check-beta ----------------------------------------------------

    @Test
    void checkBeta_returns200() throws Exception {
        when(updates.checkBeta()).thenReturn(sampleBeta());
        mockMvc.perform(get("/api/admin/updates/check-beta").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    // --- POST /apply --------------------------------------------------------

    @Test
    void apply_returns202_whenEnabledAndTriggered() throws Exception {
        when(updates.isEnabled()).thenReturn(true);
        mockMvc.perform(post("/api/admin/updates/apply").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("triggered"));
    }

    @Test
    void apply_returns503_whenNotConfigured() throws Exception {
        when(updates.isEnabled()).thenReturn(false);
        mockMvc.perform(post("/api/admin/updates/apply").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void apply_returns502_whenWatchtowerUnreachable() throws Exception {
        when(updates.isEnabled()).thenReturn(true);
        doThrow(new RuntimeException("connect timeout")).when(updates).apply();
        mockMvc.perform(post("/api/admin/updates/apply").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").exists());
    }
}
