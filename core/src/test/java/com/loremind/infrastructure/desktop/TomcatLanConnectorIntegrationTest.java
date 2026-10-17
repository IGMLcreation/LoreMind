package com.loremind.infrastructure.desktop;

import com.loremind.application.remoteaccess.RemoteAccessService;
import com.loremind.domain.remoteaccess.RemoteAccessConfig;
import com.loremind.domain.remoteaccess.RemoteDevice;
import com.loremind.domain.remoteaccess.ports.LanConnector;
import com.loremind.domain.remoteaccess.ports.RemoteAccessConfigRepository;
import com.loremind.domain.remoteaccess.ports.RemoteDeviceRepository;
import com.loremind.infrastructure.web.config.RemoteAccessFilter;
import com.loremind.infrastructure.web.controller.RemotePairingController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test d'intégration de l'accès distant sur un VRAI Tomcat : ouverture à chaud du second
 * connecteur, garde du filtre, appairage par code avec pose du cookie, puis fermeture.
 * Contexte Spring minimal (ni base, ni sécurité) : seules les briques de l'accès distant.
 */
@SpringBootTest(classes = TomcatLanConnectorIntegrationTest.App.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.banner-mode=off")
class TomcatLanConnectorIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class, FlywayAutoConfiguration.class,
            SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
    @Import(App.Ping.class)
    static class App {

        @RestController
        static class Ping {
            @GetMapping("/api/ping")
            String ping() { return "pong"; }

            @GetMapping("/api/settings/secret")
            String secret() { return "admin"; }
        }

        @Bean
        TomcatLanConnector lanConnector(ApplicationContext context) {
            return new TomcatLanConnector(context);
        }

        @Bean
        RemoteAccessService remoteAccessService(LanConnector lan) {
            return new RemoteAccessService(new InMemoryConfig(), new InMemoryDevices(), lan, 1);
        }

        /** En @Bean : un @Import ignorerait la classe, annotée @Profile("local"). */
        @Bean
        RemotePairingController remotePairingController(RemoteAccessService service) {
            return new RemotePairingController(service);
        }

        @Bean
        FilterRegistrationBean<RemoteAccessFilter> remoteAccessFilter(RemoteAccessService service) {
            return new FilterRegistrationBean<>(new RemoteAccessFilter(service));
        }
    }

    @LocalServerPort private int localPort;
    @Autowired private RemoteAccessService service;
    @Autowired private TomcatLanConnector lan;

    private final HttpClient http = HttpClient.newHttpClient();

    @AfterEach
    void closeLan() {
        lan.stop();
    }

    private HttpResponse<String> get(int port, String path, String cookie) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
        if (cookie != null) req.header("Cookie", cookie);
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(int port, String path, String json) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    @Test
    void lanConnector_guardsApi_pairsWithCode_andClosesCleanly() throws Exception {
        // Connecteur habituel : inchangé, aucun jeton demandé.
        assertEquals(200, get(localPort, "/api/ping", null).statusCode());

        int lanPort = freePort();
        assertTrue(service.configure(true, lanPort).running());

        // Depuis le réseau : appairage exigé, administration interdite.
        HttpResponse<String> unpaired = get(lanPort, "/api/ping", null);
        assertEquals(401, unpaired.statusCode());
        assertTrue(unpaired.body().contains("remote_pairing_required"));
        assertEquals(403, get(lanPort, "/api/settings/secret", null).statusCode());

        // Mauvais code puis bon code : le bon pose un cookie HttpOnly + SameSite=Strict.
        assertEquals(403, post(lanPort, "/api/remote-access/pair", "{\"code\":\"abcdef\",\"deviceName\":\"x\"}").statusCode());
        String code = service.newPairingCode().code();
        HttpResponse<String> paired = post(lanPort, "/api/remote-access/pair",
                "{\"code\":\"" + code + "\",\"deviceName\":\"Tablette de test\"}");
        assertEquals(200, paired.statusCode());
        String setCookie = paired.headers().firstValue("Set-Cookie").orElseThrow();
        assertTrue(setCookie.startsWith(RemoteAccessFilter.DEVICE_COOKIE + "="));
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("SameSite=Strict"));
        String cookie = setCookie.split(";", 2)[0];

        HttpResponse<String> authorized = get(lanPort, "/api/ping", cookie);
        assertEquals(200, authorized.statusCode());
        assertEquals("pong", authorized.body());
        assertEquals(403, get(lanPort, "/api/settings/secret", cookie).statusCode());
        assertTrue(get(lanPort, "/api/remote-access/session", cookie).body().contains("\"paired\":true"));

        // Désactivation : le port réseau se ferme, le connecteur local continue.
        service.configure(false, lanPort);
        assertThrows(ConnectException.class, () -> get(lanPort, "/api/ping", cookie));
        assertEquals(200, get(localPort, "/api/ping", null).statusCode());
    }

    @Test
    void busyPort_isReportedClearly() throws Exception {
        try (ServerSocket occupied = new ServerSocket(0)) {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> service.configure(true, occupied.getLocalPort()));
            assertTrue(e.getMessage().contains("déjà utilisé"));
        }
    }

    // --- Doublures de persistance ---

    static final class InMemoryConfig implements RemoteAccessConfigRepository {
        private RemoteAccessConfig saved;

        @Override public Optional<RemoteAccessConfig> find() { return Optional.ofNullable(saved); }
        @Override public RemoteAccessConfig save(RemoteAccessConfig c) { saved = c; return c; }
    }

    static final class InMemoryDevices implements RemoteDeviceRepository {
        private final List<RemoteDevice> items = new ArrayList<>();

        @Override public synchronized RemoteDevice save(RemoteDevice d) {
            d.setId(String.valueOf(items.size() + 1));
            items.add(d);
            return d;
        }
        @Override public synchronized Optional<RemoteDevice> findByTokenHash(String h) {
            return items.stream().filter(d -> d.getTokenHash().equals(h)).findFirst();
        }
        @Override public synchronized List<RemoteDevice> findAll() { return List.copyOf(items); }
        @Override public void touch(String id, Instant at) { /* sans objet ici */ }
        @Override public synchronized boolean deleteById(String id) { return items.removeIf(d -> d.getId().equals(id)); }
    }
}
