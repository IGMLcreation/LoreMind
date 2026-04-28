package com.loremind.infrastructure.web.controller;

import com.loremind.application.licensing.LicenseService;
import com.loremind.application.licensing.LicenseService.InstallException;
import com.loremind.domain.licensing.LicenseSnapshot;
import com.loremind.infrastructure.web.dto.licensing.LicenseStatusDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    public LicenseController(LicenseService licenseService) {
        this.licenseService = licenseService;
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

    public record InstallRequest(String jwt) {}
    public record BetaChannelRequest(boolean enabled) {}
}
