package com.loremind.infrastructure.web.config;

import com.loremind.application.remoteaccess.RemoteAccessService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Garde de l'accès distant (mode bureau). Ne concerne QUE les requêtes arrivées par le
 * connecteur réseau local (port dédié) : celles du connecteur habituel (127.0.0.1) passent
 * sans changement.
 * <ul>
 *   <li>front statique (hors {@code /api/}) : libre — la tablette doit pouvoir afficher l'écran d'appairage ;</li>
 *   <li>{@code /api/config}, {@code /api/version}, appairage : libres ;</li>
 *   <li>administration (Paramètres, données, mises à jour, licence) : refusée, toujours, même appairé ;</li>
 *   <li>tout le reste de l'API : cookie d'appareil valide, sinon 401 {@code remote_pairing_required}.</li>
 * </ul>
 * Pas un {@code @Component} : instancié par {@link SecurityConfig} pour ne pas être enregistré
 * deux fois (chaîne de sécurité + filtre servlet automatique).
 */
public class RemoteAccessFilter extends OncePerRequestFilter {

    public static final String DEVICE_COOKIE = "lm_device";

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/config", "/api/version", "/api/remote-access/pair", "/api/remote-access/session");
    private static final List<String> LOCAL_ONLY_PREFIXES = List.of(
            "/api/settings", "/api/admin");

    private final RemoteAccessService remoteAccessService;

    public RemoteAccessFilter(RemoteAccessService remoteAccessService) {
        this.remoteAccessService = remoteAccessService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!isFromLan(request, remoteAccessService.lanPort())) {
            chain.doFilter(request, response);
            return;
        }
        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || PUBLIC_PATHS.contains(path)) {
            chain.doFilter(request, response);
            return;
        }
        if (LOCAL_ONLY_PREFIXES.stream().anyMatch(path::startsWith)) {
            reject(response, HttpServletResponse.SC_FORBIDDEN, "remote_forbidden");
            return;
        }
        if (remoteAccessService.authenticate(deviceToken(request)).isPresent()) {
            chain.doFilter(request, response);
            return;
        }
        reject(response, HttpServletResponse.SC_UNAUTHORIZED, "remote_pairing_required");
    }

    /** Arrivée par le connecteur réseau local ? (le port d'arrivée suffit : l'autre connecteur est sur 127.0.0.1). */
    public static boolean isFromLan(HttpServletRequest request, int lanPort) {
        return lanPort > 0 && request.getLocalPort() == lanPort;
    }

    public static String deviceToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (DEVICE_COOKIE.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private static void reject(HttpServletResponse response, int status, String error) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + error + "\"}");
    }
}
