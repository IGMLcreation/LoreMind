package com.loremind.infrastructure.web.controller;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Couvre le pull streamé d'un modèle Ollama de {@link SettingsController}
 * (POST /api/settings/models/ollama/pull). Cet endpoint BYPASS le RestTemplate
 * (donc {@link SettingsControllerTest} ne peut pas le couvrir via MockRestServiceServer) :
 * il ouvre un {@code java.net.http.HttpClient} directement vers le Brain et relaie
 * le NDJSON chunk par chunk.
 * <p>
 * On démarre donc un vrai mini serveur HTTP local (JDK {@link HttpServer}) qui imite
 * la réponse NDJSON du Brain, et on pointe {@code brain.base-url} dessus via
 * {@link DynamicPropertySource}. Cela exerce tout le corps streamé : sérialisation
 * {@code toJson} du body, envoi HTTP/1.1, en-tête d'auth interne, boucle de lecture/relai.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SettingsControllerPullTest {

    /** Identifiants ADMIN (cf. src/test/resources/application.properties) — endpoint sous /api/settings/**. */
    private static final String ADMIN_AUTH = "Basic " + Base64.getEncoder()
            .encodeToString("test-admin:test-admin-password".getBytes(StandardCharsets.UTF_8));

    private static HttpServer stubBrain;

    @Autowired private MockMvc mockMvc;

    @DynamicPropertySource
    static void brainStub(DynamicPropertyRegistry registry) throws IOException {
        stubBrain = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stubBrain.createContext("/models/ollama/pull", exchange -> {
            // On consomme la requête (le body JSON sérialisé par le controller) puis on
            // renvoie un flux NDJSON de progression, comme le ferait le Brain/Ollama.
            exchange.getRequestBody().readAllBytes();
            byte[] body = ("{\"status\":\"pulling manifest\"}\n"
                    + "{\"status\":\"success\"}\n").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/x-ndjson");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        stubBrain.start();
        int port = stubBrain.getAddress().getPort();
        registry.add("brain.base-url", () -> "http://127.0.0.1:" + port);
    }

    @AfterAll
    static void stopStub() {
        if (stubBrain != null) {
            stubBrain.stop(0);
        }
    }

    @Test
    void pullOllamaModel_streamsBrainNdjson() throws Exception {
        // Body avec des valeurs de types variés (String/Number/Boolean/null) pour
        // exercer toutes les branches du sérialiseur maison toJson(...).
        String body = "{\"name\":\"llama3\",\"n\":3,\"insecure\":true,\"opt\":null}";

        MvcResult result = mockMvc.perform(post("/api/settings/models/ollama/pull")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("pulling manifest")))
                .andExpect(content().string(containsString("success")));
    }
}
