package com.loremind.infrastructure.web.config;

import com.loremind.application.remoteaccess.RemoteAccessService;
import com.loremind.domain.remoteaccess.RemoteDevice;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Test unitaire de {@link RemoteAccessFilter} : ce qui passe et ce qui est refusé selon
 * le connecteur d'arrivée (local / réseau), le chemin et le cookie d'appareil.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RemoteAccessFilterTest {

    private static final int LOCAL_PORT = 8080;
    private static final int LAN_PORT = 8765;

    @Mock private RemoteAccessService service;
    private RemoteAccessFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RemoteAccessFilter(service);
        when(service.lanPort()).thenReturn(LAN_PORT);
        when(service.authenticate("bon-jeton")).thenReturn(Optional.of(RemoteDevice.builder().id("1").name("Tablette").build()));
        when(service.authenticate(null)).thenReturn(Optional.empty());
        when(service.authenticate("mauvais")).thenReturn(Optional.empty());
    }

    private record Outcome(boolean passed, int status, String body) {}

    private Outcome run(int localPort, String path, String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setLocalPort(localPort);
        if (token != null) request.setCookies(new Cookie(RemoteAccessFilter.DEVICE_COOKIE, token));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return new Outcome(chain.getRequest() != null, response.getStatus(), response.getContentAsString());
    }

    @Test
    void localConnector_isUntouched_evenForSettings() throws Exception {
        assertTrue(run(LOCAL_PORT, "/api/campaigns", null).passed());
        assertTrue(run(LOCAL_PORT, "/api/settings", null).passed());
    }

    @Test
    void lanClosed_meansEverythingIsLocal() throws Exception {
        when(service.lanPort()).thenReturn(-1);
        assertTrue(run(LOCAL_PORT, "/api/campaigns", null).passed());
    }

    @Test
    void lan_staticFrontAndPublicApi_passWithoutToken() throws Exception {
        assertTrue(run(LAN_PORT, "/", null).passed());
        assertTrue(run(LAN_PORT, "/pair", null).passed());
        assertTrue(run(LAN_PORT, "/main-ABC123.js", null).passed());
        assertTrue(run(LAN_PORT, "/api/config", null).passed());
        assertTrue(run(LAN_PORT, "/api/remote-access/pair", null).passed());
        assertTrue(run(LAN_PORT, "/api/remote-access/session", null).passed());
    }

    @Test
    void lan_api_requiresValidDeviceToken() throws Exception {
        Outcome none = run(LAN_PORT, "/api/campaigns", null);
        assertFalse(none.passed());
        assertEquals(401, none.status());
        assertTrue(none.body().contains("remote_pairing_required"));

        assertEquals(401, run(LAN_PORT, "/api/images/42", "mauvais").status());
        assertTrue(run(LAN_PORT, "/api/campaigns", "bon-jeton").passed());
        assertTrue(run(LAN_PORT, "/api/images/42", "bon-jeton").passed());
    }

    @Test
    void lan_adminRoutes_areAlwaysForbidden_evenWhenPaired() throws Exception {
        for (String path : new String[]{"/api/settings", "/api/settings/remote-access", "/api/admin/data/export", "/api/admin/updates/channel"}) {
            Outcome outcome = run(LAN_PORT, path, "bon-jeton");
            assertFalse(outcome.passed(), path);
            assertEquals(403, outcome.status(), path);
        }
    }
}
