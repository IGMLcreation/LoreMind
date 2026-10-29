package com.loremind.infrastructure.updates;

import com.loremind.application.channel.ChannelSwitcherService;
import com.loremind.infrastructure.updates.UpdateCheckService.ImageStatus;
import com.loremind.infrastructure.updates.UpdateCheckService.ImageStatusKind;
import com.loremind.infrastructure.updates.UpdateCheckService.TagsListResponse;
import com.loremind.infrastructure.updates.UpdateCheckService.UpdateStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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
class UpdateCheckServiceTest {

    private static UpdateCheckService newService(String token, String currentVersion) {
        BuildProperties bp = null;
        if (currentVersion != null) {
            Properties p = new Properties();
            p.setProperty("version", currentVersion);
            bp = new BuildProperties(p);
        }
        return newService(token, bp, ChannelSwitcherService.Channel.STABLE);
    }

    private static UpdateCheckService newService(String token, BuildProperties bp,
                                                 ChannelSwitcherService.Channel channel) {
        ChannelSwitcherService channels = mock(ChannelSwitcherService.class);
        when(channels.getCurrentChannel()).thenReturn(channel);
        return new UpdateCheckService(
                new RestTemplateBuilder(),
                "ghcr.io",
                "igmlcreation/loremind-core,igmlcreation/loremind-brain",
                "http://watchtower:8080",
                token,
                "ghcr.io",
                "igmlcreation/loremind-beta-core",
                channels,
                bp
        );
    }

    private static BuildProperties version(String v) {
        Properties p = new Properties();
        p.setProperty("version", v);
        return new BuildProperties(p);
    }

    // --- Canal beta (libre : images publiques, lecture anonyme) ------------------

    @Test
    void checkBeta_disabledWhenNotOnBetaChannel() {
        UpdateCheckService svc = newService("tok", version("1.4.0"), ChannelSwitcherService.Channel.STABLE);
        RestTemplate http = stubHttp(svc);

        UpdateCheckService.BetaStatus status = svc.checkBeta();

        assertFalse(status.enabled());
        assertEquals("not-on-beta-channel", status.disabledReason());
        verifyNoInteractions(http);
    }

    @Test
    void checkBeta_readsPublicBetaImagesWithoutCredentials() {
        UpdateCheckService svc = newService("tok", version("1.5.0-beta"), ChannelSwitcherService.Channel.BETA);
        RestTemplate http = stubHttp(svc);
        stubTags(http, "igmlcreation/loremind-beta-core", List.of("beta", "1.5.0-beta", "1.6.0-beta"));

        UpdateCheckService.BetaStatus status = svc.checkBeta();

        assertTrue(status.enabled());
        assertTrue(status.updateAvailable());
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<HttpEntity> sent = ArgumentCaptor.forClass(HttpEntity.class);
        verify(http).exchange(eq("https://ghcr.io/v2/igmlcreation/loremind-beta-core/tags/list"),
                eq(HttpMethod.GET), sent.capture(), eq(TagsListResponse.class));
        assertFalse(sent.getValue().getHeaders().containsKey(HttpHeaders.AUTHORIZATION),
                "plus d'identifiants : les images beta sont publiques");
    }

    private static RestTemplate stubHttp(UpdateCheckService svc) {
        RestTemplate http = mock(RestTemplate.class);
        ReflectionTestUtils.setField(svc, "http", http);
        return http;
    }

    private static void stubTags(RestTemplate http, String image, List<String> tags) {
        TagsListResponse body = new TagsListResponse(image, tags);
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

    // NB : les utilitaires semver (parseSemver / findMaxSemver / compareSemver) sont
    // désormais dans SemverComparator et testés par SemverComparatorTest.
}
