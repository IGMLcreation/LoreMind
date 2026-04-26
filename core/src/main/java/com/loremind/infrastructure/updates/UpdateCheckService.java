package com.loremind.infrastructure.updates;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detection des mises a jour disponibles + declenchement via Watchtower.
 *
 * Strategie :
 *  - Au demarrage, on interroge le registry pour le digest courant de chaque
 *    image suivie ({@code update-check.images}). On stocke ces digests comme
 *    "baseline" (= ce que le conteneur en cours d'execution est cense faire
 *    tourner, puisque le `docker compose pull` precede toujours `up -d`).
 *  - {@link #check()} re-interroge le registry et compare. Si un digest a
 *    change, une mise a jour est disponible.
 *  - {@link #apply()} POST sur /v1/update de Watchtower (qui doit etre lance
 *    avec WATCHTOWER_HTTP_API_UPDATE=true et le meme token).
 *
 * Apres un apply reussi, Watchtower redemarre core => ce service est
 * re-instancie => baseline re-aligne sur le registry => check renvoie
 * "pas de MAJ" (etat coherent).
 *
 * La feature est <b>desactivee silencieusement</b> si {@code WATCHTOWER_TOKEN}
 * n'est pas defini : check/apply renvoient des reponses neutres et l'UI
 * masque le badge / bouton.
 */
@Service
public class UpdateCheckService {

    private static final Logger log = LoggerFactory.getLogger(UpdateCheckService.class);

    private static final List<MediaType> MANIFEST_ACCEPT = List.of(
            MediaType.parseMediaType("application/vnd.docker.distribution.manifest.v2+json"),
            MediaType.parseMediaType("application/vnd.docker.distribution.manifest.list.v2+json"),
            MediaType.parseMediaType("application/vnd.oci.image.manifest.v1+json"),
            MediaType.parseMediaType("application/vnd.oci.image.index.v1+json")
    );

    private final RestTemplate http;
    private final String registry;
    private final List<String> images;
    private final String tag;
    private final String watchtowerUrl;
    private final String watchtowerToken;

    private final Map<String, String> baselineDigests = new ConcurrentHashMap<>();

    public UpdateCheckService(
            RestTemplateBuilder builder,
            @Value("${update-check.registry:}") String registry,
            @Value("${update-check.images:}") String imagesCsv,
            @Value("${update-check.tag:latest}") String tag,
            @Value("${update-check.watchtower-url:http://watchtower:8080}") String watchtowerUrl,
            @Value("${update-check.watchtower-token:}") String watchtowerToken) {
        this.http = builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(15))
                .build();
        this.registry = normalizeRegistry(registry);
        this.images = parseImages(imagesCsv);
        this.tag = tag;
        this.watchtowerUrl = watchtowerUrl;
        this.watchtowerToken = watchtowerToken;
    }

    @PostConstruct
    void initBaseline() {
        if (!isEnabled()) {
            log.info("Update check disabled (WATCHTOWER_TOKEN not set)");
            return;
        }
        log.info("Update check enabled - registry={} images={} tag={}", registry, images, tag);
        for (String image : images) {
            try {
                String digest = fetchRemoteDigest(image);
                if (digest != null) {
                    baselineDigests.put(image, digest);
                    log.debug("Baseline digest for {} = {}", image, digest);
                }
            } catch (Exception e) {
                log.warn("Cannot baseline digest for {}: {}", image, e.getMessage());
            }
        }
    }

    public boolean isEnabled() {
        return watchtowerToken != null && !watchtowerToken.isBlank() && !images.isEmpty();
    }

    public UpdateStatus check() {
        if (!isEnabled()) {
            return new UpdateStatus(false, false, List.of(), Instant.now());
        }
        List<ImageStatus> statuses = new ArrayList<>();
        boolean anyUpdate = false;
        for (String image : images) {
            String baseline = baselineDigests.get(image);
            String remote = null;
            try {
                remote = fetchRemoteDigest(image);
            } catch (Exception e) {
                log.warn("Check failed for {}: {}", image, e.getMessage());
            }
            // Si on n'a pas de baseline (echec au boot), on l'aligne maintenant
            // pour eviter un faux positif "MAJ dispo".
            if (baseline == null && remote != null) {
                baselineDigests.put(image, remote);
                baseline = remote;
            }
            boolean updateAvailable = baseline != null && remote != null && !baseline.equals(remote);
            if (updateAvailable) anyUpdate = true;
            statuses.add(new ImageStatus(image, baseline, remote, updateAvailable));
        }
        return new UpdateStatus(true, anyUpdate, statuses, Instant.now());
    }

    public void apply() {
        if (!isEnabled()) {
            throw new IllegalStateException("Update apply not configured (WATCHTOWER_TOKEN missing)");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(watchtowerToken);
        // Watchtower /v1/update declenche un scan+update immediat de tous les
        // conteneurs labellises. La reponse est synchrone et peut prendre
        // plusieurs secondes; en cas de redemarrage de core, le client
        // recevra une connexion coupee — c'est attendu, l'UI le gere.
        http.exchange(
                watchtowerUrl + "/v1/update",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                Void.class);
    }

    // -----------------------------------------------------------------------
    // Registry HTTP API v2
    // -----------------------------------------------------------------------

    private String fetchRemoteDigest(String image) {
        String url = registry + "/v2/" + image + "/manifests/" + tag;
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(MANIFEST_ACCEPT);
        try {
            return digestCall(url, headers);
        } catch (HttpClientErrorException.Unauthorized e) {
            String www = e.getResponseHeaders() == null ? null
                    : e.getResponseHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE);
            String token = obtainBearerToken(www);
            if (token == null) {
                log.warn("Cannot obtain bearer token for {} (registry response: {})", image, www);
                return null;
            }
            headers.setBearerAuth(token);
            return digestCall(url, headers);
        }
    }

    private String digestCall(String url, HttpHeaders headers) {
        ResponseEntity<Void> resp = http.exchange(
                url, HttpMethod.HEAD, new HttpEntity<>(headers), Void.class);
        return resp.getHeaders().getFirst("Docker-Content-Digest");
    }

    /**
     * Suit le challenge {@code WWW-Authenticate: Bearer realm="...",service="...",scope="..."}
     * pour obtenir un jeton (anonyme — suffisant pour les images publiques).
     */
    @SuppressWarnings("rawtypes")
    private String obtainBearerToken(String wwwAuth) {
        if (wwwAuth == null) return null;
        String prefix = "Bearer ";
        if (!wwwAuth.regionMatches(true, 0, prefix, 0, prefix.length())) return null;
        Map<String, String> params = parseAuthParams(wwwAuth.substring(prefix.length()));
        String realm = params.get("realm");
        if (realm == null) return null;
        StringBuilder url = new StringBuilder(realm);
        boolean hasQuery = realm.contains("?");
        for (String key : new String[]{"service", "scope"}) {
            String v = params.get(key);
            if (v != null) {
                // URLEncoder fait du "form encoding" qui transforme `:` et `/`
                // en %3A et %2F. La plupart des registries (Docker Hub, Gitea)
                // acceptent les deux, mais GHCR est strict et rejette le scope
                // encode (403 DENIED). On preserve donc `:` et `/` dans la
                // valeur, conformement a ce que GHCR attend
                // (et que docker pull lui-meme envoie).
                String encoded = URLEncoder.encode(v, StandardCharsets.UTF_8)
                        .replace("%3A", ":")
                        .replace("%2F", "/");
                url.append(hasQuery ? '&' : '?')
                   .append(key).append('=')
                   .append(encoded);
                hasQuery = true;
            }
        }
        try {
            ResponseEntity<Map> resp = http.getForEntity(url.toString(), Map.class);
            Map<?, ?> body = resp.getBody();
            if (body == null) return null;
            Object t = body.get("token");
            if (t == null) t = body.get("access_token");
            return t == null ? null : t.toString();
        } catch (Exception e) {
            log.warn("Bearer token request failed: {}", e.getMessage());
            return null;
        }
    }

    /** Parser minimaliste pour {@code key="value", key2="value2"}. */
    private static Map<String, String> parseAuthParams(String s) {
        Map<String, String> out = new HashMap<>();
        int i = 0;
        int n = s.length();
        while (i < n) {
            while (i < n && (s.charAt(i) == ',' || s.charAt(i) == ' ')) i++;
            int eq = s.indexOf('=', i);
            if (eq < 0) break;
            String key = s.substring(i, eq).trim();
            int valStart = eq + 1;
            String val;
            if (valStart < n && s.charAt(valStart) == '"') {
                int valEnd = s.indexOf('"', valStart + 1);
                if (valEnd < 0) break;
                val = s.substring(valStart + 1, valEnd);
                i = valEnd + 1;
            } else {
                int valEnd = s.indexOf(',', valStart);
                if (valEnd < 0) valEnd = n;
                val = s.substring(valStart, valEnd).trim();
                i = valEnd;
            }
            out.put(key, val);
        }
        return out;
    }

    private static String normalizeRegistry(String value) {
        if (value == null || value.isBlank()) return "";
        String v = value.trim();
        if (!v.startsWith("http://") && !v.startsWith("https://")) {
            v = "https://" + v;
        }
        if (v.endsWith("/")) v = v.substring(0, v.length() - 1);
        return v;
    }

    private static List<String> parseImages(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String part : csv.split(",")) {
            String p = part.trim();
            if (!p.isEmpty()) out.add(p);
        }
        return out;
    }

    // -----------------------------------------------------------------------
    // Records de retour (sortis sous forme JSON par Jackson)
    // -----------------------------------------------------------------------

    public record UpdateStatus(
            boolean enabled,
            boolean updateAvailable,
            List<ImageStatus> images,
            Instant checkedAt) {}

    public record ImageStatus(
            String image,
            String localDigest,
            String remoteDigest,
            boolean updateAvailable) {}
}
