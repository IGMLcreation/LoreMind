package com.loremind.infrastructure.web.controller;

import com.loremind.application.licensing.ChannelSwitcherService;
import com.loremind.application.licensing.LicenseService;
import com.loremind.application.licensing.LicenseService.InstallException;
import com.loremind.domain.licensing.LicenseSnapshot;
import com.loremind.infrastructure.web.dto.licensing.ChannelStatusDTO;
import com.loremind.infrastructure.web.dto.licensing.LicenseStatusDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

/**
 * Endpoints de gestion de la licence Patreon.
 *
 * <ul>
 *   <li>{@code GET /api/license} : etat courant (status, tier, expiration...)</li>
 *   <li>{@code GET /api/license/connect-url} : URL OAuth a ouvrir dans le navigateur</li>
 *   <li>{@code POST /api/license/install} : colle un JWT recu du relais</li>
 *   <li>{@code DELETE /api/license} : deconnecte Patreon (efface la licence)</li>
 *   <li>{@code POST /api/license/refresh} : force un refresh manuel</li>
 *   <li>{@code PUT /api/license/beta-channel} : active/desactive le canal beta</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/license")
public class LicenseController {

    private final LicenseService licenseService;
    private final ChannelSwitcherService channelSwitcher;

    public LicenseController(LicenseService licenseService, ChannelSwitcherService channelSwitcher) {
        this.licenseService = licenseService;
        this.channelSwitcher = channelSwitcher;
    }

    @GetMapping
    public LicenseStatusDTO getStatus() {
        boolean enabled = licenseService.isLicensingEnabled();
        LicenseSnapshot snap = licenseService.getCurrentSnapshot();
        return LicenseStatusDTO.from(enabled, snap);
    }

    @GetMapping("/connect-url")
    public Map<String, String> getConnectUrl() {
        return Map.of("url", licenseService.buildConnectUrl());
    }

    @PostMapping("/install")
    public ResponseEntity<?> install(@RequestBody InstallRequest request) {
        if (request == null || request.jwt() == null || request.jwt().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "missing jwt"));
        }
        try {
            LicenseSnapshot snap = licenseService.installToken(request.jwt());
            return ResponseEntity.ok(LicenseStatusDTO.from(true, snap));
        } catch (InstallException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping
    public ResponseEntity<Void> disconnect() {
        licenseService.disconnect();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<LicenseStatusDTO> refresh() {
        licenseService.forceRefresh();
        boolean enabled = licenseService.isLicensingEnabled();
        return ResponseEntity.ok(LicenseStatusDTO.from(enabled, licenseService.getCurrentSnapshot()));
    }

    @PutMapping("/beta-channel")
    public ResponseEntity<?> setBetaChannel(@RequestBody BetaChannelRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "missing body"));
        }
        try {
            LicenseSnapshot snap = licenseService.setBetaChannelEnabled(request.enabled());
            return ResponseEntity.ok(LicenseStatusDTO.from(true, snap));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Bascule de canal (stable <-> beta) via sidecar switcher ────────────
    //
    // Le flux :
    //  1. UI POST /api/license/channel/switch { channel: "beta" }
    //  2. Core valide la licence (refus si target=beta sans Patreon actif)
    //  3. Core depose une commande dans le volume partage
    //  4. Sidecar `switcher` la traite (sed .env, docker compose up -d)
    //  5. UI poll GET /api/license/channel pour suivre le status

    /** Etat courant : canal actuel + dispo du sidecar + dernier resultat. */
    @GetMapping("/channel")
    public ChannelStatusDTO getChannel() {
        return ChannelStatusDTO.from(channelSwitcher);
    }

    /** Declenche un switch de canal. Renvoie l'ID de la commande pour le polling. */
    @PostMapping("/channel/switch")
    public ResponseEntity<?> switchChannel(@RequestBody ChannelSwitchRequest request) {
        if (request == null || request.channel() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "missing channel"));
        }

        ChannelSwitcherService.Channel target;
        try {
            target = ChannelSwitcherService.Channel.valueOf(request.channel().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "invalid channel (allowed: stable, beta)"));
        }

        // Garde : pas de switch vers beta sans licence Patreon valide.
        // Le switcher ferait le boulot quoi qu'il arrive (il valide juste le
        // format), donc c'est ici qu'on doit refuser cote metier.
        // VALID + GRACE autorisent l'acces beta (cf. javadoc de LicenseStatus).
        if (target == ChannelSwitcherService.Channel.BETA) {
            LicenseSnapshot snap = licenseService.getCurrentSnapshot();
            com.loremind.domain.licensing.LicenseStatus s = (snap != null) ? snap.status() : null;
            boolean allowed = s == com.loremind.domain.licensing.LicenseStatus.VALID
                           || s == com.loremind.domain.licensing.LicenseStatus.GRACE;
            if (!allowed) {
                return ResponseEntity.status(403).body(Map.of(
                        "error", "Aucune licence Patreon active — impossible de basculer sur le canal beta."));
            }
        }

        if (!channelSwitcher.isSwitcherAvailable()) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "Sidecar switcher non disponible (mise a jour requise du docker-compose.yml)."));
        }

        try {
            String id = channelSwitcher.requestSwitch(target);
            return ResponseEntity.accepted().body(Map.of(
                    "id", id,
                    "channel", target.name().toLowerCase(Locale.ROOT)));
        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Impossible d'ecrire la commande de switch: " + e.getMessage()));
        }
    }

    public record InstallRequest(String jwt) {}
    public record BetaChannelRequest(boolean enabled) {}
    public record ChannelSwitchRequest(String channel) {}
}
