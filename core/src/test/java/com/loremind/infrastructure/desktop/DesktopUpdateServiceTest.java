package com.loremind.infrastructure.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mises a jour du bureau : la version stable suit {@code /releases/latest} ; une version
 * beta (canal libre, publie en prerelease GitHub) suit la liste complete des releases.
 */
class DesktopUpdateServiceTest {

    private static final String LATEST = "https://api.github.com/repos/IGMLcreation/LoreMind/releases/latest";
    private static final String ALL = "https://api.github.com/repos/IGMLcreation/LoreMind/releases?per_page=30";
    private static final ObjectMapper JSON = new ObjectMapper();

    private static DesktopUpdateService service(String currentVersion, RestTemplate http) {
        Properties p = new Properties();
        p.setProperty("version", currentVersion);
        DesktopUpdateService svc = new DesktopUpdateService(new RestTemplateBuilder(), true, LATEST, new BuildProperties(p));
        ReflectionTestUtils.setField(svc, "http", http);
        return svc;
    }

    private static void reply(RestTemplate http, String url, String json) throws Exception {
        JsonNode body = JSON.readTree(json);
        when(http.exchange(eq(url), eq(HttpMethod.GET), any(), eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(body));
    }

    @Test
    void stableVersion_followsTheLatestStableRelease() throws Exception {
        RestTemplate http = mock(RestTemplate.class);
        reply(http, LATEST, "{\"tag_name\":\"v1.6.0\",\"html_url\":\"https://x/1.6.0\"}");

        Optional<DesktopUpdateService.UpdateInfo> info = service("1.5.0", http).checkForUpdate();

        assertTrue(info.isPresent());
        assertEquals("1.6.0", info.get().latestVersion());
    }

    @Test
    void betaVersion_isToldAboutTheNextBeta() throws Exception {
        RestTemplate http = mock(RestTemplate.class);
        reply(http, ALL, "[{\"tag_name\":\"v1.5.0-beta.2\",\"prerelease\":true,\"html_url\":\"https://x/b2\"},"
                + "{\"tag_name\":\"v1.6.0-beta\",\"draft\":true},"
                + "{\"tag_name\":\"v1.4.0\",\"html_url\":\"https://x/1.4.0\"}]");

        Optional<DesktopUpdateService.UpdateInfo> info = service("1.5.0-beta", http).checkForUpdate();

        assertTrue(info.isPresent(), "beta.2 > beta ; le brouillon 1.6.0-beta est ignore");
        assertEquals("1.5.0-beta.2", info.get().latestVersion());
        assertEquals("https://x/b2", info.get().releaseUrl());
    }

    @Test
    void betaVersion_isToldWhenTheStableReleaseReplacesIt() throws Exception {
        RestTemplate http = mock(RestTemplate.class);
        reply(http, ALL, "[{\"tag_name\":\"v1.5.0\",\"html_url\":\"https://x/1.5.0\"},"
                + "{\"tag_name\":\"v1.5.0-beta\",\"prerelease\":true}]");

        Optional<DesktopUpdateService.UpdateInfo> info = service("1.5.0-beta", http).checkForUpdate();

        assertTrue(info.isPresent());
        assertEquals("1.5.0", info.get().latestVersion());
    }

    @Test
    void betaVersion_upToDate() throws Exception {
        RestTemplate http = mock(RestTemplate.class);
        reply(http, ALL, "[{\"tag_name\":\"v1.5.0-beta\",\"prerelease\":true},{\"tag_name\":\"v1.4.0\"}]");

        assertFalse(service("1.5.0-beta", http).checkForUpdate().isPresent());
    }

    @Test
    void compareVersions_ordersStableAfterBetaAndBetasByNumber() {
        assertTrue(DesktopUpdateService.compareVersions("1.5.0-beta", "1.5.0") < 0);
        assertTrue(DesktopUpdateService.compareVersions("1.5.0-beta", "1.5.0-beta.2") < 0);
        assertTrue(DesktopUpdateService.compareVersions("1.5.0-beta.2", "1.5.0-beta.10") < 0);
        assertTrue(DesktopUpdateService.compareVersions("1.6.0-beta", "1.5.0") > 0);
        assertEquals(0, DesktopUpdateService.compareVersions("1.5.0", "1.5.0"));
    }
}
