package com.loremind.infrastructure.web.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

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

    @GetMapping("/models/onemin")
    public ResponseEntity<Map<String, Object>> listOneMinModels() {
        return forward(HttpMethod.GET, "/models/onemin", null);
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
