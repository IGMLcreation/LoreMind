package com.loremind.infrastructure.updates;

import com.loremind.infrastructure.updates.UpdateCheckService.ImageStatus;
import com.loremind.infrastructure.updates.UpdateCheckService.ImageStatusKind;
import com.loremind.infrastructure.updates.UpdateCheckService.UpdateStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Test unitaire pour UpdateCheckService.
 *
 * Couvre les invariants critiques de la detection de MAJ :
 *  - feature desactivee si token absent
 *  - status UP_TO_DATE quand baseline == remote
 *  - status UPDATE_AVAILABLE quand baseline != remote
 *  - status UNKNOWN quand baseline manque (PAS d'alignement lazy — invariant
 *    central, regression historique)
 *  - status UNKNOWN quand remote impossible a fetcher
 *  - drapeaux top-level updateAvailable / anyUnknown coherents
 *  - back-compat : champ updateAvailable sur ImageStatus = (status == UPDATE_AVAILABLE)
 */
public class UpdateCheckServiceTest {

    private static UpdateCheckService newService(String token) {
        // licensing.* params left empty + LicenseService null : la feature beta est
        // desactivee dans ces tests, qui couvrent uniquement le canal stable.
        return new UpdateCheckService(
                new RestTemplateBuilder(),
                "ghcr.io",
                "igmlcreation/loremind-core,igmlcreation/loremind-brain",
                "latest",
                "http://watchtower:8080",
                token,
                "",
                "latest",
                null
        );
    }

    /**
     * Injecte un RestTemplate moque dans le service deja construit, et pose
     * directement les baselines pour eviter les vrais appels HTTP.
     */
    @SuppressWarnings("unchecked")
    private static void setBaselines(UpdateCheckService svc, Map<String, String> baselines) {
        ((Map<String, String>) ReflectionTestUtils.getField(svc, "baselineDigests")).putAll(baselines);
    }

    private static RestTemplate stubHttp(UpdateCheckService svc) {
        RestTemplate http = mock(RestTemplate.class);
        ReflectionTestUtils.setField(svc, "http", http);
        return http;
    }

    private static void stubRemoteDigest(RestTemplate http, String image, String digest) {
        HttpHeaders headers = new HttpHeaders();
        if (digest != null) headers.add("Docker-Content-Digest", digest);
        ResponseEntity<Void> resp = new ResponseEntity<>(headers, org.springframework.http.HttpStatus.OK);
        when(http.exchange(eq("https://ghcr.io/v2/" + image + "/manifests/latest"),
                eq(org.springframework.http.HttpMethod.HEAD), any(), eq(Void.class)))
                .thenReturn(resp);
    }

    private static void stubRemoteFailure(RestTemplate http, String image) {
        when(http.exchange(eq("https://ghcr.io/v2/" + image + "/manifests/latest"),
                eq(org.springframework.http.HttpMethod.HEAD), any(), eq(Void.class)))
                .thenThrow(new RuntimeException("network down"));
    }

    @Test
    void disabledWhenTokenMissing() {
        UpdateCheckService svc = newService("");
        UpdateStatus status = svc.check();
        assertFalse(status.enabled());
        assertFalse(status.updateAvailable());
        assertFalse(status.anyUnknown());
        assertTrue(status.images().isEmpty());
    }

    @Test
    void upToDate_whenBaselineEqualsRemote() {
        UpdateCheckService svc = newService("token");
        ReflectionTestUtils.setField(svc, "baselineDigests", new ConcurrentHashMap<>());
        setBaselines(svc, Map.of(
                "igmlcreation/loremind-core", "sha256:aaa",
                "igmlcreation/loremind-brain", "sha256:bbb"
        ));
        RestTemplate http = stubHttp(svc);
        stubRemoteDigest(http, "igmlcreation/loremind-core", "sha256:aaa");
        stubRemoteDigest(http, "igmlcreation/loremind-brain", "sha256:bbb");

        UpdateStatus status = svc.check();

        assertTrue(status.enabled());
        assertFalse(status.updateAvailable());
        assertFalse(status.anyUnknown());
        for (ImageStatus img : status.images()) {
            assertEquals(ImageStatusKind.UP_TO_DATE, img.status());
            assertFalse(img.updateAvailable(), "back-compat bool");
        }
    }

    @Test
    void updateAvailable_whenRemoteDiffers() {
        UpdateCheckService svc = newService("token");
        ReflectionTestUtils.setField(svc, "baselineDigests", new ConcurrentHashMap<>());
        setBaselines(svc, Map.of(
                "igmlcreation/loremind-core", "sha256:OLD",
                "igmlcreation/loremind-brain", "sha256:bbb"
        ));
        RestTemplate http = stubHttp(svc);
        stubRemoteDigest(http, "igmlcreation/loremind-core", "sha256:NEW");
        stubRemoteDigest(http, "igmlcreation/loremind-brain", "sha256:bbb");

        UpdateStatus status = svc.check();

        assertTrue(status.updateAvailable());
        assertFalse(status.anyUnknown());
        ImageStatus core = status.images().stream()
                .filter(i -> i.image().endsWith("core")).findFirst().orElseThrow();
        assertEquals(ImageStatusKind.UPDATE_AVAILABLE, core.status());
        assertTrue(core.updateAvailable(), "back-compat bool");
        ImageStatus brain = status.images().stream()
                .filter(i -> i.image().endsWith("brain")).findFirst().orElseThrow();
        assertEquals(ImageStatusKind.UP_TO_DATE, brain.status());
    }

    @Test
    void unknown_whenBaselineMissing_DOES_NOT_lazyAlign() {
        // INVARIANT CENTRAL : si la baseline est absente (echec init au boot),
        // on NE DOIT PAS aligner lazy sur le remote courant — sinon une MAJ
        // pousse APRES le boot serait declaree "a jour" silencieusement.
        UpdateCheckService svc = newService("token");
        ReflectionTestUtils.setField(svc, "baselineDigests", new ConcurrentHashMap<>());
        // baseline DELIBEREMENT vide
        RestTemplate http = stubHttp(svc);
        stubRemoteDigest(http, "igmlcreation/loremind-core", "sha256:remote-now");
        stubRemoteDigest(http, "igmlcreation/loremind-brain", "sha256:remote-now-2");

        UpdateStatus status = svc.check();

        assertTrue(status.enabled());
        assertFalse(status.updateAvailable());
        assertTrue(status.anyUnknown());
        for (ImageStatus img : status.images()) {
            assertEquals(ImageStatusKind.UNKNOWN, img.status());
            assertNull(img.localDigest());
            assertNotNull(img.remoteDigest()); // remote OK, baseline manquante
        }

        // VERIFICATION CRITIQUE : la baseline ne doit PAS avoir ete posee.
        @SuppressWarnings("unchecked")
        Map<String, String> baselines = (Map<String, String>) ReflectionTestUtils.getField(svc, "baselineDigests");
        assertTrue(baselines.isEmpty(),
                "check() ne doit JAMAIS aligner lazy la baseline sur le remote — "
              + "regression de bug historique (faux negatif silencieux).");
    }

    @Test
    void unknown_whenRemoteFetchFails() {
        UpdateCheckService svc = newService("token");
        ReflectionTestUtils.setField(svc, "baselineDigests", new ConcurrentHashMap<>());
        setBaselines(svc, Map.of("igmlcreation/loremind-core", "sha256:aaa",
                                 "igmlcreation/loremind-brain", "sha256:bbb"));
        RestTemplate http = stubHttp(svc);
        stubRemoteFailure(http, "igmlcreation/loremind-core");
        stubRemoteDigest(http, "igmlcreation/loremind-brain", "sha256:bbb");

        UpdateStatus status = svc.check();

        assertFalse(status.updateAvailable());
        assertTrue(status.anyUnknown());
        ImageStatus core = status.images().stream()
                .filter(i -> i.image().endsWith("core")).findFirst().orElseThrow();
        assertEquals(ImageStatusKind.UNKNOWN, core.status());
        assertNull(core.remoteDigest());
        assertEquals("sha256:aaa", core.localDigest()); // baseline preservee
    }

    @Test
    void mixedStatuses_anyUnknownAndAnyUpdateBothTrue() {
        UpdateCheckService svc = newService("token");
        ReflectionTestUtils.setField(svc, "baselineDigests", new ConcurrentHashMap<>());
        setBaselines(svc, Map.of("igmlcreation/loremind-core", "sha256:OLD"));
        // brain n'a pas de baseline -> UNKNOWN
        RestTemplate http = stubHttp(svc);
        stubRemoteDigest(http, "igmlcreation/loremind-core", "sha256:NEW");
        stubRemoteFailure(http, "igmlcreation/loremind-brain");

        UpdateStatus status = svc.check();

        assertTrue(status.updateAvailable(), "core a une MAJ disponible");
        assertTrue(status.anyUnknown(), "brain est UNKNOWN");
    }
}
