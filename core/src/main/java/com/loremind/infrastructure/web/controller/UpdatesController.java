package com.loremind.infrastructure.web.controller;

import com.loremind.infrastructure.updates.UpdateCheckService;
import com.loremind.infrastructure.updates.UpdateCheckService.UpdateStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Endpoints admin pour la verification et le declenchement des mises a jour
 * des conteneurs LoreMind (core/brain/web).
 *
 * Protege par HTTP Basic via SecurityConfig (path /api/admin/**).
 * Si la feature n'est pas configuree (WATCHTOWER_TOKEN vide), check renvoie
 * {enabled:false} et apply repond 503.
 */
@RestController
@RequestMapping("/api/admin/updates")
public class UpdatesController {

    private static final Logger log = LoggerFactory.getLogger(UpdatesController.class);

    private final UpdateCheckService updates;
    private final boolean demoMode;

    public UpdatesController(UpdateCheckService updates,
                             @Value("${app.demo-mode:false}") boolean demoMode) {
        this.updates = updates;
        this.demoMode = demoMode;
    }

    @GetMapping("/check")
    public UpdateStatus check() {
        guardDemoMode();
        return updates.check();
    }

    @PostMapping("/apply")
    public ResponseEntity<Map<String, Object>> apply() {
        guardDemoMode();
        if (!updates.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Update apply not configured"));
        }
        try {
            updates.apply();
            return ResponseEntity.accepted()
                    .body(Map.of("status", "triggered",
                                 "message", "Watchtower va telecharger et redemarrer les conteneurs."));
        } catch (Exception e) {
            log.error("Apply update failed", e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Watchtower unreachable: " + e.getMessage()));
        }
    }

    /**
     * En mode demo, les instances ne doivent pas se mettre a jour ni meme
     * exposer leur statut (pas de surface d'attaque, et pas de redemarrage
     * intempestif d'une demo en cours). Cohérent avec SettingsController.
     */
    private void guardDemoMode() {
        if (demoMode) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Updates disabled in demo mode");
        }
    }
}
