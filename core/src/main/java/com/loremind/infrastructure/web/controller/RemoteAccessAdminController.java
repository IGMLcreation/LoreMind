package com.loremind.infrastructure.web.controller;

import com.loremind.application.remoteaccess.NetworkReadiness;
import com.loremind.application.remoteaccess.PairingCode;
import com.loremind.application.remoteaccess.RemoteAccessService;
import com.loremind.application.remoteaccess.RemoteAccessStatus;
import com.loremind.domain.remoteaccess.ports.HostNetworkAccess;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Réglage de l'accès distant, côté PC (mode bureau). Sous {@code /api/settings} : réservé
 * à l'admin (HTTP Basic) ET à la machine elle-même ({@code RemoteAccessFilter} refuse ces
 * routes à tout appareil du réseau, même appairé). 404 en Docker (profil {@code local} absent).
 */
@RestController
@Profile("local")
@RequestMapping("/api/settings/remote-access")
public class RemoteAccessAdminController {

    private final RemoteAccessService remoteAccessService;
    private final HostNetworkAccess hostNetworkAccess;

    public RemoteAccessAdminController(RemoteAccessService remoteAccessService, HostNetworkAccess hostNetworkAccess) {
        this.remoteAccessService = remoteAccessService;
        this.hostNetworkAccess = hostNetworkAccess;
    }

    public record ConfigureRequest(boolean enabled, int port) {}

    /** Réponse du bouton « Autoriser dans le pare-feu » : ce qui s'est passé + le nouvel état. */
    public record FirewallFixResponse(HostNetworkAccess.FirewallFixOutcome outcome, NetworkReadiness readiness) {}

    @GetMapping
    public RemoteAccessStatus status() {
        return remoteAccessService.status();
    }

    @PutMapping
    public RemoteAccessStatus configure(@RequestBody ConfigureRequest request) {
        return remoteAccessService.configure(request.enabled(), request.port());
    }

    @PostMapping("/pairing-code")
    public PairingCode newPairingCode() {
        return remoteAccessService.newPairingCode();
    }

    /** Une tablette peut-elle joindre ce PC ? (type de réseau, règle du pare-feu) */
    @GetMapping("/network-check")
    public NetworkReadiness networkCheck() {
        return NetworkReadiness.evaluate(hostNetworkAccess.inspect());
    }

    /** Ajoute la règle du pare-feu ; attend la réponse de l'utilisateur à la confirmation Windows (UAC). */
    @PostMapping("/firewall-rule")
    public FirewallFixResponse allowThroughFirewall() {
        HostNetworkAccess.FirewallFixOutcome outcome = hostNetworkAccess.allowThroughFirewall();
        return new FirewallFixResponse(outcome, NetworkReadiness.evaluate(hostNetworkAccess.inspect()));
    }

    /** Ouvre les réglages réseau de Windows (pour passer un Wi-Fi « Public » en « Privé »). */
    @PostMapping("/open-network-settings")
    public ResponseEntity<Void> openNetworkSettings() {
        hostNetworkAccess.openNetworkSettings();
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/devices/{id}")
    public ResponseEntity<Void> revoke(@PathVariable String id) {
        return remoteAccessService.revoke(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    /** Port occupé, accès non ouvert… : conflit d'état, message prêt à afficher. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }
}
