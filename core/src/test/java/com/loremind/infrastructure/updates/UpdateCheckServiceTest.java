package com.loremind.infrastructure.updates;

import com.loremind.application.licensing.LicenseService;
import com.loremind.infrastructure.updates.UpdateCheckService.ImageStatus;
import com.loremind.infrastructure.updates.UpdateCheckService.ImageStatusKind;
import com.loremind.infrastructure.updates.UpdateCheckService.TagsListResponse;
import com.loremind.infrastructure.updates.UpdateCheckService.UpdateStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests UpdateCheckService - approche semver (post-refactor v0.8.x).
 *
 * Couvre :
 *  - feature desactivee si WATCHTOWER_TOKEN absent
 *  - UP_TO_DATE quand version locale == max(tags remote)
 *  - UPDATE_AVAILABLE quand un tag plus eleve existe
 *  - UNKNOWN quand le registry echoue
 *  - UNKNOWN quand BuildProperties est absent (currentVersion = null)
 *  - parseSemver / findMaxSemver / compareSemver utilitaires
 */
public class UpdateCheckServiceTest {

    private static UpdateCheckService newService(String token, String currentVersion) {
        BuildProperties bp = null;
        if (currentVersion != null) {
            Properties p = new Properties();
            p.setProperty("version", currentVersion);
            bp = new BuildProperties(p);
        }
        // licenseService null : la beta est testee separement, ces tests
        // couvrent uniquement le canal stable.
        return new UpdateCheckService(
                new RestTemplateBuilder(),
                "ghcr.io",
                "igmlcreation/loremind-core,igmlcreation/loremind-brain",
                "http://watchtower:8080",
                token,
                "",
                null,
                bp
        );
    }

    private static RestTemplate stubHttp(UpdateCheckService svc) {
        RestTemplate http = mock(RestTemplate.class);
        ReflectionTestUtils.setField(svc, "http", http);
        return http;
    }

    private static void stubTags(RestTemplate http, String image, List<String> tags) {
        TagsListResponse body = new TagsListResponse();
        body.name = image;
        body.tags = tags;
        ResponseEntity<TagsListResponse> resp = new ResponseEntity<>(body, HttpStatus.OK);
        when(http.exchange(eq("https://ghcr.io/v2/" + image + "/tags/list"),
                eq(HttpMethod.GET), any(), eq(TagsListResponse.class)))
                .thenReturn(resp);
    }

    private static void stubTagsFailure(RestTemplate http, String image) {
        when(http.exchange(eq("https://ghcr.io/v2/" + image + "/tags/list"),
                eq(HttpMethod.GET), any(), eq(TagsListResponse.class)))
                .thenThrow(new RuntimeException("network down"));
    }

    // -----------------------------------------------------------------
    // Comportement du service
    // -----------------------------------------------------------------

    @Test
    void disabledWhenTokenMissing() {
        UpdateCheckService svc = newService("", "0.8.0");
        UpdateStatus status = svc.check();
        assertFalse(status.enabled());
        assertFalse(status.updateAvailable());
        assertFalse(status.anyUnknown());
        assertTrue(status.images().isEmpty());
    }

    @Test
    void upToDate_whenCurrentEqualsMaxRemote() {
        UpdateCheckService svc = newService("token", "0.8.0");
        RestTemplate http = stubHttp(svc);
        stubTags(http, "igmlcreation/loremind-core",
                List.of("0.7.0", "0.7.1", "0.7.2", "0.8.0", "latest"));
        stubTags(http, "igmlcreation/loremind-brain",
                List.of("0.7.0", "0.8.0", "latest"));

        UpdateStatus status = svc.check();

        assertTrue(status.enabled());
        assertFalse(status.updateAvailable());
        assertFalse(status.anyUnknown());
        assertEquals("0.8.0", status.currentVersion());
        for (ImageStatus img : status.images()) {
            assertEquals(ImageStatusKind.UP_TO_DATE, img.status());
            assertEquals("0.8.0", img.localVersion());
            assertEquals("0.8.0", img.remoteVersion());
            assertFalse(img.updateAvailable(), "back-compat bool");
        }
    }

    @Test
    void updateAvailable_whenRemoteHigher() {
        UpdateCheckService svc = newService("token", "0.7.2");
        RestTemplate http = stubHttp(svc);
        stubTags(http, "igmlcreation/loremind-core",
                List.of("0.7.0", "0.7.1", "0.7.2", "0.8.0", "latest"));
        stubTags(http, "igmlcreation/loremind-brain",
                List.of("0.7.2", "latest"));

        UpdateStatus status = svc.check();

        assertTrue(status.updateAvailable());
        assertFalse(status.anyUnknown());

        ImageStatus core = status.images().stream()
                .filter(i -> i.image().endsWith("core")).findFirst().orElseThrow();
        assertEquals(ImageStatusKind.UPDATE_AVAILABLE, core.status());
        assertEquals("0.7.2", core.localVersion());
        assertEquals("0.8.0", core.remoteVersion());
        assertTrue(core.updateAvailable(), "back-compat bool");

        ImageStatus brain = status.images().stream()
                .filter(i -> i.image().endsWith("brain")).findFirst().orElseThrow();
        assertEquals(ImageStatusKind.UP_TO_DATE, brain.status());
    }

    @Test
    void unknown_whenRegistryFails() {
        UpdateCheckService svc = newService("token", "0.8.0");
        RestTemplate http = stubHttp(svc);
        stubTagsFailure(http, "igmlcreation/loremind-core");
        stubTags(http, "igmlcreation/loremind-brain", List.of("0.8.0"));

        UpdateStatus status = svc.check();

        assertTrue(status.anyUnknown());
        ImageStatus core = status.images().stream()
                .filter(i -> i.image().endsWith("core")).findFirst().orElseThrow();
        assertEquals(ImageStatusKind.UNKNOWN, core.status());
        assertNull(core.remoteVersion());
        assertEquals("0.8.0", core.localVersion());
    }

    @Test
    void unknown_whenNoValidSemverTags() {
        UpdateCheckService svc = newService("token", "0.8.0");
        RestTemplate http = stubHttp(svc);
        stubTags(http, "igmlcreation/loremind-core", List.of("latest", "stable", "main"));
        stubTags(http, "igmlcreation/loremind-brain", List.of("0.8.0"));

        UpdateStatus status = svc.check();

        assertTrue(status.anyUnknown());
        ImageStatus core = status.images().stream()
                .filter(i -> i.image().endsWith("core")).findFirst().orElseThrow();
        assertEquals(ImageStatusKind.UNKNOWN, core.status());
        assertNull(core.remoteVersion());
    }

    @Test
    void unknown_whenBuildPropertiesAbsent() {
        // INVARIANT : pas de version courante => tout UNKNOWN, jamais "a jour"
        // par defaut. Evite de declarer "a jour" un build dev sans build-info.
        UpdateCheckService svc = newService("token", null);
        RestTemplate http = stubHttp(svc);
        // Meme si on stub des tags, le service doit bypass et renvoyer UNKNOWN
        stubTags(http, "igmlcreation/loremind-core", List.of("0.8.0"));

        UpdateStatus status = svc.check();

        assertTrue(status.enabled());
        assertFalse(status.updateAvailable());
        assertTrue(status.anyUnknown());
        assertNull(status.currentVersion());
        for (ImageStatus img : status.images()) {
            assertEquals(ImageStatusKind.UNKNOWN, img.status());
        }
    }

    // -----------------------------------------------------------------
    // Utilitaires semver
    // -----------------------------------------------------------------

    @Test
    void parseSemver_acceptsCommonFormats() {
        assertArrayEquals(new int[]{0, 8, 0}, UpdateCheckService.parseSemver("0.8.0"));
        assertArrayEquals(new int[]{0, 8, 0}, UpdateCheckService.parseSemver("v0.8.0"));
        assertArrayEquals(new int[]{1, 0, 0}, UpdateCheckService.parseSemver("1.0.0"));
        assertArrayEquals(new int[]{0, 8, 0}, UpdateCheckService.parseSemver("0.8.0-beta.1"));
        assertArrayEquals(new int[]{0, 8, 0}, UpdateCheckService.parseSemver("0.8.0+build.42"));
    }

    @Test
    void parseSemver_rejectsInvalid() {
        assertNull(UpdateCheckService.parseSemver(null));
        assertNull(UpdateCheckService.parseSemver(""));
        assertNull(UpdateCheckService.parseSemver("latest"));
        assertNull(UpdateCheckService.parseSemver("stable"));
        assertNull(UpdateCheckService.parseSemver("0.8.0.1.2"));
        assertNull(UpdateCheckService.parseSemver("0.x.0"));
    }

    @Test
    void compareSemver_basic() {
        assertTrue(UpdateCheckService.compareSemver("0.7.2", "0.8.0") < 0);
        assertTrue(UpdateCheckService.compareSemver("0.8.0", "0.7.2") > 0);
        assertEquals(0, UpdateCheckService.compareSemver("0.8.0", "0.8.0"));
        assertEquals(0, UpdateCheckService.compareSemver("v0.8.0", "0.8.0"));
        assertTrue(UpdateCheckService.compareSemver("0.8.0", "0.10.0") < 0);
        assertTrue(UpdateCheckService.compareSemver("1.0.0", "0.99.99") > 0);
    }

    @Test
    void findMaxSemver_picksHighest() {
        assertEquals("0.8.0", UpdateCheckService.findMaxSemver(
                List.of("0.7.0", "0.7.1", "0.7.2", "0.8.0", "latest")));
        assertEquals("0.10.0", UpdateCheckService.findMaxSemver(
                List.of("0.8.0", "0.10.0", "0.9.5")));
        assertEquals("v1.0.0", UpdateCheckService.findMaxSemver(
                List.of("v0.8.0", "v1.0.0", "latest")));
    }

    @Test
    void findMaxSemver_returnsNullWhenNoValidTag() {
        assertNull(UpdateCheckService.findMaxSemver(List.of("latest", "stable", "main")));
        assertNull(UpdateCheckService.findMaxSemver(List.of()));
    }
}
