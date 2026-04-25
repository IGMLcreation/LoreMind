package com.loremind.infrastructure.web.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Proxy fin entre le frontend Angular et les endpoints /settings du Brain Python.
 * <p>
 * Ce controller n'a aucune logique metier propre : il transfere les requetes
 * telles-quelles. Raison d'etre : eviter d'exposer le Brain (port 8000) au
 * navigateur et centraliser CORS sur Spring.
 * <p>
 * Les payloads sont passes en Map<String,Object> pour rester tolerant aux
 * evolutions du schema cote Brain (ajout de champs sans recompiler le Core).
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final RestTemplate restTemplate;
    private final String brainBaseUrl;
    private final boolean demoMode;

    public SettingsController(RestTemplate restTemplate,
                              @Value("${brain.base-url}") String brainBaseUrl,
                              @Value("${app.demo-mode:false}") boolean demoMode) {
        this.restTemplate = restTemplate;
        this.brainBaseUrl = brainBaseUrl;
        this.demoMode = demoMode;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getSettings() {
        guardDemoMode();
        return forward(HttpMethod.GET, "/settings", null);
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> updateSettings(@RequestBody Map<String, Object> patch) {
        guardDemoMode();
        return forward(HttpMethod.PUT, "/settings", patch);
    }

    @GetMapping("/models/ollama")
    public ResponseEntity<Map<String, Object>> listOllamaModels() {
        return forward(HttpMethod.GET, "/models/ollama", null);
    }

    @PostMapping("/models/ollama/info")
    public ResponseEntity<Map<String, Object>> getOllamaModelInfo(@RequestBody Map<String, Object> body) {
        return forward(HttpMethod.POST, "/models/ollama/info", body);
    }

    /**
     * Telecharge un modele Ollama et streame la progression au client.
     * <p>
     * On bypass RestTemplate (qui bufferise toute la reponse) au profit du
     * client HTTP standard de Java en mode streaming. Le Brain renvoie du
     * NDJSON ligne par ligne ; on relaie chaque chunk tel quel pour que le
     * frontend voie la progression en temps reel.
     */
    @PostMapping(value = "/models/ollama/pull", produces = "application/x-ndjson")
    public ResponseEntity<StreamingResponseBody> pullOllamaModel(@RequestBody Map<String, Object> body) {
        guardDemoMode();
        StreamingResponseBody stream = output -> {
            HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(brainBaseUrl + "/models/ollama/pull"))
                    .timeout(Duration.ofMinutes(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))
                    .build();
            try {
                HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream in = resp.body()) {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        output.write(buf, 0, n);
                        output.flush();
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IOException("Pull interrompu", ie);
            }
        };
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/x-ndjson")).body(stream);
    }

    @DeleteMapping("/models/ollama/{name}")
    public ResponseEntity<Map<String, Object>> deleteOllamaModel(@PathVariable("name") String name) {
        guardDemoMode();
        return forward(HttpMethod.DELETE, "/models/ollama/" + name, null);
    }

    @GetMapping("/models/onemin")
    public ResponseEntity<Map<String, Object>> listOneMinModels() {
        return forward(HttpMethod.GET, "/models/onemin", null);
    }

    /**
     * Serialiseur JSON minimal pour eviter d'instancier ObjectMapper a chaque
     * appel. Suffisant pour notre cas d'usage : Map<String,Object> avec des
     * String/Number/Boolean en valeur.
     */
    private static String toJson(Map<String, Object> m) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : m.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escape(e.getKey())).append("\":");
            Object v = e.getValue();
            if (v == null) sb.append("null");
            else if (v instanceof Number || v instanceof Boolean) sb.append(v);
            else sb.append("\"").append(escape(v.toString())).append("\"");
            first = false;
        }
        return sb.append("}").toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private void guardDemoMode() {
        if (demoMode) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Settings disabled in demo mode");
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ResponseEntity<Map<String, Object>> forward(HttpMethod method, String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                brainBaseUrl + path, method, entity, Map.class);
        return ResponseEntity.status(response.getStatusCode()).body((Map<String, Object>) response.getBody());
    }
}
