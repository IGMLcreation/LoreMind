package com.loremind.infrastructure.web.controller;

import com.loremind.application.licensing.ChannelSwitcherService;
import com.loremind.application.licensing.LicenseService;
import com.loremind.application.licensing.LicenseService.InstallException;
import com.loremind.domain.licensing.LicenseSnapshot;
import com.loremind.domain.licensing.LicenseStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'integration pour {@link LicenseController} (gestion licence Patreon).
 * <p>
 * {@code /api/license/**} exige le role ADMIN (HTTP Basic) : chaque requete porte
 * l'entete d'auth construite a partir des identifiants de test (cf.
 * src/test/resources/application.properties). Sans cet entete, la securite
 * renverrait 401.
 * <p>
 * Les deux services applicatifs sont mockes : {@link LicenseService} (qui appelle
 * sinon le relais OAuth distant + verification JWT) et {@link ChannelSwitcherService}
 * (qui ecrit sinon dans un volume partage avec le sidecar). Aucun acces reseau ni
 * fichier reel.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LicenseControllerTest {

    /** Identifiants definis dans src/test/resources/application.properties. */
    private static final String ADMIN_AUTH = "Basic " + Base64.getEncoder()
            .encodeToString("test-admin:test-admin-password".getBytes(StandardCharsets.UTF_8));

    @Autowired private MockMvc mockMvc;

    @MockitoBean private LicenseService licenseService;
    @MockitoBean private ChannelSwitcherService channelSwitcher;

    private LicenseSnapshot validSnapshot() {
        return new LicenseSnapshot(
                LicenseStatus.VALID, "user-42", "tier-1", "li-xyz",
                Instant.now().plusSeconds(3600), Instant.now(), true, true);
    }

    @BeforeEach
    void setUp() {
        // Valeurs par defaut neutres ; chaque test surcharge ce dont il a besoin.
        when(licenseService.isLicensingEnabled()).thenReturn(true);
        when(licenseService.getCurrentSnapshot()).thenReturn(validSnapshot());
    }

    // --- Securite ----------------------------------------------------------

    @Test
    void getStatus_returns401_withoutAuth() throws Exception {
        mockMvc.perform(get("/api/license"))
                .andExpect(status().isUnauthorized());
    }

    // --- GET /api/license --------------------------------------------------

    @Test
    void getStatus_returns200() throws Exception {
        mockMvc.perform(get("/api/license").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.patreonUserId").value("user-42"));
    }

    // --- GET /api/license/connect-url --------------------------------------

    @Test
    void getConnectUrl_returns200() throws Exception {
        when(licenseService.buildConnectUrl()).thenReturn("https://relay/oauth?x=1");
        mockMvc.perform(get("/api/license/connect-url").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://relay/oauth?x=1"));
    }

    // --- POST /api/license/install -----------------------------------------

    @Test
    void install_returns200_onValidJwt() throws Exception {
        when(licenseService.installToken("good-jwt")).thenReturn(validSnapshot());
        mockMvc.perform(post("/api/license/install")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jwt\":\"good-jwt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALID"));
    }

    @Test
    void install_returns400_whenJwtMissing() throws Exception {
        mockMvc.perform(post("/api/license/install")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jwt\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("missing jwt"));
    }

    @Test
    void install_returns400_whenInstallFails() throws Exception {
        when(licenseService.installToken("bad-jwt"))
                .thenThrow(new InstallException("Invalid JWT: signature"));
        mockMvc.perform(post("/api/license/install")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jwt\":\"bad-jwt\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid JWT: signature"));
    }

    // --- DELETE /api/license -----------------------------------------------

    @Test
    void disconnect_returns204() throws Exception {
        mockMvc.perform(delete("/api/license").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH))
                .andExpect(status().isNoContent());
    }

    // --- POST /api/license/refresh -----------------------------------------

    @Test
    void refresh_returns200() throws Exception {
        mockMvc.perform(post("/api/license/refresh").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALID"));
    }

    // --- PUT /api/license/beta-channel -------------------------------------

    @Test
    void setBetaChannel_returns200() throws Exception {
        when(licenseService.setBetaChannelEnabled(eq(false))).thenReturn(validSnapshot());
        mockMvc.perform(put("/api/license/beta-channel")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALID"));
    }

    @Test
    void setBetaChannel_returns409_whenNoLicense() throws Exception {
        when(licenseService.setBetaChannelEnabled(anyBoolean()))
                .thenThrow(new IllegalStateException("No license installed"));
        mockMvc.perform(put("/api/license/beta-channel")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("No license installed"));
    }

    // --- GET /api/license/channel ------------------------------------------

    @Test
    void getChannel_returns200() throws Exception {
        when(channelSwitcher.getCurrentChannel()).thenReturn(ChannelSwitcherService.Channel.STABLE);
        when(channelSwitcher.isSwitcherAvailable()).thenReturn(true);
        when(channelSwitcher.getLastResult()).thenReturn(null);
        mockMvc.perform(get("/api/license/channel").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentChannel").value("stable"))
                .andExpect(jsonPath("$.switcherAvailable").value(true));
    }

    // --- POST /api/license/channel/switch ----------------------------------

    @Test
    void switchChannel_returns400_whenChannelMissing() throws Exception {
        mockMvc.perform(post("/api/license/channel/switch")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("missing channel"));
    }

    @Test
    void switchChannel_returns400_whenChannelInvalid() throws Exception {
        mockMvc.perform(post("/api/license/channel/switch")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"nightly\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void switchChannel_returns403_whenBetaWithoutLicense() throws Exception {
        // Snapshot EXPIRED -> pas d'acces beta.
        when(licenseService.getCurrentSnapshot()).thenReturn(new LicenseSnapshot(
                LicenseStatus.EXPIRED, null, null, null, null, null, false, false));
        mockMvc.perform(post("/api/license/channel/switch")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"beta\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void switchChannel_returns503_whenSwitcherUnavailable() throws Exception {
        // Licence VALID (autorise beta) mais sidecar absent.
        when(channelSwitcher.isSwitcherAvailable()).thenReturn(false);
        mockMvc.perform(post("/api/license/channel/switch")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"beta\"}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void switchChannel_returns202_onSuccess() throws Exception {
        when(channelSwitcher.isSwitcherAvailable()).thenReturn(true);
        when(channelSwitcher.requestSwitch(ChannelSwitcherService.Channel.STABLE))
                .thenReturn("cmd-1");
        mockMvc.perform(post("/api/license/channel/switch")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"stable\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value("cmd-1"))
                .andExpect(jsonPath("$.channel").value("stable"));
    }

    @Test
    void switchChannel_returns500_whenWriteFails() throws Exception {
        when(channelSwitcher.isSwitcherAvailable()).thenReturn(true);
        doThrow(new IOException("disk full"))
                .when(channelSwitcher).requestSwitch(ChannelSwitcherService.Channel.STABLE);
        mockMvc.perform(post("/api/license/channel/switch")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"stable\"}"))
                .andExpect(status().isInternalServerError());
    }
}
