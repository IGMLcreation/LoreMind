package com.loremind.infrastructure.web.controller;

import com.loremind.infrastructure.updates.UpdateCheckService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Expose la configuration publique consommee par le frontend au demarrage.
 * Activer le mode demo via la variable d'env DEMO_MODE=true : le front
 * masque alors Settings / Export VTT, et les endpoints sensibles sont
 * verrouilles cote serveur (cf. SettingsController).
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final boolean demoMode;
    private final UpdateCheckService updates;

    public ConfigController(@Value("${app.demo-mode:false}") boolean demoMode,
                            UpdateCheckService updates) {
        this.demoMode = demoMode;
        this.updates = updates;
    }

    @GetMapping
    public Map<String, Object> getPublicConfig() {
        return Map.of(
                "demoMode", demoMode,
                "updateCheckEnabled", updates.isEnabled());
    }
}
