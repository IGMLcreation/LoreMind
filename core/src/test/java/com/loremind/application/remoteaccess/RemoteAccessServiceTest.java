package com.loremind.application.remoteaccess;

import com.loremind.domain.remoteaccess.RemoteAccessConfig;
import com.loremind.domain.remoteaccess.RemoteDevice;
import com.loremind.domain.remoteaccess.ports.LanConnector;
import com.loremind.domain.remoteaccess.ports.RemoteAccessConfigRepository;
import com.loremind.domain.remoteaccess.ports.RemoteDeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test unitaire de {@link RemoteAccessService} avec des doublures en mémoire (pas de Mockito :
 * l'état des appareils et du connecteur doit vraiment évoluer d'un appel à l'autre).
 */
class RemoteAccessServiceTest {

    private static final int LOCAL_PORT = 8080;

    private MutableClock clock;
    private FakeLan lan;
    private InMemoryDevices devices;
    private InMemoryConfig config;
    private RemoteAccessService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-17T10:00:00Z"));
        lan = new FakeLan();
        devices = new InMemoryDevices();
        config = new InMemoryConfig();
        service = new RemoteAccessService(config, devices, lan, LOCAL_PORT, clock);
    }

    private String freshCode() {
        service.configure(true, 8765);
        return service.newPairingCode().code();
    }

    // --- Réglage ---

    @Test
    void disabledByDefault_noConnector() {
        RemoteAccessStatus status = service.status();
        assertFalse(status.enabled());
        assertFalse(status.running());
        assertEquals(RemoteAccessConfig.DEFAULT_PORT, status.port());
        assertTrue(status.urls().isEmpty());
    }

    @Test
    void enable_opensConnector_persists_andListsUrls() {
        RemoteAccessStatus status = service.configure(true, 8765);

        assertEquals(8765, lan.port);
        assertTrue(status.running());
        assertEquals(List.of("http://192.168.1.20:8765"), status.urls());
        assertEquals(new RemoteAccessConfig(true, 8765), config.saved);
    }

    @Test
    void enable_onBusyPort_staysDisabled() {
        lan.busy = true;
        assertThrows(IllegalStateException.class, () -> service.configure(true, 8765));
        assertNull(config.saved);
        assertFalse(service.status().enabled());
    }

    @Test
    void rejects_portOutOfRange_orSameAsLocalPort() {
        assertThrows(IllegalArgumentException.class, () -> service.configure(true, 80));
        assertThrows(IllegalArgumentException.class, () -> service.configure(true, LOCAL_PORT));
    }

    @Test
    void disable_closesConnector_andInvalidatesPendingCode() {
        String code = freshCode();
        service.configure(false, 8765);

        assertEquals(-1, lan.port);
        assertThrows(PairingRejectedException.class, () -> service.pair(code, "Tablette", null));
    }

    @Test
    void applySavedConfig_reopensOnBoot_withoutCrashingOnBusyPort() {
        config.saved = new RemoteAccessConfig(true, 8765);
        lan.busy = true;

        service.applySavedConfig();

        assertEquals(-1, lan.port);
        assertNotNull(service.status().lastError());
    }

    // --- Appairage ---

    @Test
    void pairingCode_requiresOpenConnector() {
        assertThrows(IllegalStateException.class, () -> service.newPairingCode());
    }

    @Test
    void pairingCode_isSixDigits() {
        assertTrue(freshCode().matches("\\d{6}"));
    }

    @Test
    void pair_withRightCode_issuesToken_storesOnlyItsHash_andConsumesTheCode() {
        String code = freshCode();

        PairingResult result = service.pair(code, "  Tablette de Léa ", "Mozilla/5.0 (iPad)");

        assertEquals("Tablette de Léa", result.device().name());
        RemoteDevice stored = devices.items.get(0);
        assertNotEquals(result.token(), stored.getTokenHash());
        assertEquals(RemoteAccessService.sha256(result.token()), stored.getTokenHash());
        assertTrue(service.authenticate(result.token()).isPresent());
        // Code à usage unique.
        assertThrows(PairingRejectedException.class, () -> service.pair(code, "Autre", null));
    }

    @Test
    void pair_rejectsExpiredCode() {
        String code = freshCode();
        clock.advance(RemoteAccessService.CODE_TTL.plusSeconds(1));
        assertThrows(PairingRejectedException.class, () -> service.pair(code, "Tablette", null));
        assertTrue(devices.items.isEmpty());
    }

    @Test
    void pair_invalidatesCodeAfterTooManyWrongAttempts() {
        String code = freshCode();
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < RemoteAccessService.CODE_MAX_ATTEMPTS; i++) {
            assertThrows(PairingRejectedException.class, () -> service.pair(wrong, "Tablette", null));
        }
        // Même le bon code ne passe plus : il faut en afficher un nouveau.
        assertThrows(PairingRejectedException.class, () -> service.pair(code, "Tablette", null));
    }

    @Test
    void pair_globalThrottle_blocksBurstAcrossCodes() {
        for (int i = 0; i < RemoteAccessService.GLOBAL_MAX_FAILURES_PER_MINUTE; i++) {
            assertThrows(PairingRejectedException.class, () -> service.pair("123456", "x", null));
        }
        String code = freshCode();
        PairingRejectedException blocked =
                assertThrows(PairingRejectedException.class, () -> service.pair(code, "Tablette", null));
        assertTrue(blocked.getMessage().contains("minute"));

        clock.advance(Duration.ofMinutes(2));
        assertDoesNotThrow(() -> service.pair(code, "Tablette", null));
    }

    @Test
    void deviceName_defaultsAndIsTruncated() {
        PairingResult unnamed = service.pair(freshCode(), "  ", null);
        assertEquals("Appareil", unnamed.device().name());

        PairingResult longName = service.pair(service.newPairingCode().code(), "x".repeat(300), null);
        assertEquals(RemoteAccessService.DEVICE_NAME_MAX, longName.device().name().length());
    }

    // --- Authentification & révocation ---

    @Test
    void authenticate_unknownOrBlankToken_isEmpty() {
        assertTrue(service.authenticate(null).isEmpty());
        assertTrue(service.authenticate("").isEmpty());
        assertTrue(service.authenticate("inconnu").isEmpty());
    }

    @Test
    void authenticate_touchesLastSeen_atMostOncePerMinute() {
        String token = service.pair(freshCode(), "Tablette", null).token();
        devices.touches = 0;

        service.authenticate(token);
        assertEquals(0, devices.touches);

        clock.advance(Duration.ofMinutes(2));
        service.authenticate(token);
        assertEquals(1, devices.touches);
    }

    @Test
    void revoke_cutsAccess() {
        PairingResult result = service.pair(freshCode(), "Tablette", null);

        assertTrue(service.revoke(result.device().id()));
        assertTrue(service.authenticate(result.token()).isEmpty());
        assertFalse(service.revoke(result.device().id()));
    }

    // --- Doublures ---

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) { this.now = now; }

        void advance(Duration d) { now = now.plus(d); }

        @Override public Instant instant() { return now; }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
    }

    private static final class FakeLan implements LanConnector {
        int port = -1;
        boolean busy;

        @Override public void start(int p) {
            if (busy) throw new IllegalStateException("Le port " + p + " est déjà utilisé par une autre application.");
            port = p;
        }
        @Override public void stop() { port = -1; }
        @Override public int runningPort() { return port; }
        @Override public List<String> lanAddresses() { return List.of("192.168.1.20"); }
    }

    private static final class InMemoryConfig implements RemoteAccessConfigRepository {
        RemoteAccessConfig saved;

        @Override public Optional<RemoteAccessConfig> find() { return Optional.ofNullable(saved); }
        @Override public RemoteAccessConfig save(RemoteAccessConfig c) { saved = c; return c; }
    }

    private static final class InMemoryDevices implements RemoteDeviceRepository {
        final List<RemoteDevice> items = new ArrayList<>();
        int touches;
        private int nextId = 1;

        @Override public RemoteDevice save(RemoteDevice d) {
            d.setId(String.valueOf(nextId++));
            items.add(d);
            return d;
        }
        @Override public Optional<RemoteDevice> findByTokenHash(String h) {
            return items.stream().filter(d -> d.getTokenHash().equals(h)).findFirst();
        }
        @Override public List<RemoteDevice> findAll() { return List.copyOf(items); }
        @Override public void touch(String id, Instant at) {
            touches++;
            items.stream().filter(d -> d.getId().equals(id)).forEach(d -> d.setLastSeenAt(at));
        }
        @Override public boolean deleteById(String id) { return items.removeIf(d -> d.getId().equals(id)); }
    }
}
