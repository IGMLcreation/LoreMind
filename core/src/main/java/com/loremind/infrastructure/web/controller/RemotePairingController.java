package com.loremind.infrastructure.web.controller;

import com.loremind.application.remoteaccess.PairedDevice;
import com.loremind.application.remoteaccess.PairingRejectedException;
import com.loremind.application.remoteaccess.PairingResult;
import com.loremind.application.remoteaccess.RemoteAccessService;
import com.loremind.domain.remoteaccess.RemoteDevice;
import com.loremind.infrastructure.web.config.RemoteAccessFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Appairage d'une tablette (mode bureau) : seules routes d'API joignables depuis le réseau
 * sans jeton (avec {@code /api/config} et {@code /api/version}).
 * <p>
 * Le jeton est posé en cookie {@code HttpOnly} + {@code SameSite=Strict} : envoyé
 * automatiquement par le navigateur (y compris pour les {@code <img>}), illisible par le
 * JavaScript de la page, et jamais joint à une requête venue d'un autre site (anti-CSRF).
 */
@RestController
@Profile("local")
@RequestMapping("/api/remote-access")
public class RemotePairingController {

    static final Duration COOKIE_LIFETIME = Duration.ofDays(365);

    private final RemoteAccessService remoteAccessService;

    public RemotePairingController(RemoteAccessService remoteAccessService) {
        this.remoteAccessService = remoteAccessService;
    }

    public record PairRequest(String code, String deviceName) {}

    /** Ce que le front a besoin de savoir pour décider d'afficher l'écran d'appairage. */
    public record SessionInfo(boolean remote, boolean paired, String deviceName) {}

    @PostMapping("/pair")
    public ResponseEntity<PairedDevice> pair(@RequestBody PairRequest request,
                                             @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        PairingResult result = remoteAccessService.pair(request.code(), request.deviceName(), userAgent);
        ResponseCookie cookie = ResponseCookie.from(RemoteAccessFilter.DEVICE_COOKIE, result.token())
                .httpOnly(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(COOKIE_LIFETIME)
                .build();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(result.device());
    }

    @GetMapping("/session")
    public SessionInfo session(HttpServletRequest request) {
        boolean remote = RemoteAccessFilter.isFromLan(request, remoteAccessService.lanPort());
        if (!remote) return new SessionInfo(false, true, null);
        Optional<RemoteDevice> device = remoteAccessService.authenticate(RemoteAccessFilter.deviceToken(request));
        return new SessionInfo(true, device.isPresent(), device.map(RemoteDevice::getName).orElse(null));
    }

    @ExceptionHandler(PairingRejectedException.class)
    public ResponseEntity<Map<String, String>> rejected(PairingRejectedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
    }
}
