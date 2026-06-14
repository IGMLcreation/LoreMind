package com.loremind.infrastructure.web.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'integration pour {@link SettingsController} (proxy fin vers le Brain).
 * <p>
 * Le {@link RestTemplate} (@Primary {@code brainRestTemplate}, celui injecte dans
 * le controller) est instrumente avec {@link MockRestServiceServer} : on intercepte
 * les appels sortants vers le Brain et on renvoie des reponses canned, sans reseau.
 * Cela verifie le contrat de proxy (methode, chemin, body relaye, reponse propagee).
 * <p>
 * {@code /api/settings/**} exige le role ADMIN (HTTP Basic) : chaque requete porte
 * donc l'entete d'auth construite a partir des identifiants de test. Le blocage
 * {@code app.demo-mode} est couvert par {@link SettingsControllerDemoModeTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SettingsControllerTest {

    /** Identifiants definis dans src/test/resources/application.properties. */
    private static final String ADMIN_AUTH = "Basic " + Base64.getEncoder()
            .encodeToString("test-admin:test-admin-password".getBytes(StandardCharsets.UTF_8));

    @Autowired private MockMvc mockMvc;
    @Autowired private RestTemplate restTemplate;

    private MockRestServiceServer brain;

    @BeforeEach
    void setUp() {
        brain = MockRestServiceServer.createServer(restTemplate);
    }

    @Test
    void getSettings_forwardsToBrain_andReturnsBody() throws Exception {
        brain.expect(requestTo(endsWith("/settings")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"theme\":\"dark\"}", MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/settings").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("dark"));
        brain.verify();
    }

    @Test
    void updateSettings_forwardsPatchBody() throws Exception {
        brain.expect(requestTo(endsWith("/settings")))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("{\"theme\":\"light\"}"))
                .andRespond(withSuccess("{\"theme\":\"light\"}", MediaType.APPLICATION_JSON));

        mockMvc.perform(put("/api/settings")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\":\"light\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("light"));
        brain.verify();
    }

    @Test
    void listOllamaModels_forwards() throws Exception {
        brain.expect(requestTo(endsWith("/models/ollama")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"models\":[]}", MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/settings/models/ollama").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models").isArray());
        brain.verify();
    }

    @Test
    void getOllamaModelInfo_forwardsPostBody() throws Exception {
        brain.expect(requestTo(endsWith("/models/ollama/info")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"name\":\"llama3\"}"))
                .andRespond(withSuccess("{\"size\":123}", MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/api/settings/models/ollama/info")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"llama3\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(123));
        brain.verify();
    }

    @Test
    void deleteOllamaModel_forwardsWithNameInPath() throws Exception {
        brain.expect(requestTo(endsWith("/models/ollama/llama3")))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess("{\"deleted\":true}", MediaType.APPLICATION_JSON));

        mockMvc.perform(delete("/api/settings/models/ollama/{name}", "llama3")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));
        brain.verify();
    }

    @Test
    void listOneMinModels_forwards() throws Exception {
        brain.expect(requestTo(endsWith("/models/onemin")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"models\":[]}", MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/settings/models/onemin").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH))
                .andExpect(status().isOk());
        brain.verify();
    }

    @Test
    void listOpenRouterModels_forwards() throws Exception {
        brain.expect(requestTo(endsWith("/models/openrouter")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"models\":[]}", MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/settings/models/openrouter").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH))
                .andExpect(status().isOk());
        brain.verify();
    }

    @Test
    void listMistralModels_forwards() throws Exception {
        brain.expect(requestTo(endsWith("/models/mistral")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"models\":[]}", MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/settings/models/mistral").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH))
                .andExpect(status().isOk());
        brain.verify();
    }

    @Test
    void listGeminiModels_forwards() throws Exception {
        brain.expect(requestTo(endsWith("/models/gemini")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"models\":[]}", MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/settings/models/gemini").header(HttpHeaders.AUTHORIZATION, ADMIN_AUTH))
                .andExpect(status().isOk());
        brain.verify();
    }
}
