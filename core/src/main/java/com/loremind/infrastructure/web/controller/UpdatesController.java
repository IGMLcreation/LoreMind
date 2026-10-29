package com.loremind.infrastructure.web.controller;

import com.loremind.application.channel.ChannelSwitcherService;
import com.loremind.infrastructure.updates.UpdateCheckService;
import com.loremind.infrastructure.updates.UpdateCheckService.BetaStatus;
import com.loremind.infrastructure.updates.UpdateCheckService.UpdateStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import com.loremind.infrastructure.web.dto.updates.ChannelStatusDTO;

import java.io.IOException;
import java.util.Locale;
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

    /** Clé du message d'erreur dans le body JSON — contrat lu par le frontend. */
    private static final String ERROR_KEY = "error";

    private final UpdateCheckService updates;
    private final ChannelSwitcherService channels;
    private final boolean demoMode;

    public UpdatesController(UpdateCheckService updates,
                             ChannelSwitcherService channels,
                             @Value("${app.demo-mode:false}") boolean demoMode) {
        this.updates = updates;
        this.channels = channels;
        this.demoMode = demoMode;
    }

    @GetMapping("/check")
    public UpdateStatus check() {
        guardDemoMode();
        return updates.check();
    }

    @GetMapping("/check-beta")
    public BetaStatus checkBeta() {
        guardDemoMode();
        return updates.checkBeta();
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

    // --- Bascule de canal stable <-> beta via le sidecar `switcher` ----------
    //
    // Le canal beta est libre : toute instance Docker peut y passer et en revenir.
    // L'UI depose une commande (POST /channel/switch), le sidecar l'applique
    // (IMAGE_NAMESPACE du .env + docker compose pull/up), l'UI suit le resultat
    // via GET /channel.

    /** Etat courant : canal actuel + dispo du sidecar + dernier resultat. */
    @GetMapping("/channel")
    public ChannelStatusDTO channel() {
        guardDemoMode();
        return ChannelStatusDTO.from(channels);
    }

    /** Declenche un switch de canal. Renvoie l'ID de la commande pour le polling. */
    @PostMapping("/channel/switch")
    public ResponseEntity<Object> switchChannel(@RequestBody ChannelSwitchRequest request) {
        guardDemoMode();
        if (request == null || request.channel() == null) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "missing channel"));
        }
        ChannelSwitcherService.Channel target;
        try {
            target = ChannelSwitcherService.Channel.valueOf(request.channel().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "invalid channel (allowed: stable, beta)"));
        }
        if (!channels.isSwitcherAvailable()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    ERROR_KEY, "Sidecar switcher non disponible (mise a jour requise du docker-compose.yml)."));
        }
        try {
            String id = channels.requestSwitch(target);
            return ResponseEntity.accepted().body(Map.of(
                    "id", id,
                    "channel", target.name().toLowerCase(Locale.ROOT)));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    ERROR_KEY, "Impossible d'ecrire la commande de switch: " + e.getMessage()));
        }
    }

    public record ChannelSwitchRequest(String channel) {}

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
