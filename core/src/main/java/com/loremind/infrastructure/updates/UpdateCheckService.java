package com.loremind.infrastructure.updates;

import com.loremind.application.licensing.LicenseService;
import com.loremind.domain.licensing.LicenseSnapshot;
import com.loremind.domain.licensing.LicenseStatus;
import com.loremind.domain.licensing.RegistryCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Detection des mises a jour disponibles + declenchement via Watchtower.
 * <p>
 * <b>Strategie</b> : comparaison de versions semver, pas de digests.
 * <ul>
 *   <li>La version courante de l'app est lue depuis {@link BuildProperties}
 *       (genere par spring-boot-maven-plugin dans META-INF/build-info.properties).</li>
 *   <li>Pour chaque image suivie, on interroge le registry sur
 *       {@code /v2/<image>/tags/list}, on extrait les tags semver, on prend le max.</li>
 *   <li>Si max > version courante => UPDATE_AVAILABLE.</li>
 *   <li>Si max == version courante => UP_TO_DATE.</li>
 *   <li>Si registry injoignable ou aucun tag valide => UNKNOWN.</li>
 * </ul>
 *
 * <b>Pourquoi pas les digests ?</b> Le bug historique etait : le baseline-digest
 * pose au @PostConstruct supposait que le pull venait d'avoir lieu (vrai apres
 * `docker compose pull && up -d`, faux apres un simple restart de daemon ou un
 * OOM). La version semver lue depuis le binaire est <b>fiable par construction</b> :
 * c'est ce que le code source declare faire tourner.
 */
@Service
public class UpdateCheckService {

    private static final Logger log = LoggerFactory.getLogger(UpdateCheckService.class);

    private final RestTemplate http;
    private final String registry;
    private final List<String> images;
    private final String watchtowerUrl;
    private final String watchtowerToken;
    private final List<String> betaImages;
    private final LicenseService licenseService;
    /** Version semver courante du binaire (ex: "0.8.0"). Source de verite. */
    private final String currentVersion;

    public UpdateCheckService(
            RestTemplateBuilder builder,
            @Value("${update-check.registry:}") String registry,
            @Value("${update-check.images:}") String imagesCsv,
            @Value("${update-check.watchtower-url:http://watchtower:8080}") String watchtowerUrl,
            @Value("${update-check.watchtower-token:}") String watchtowerToken,
            @Value("${licensing.beta.images:}") String betaImagesCsv,
            LicenseService licenseService,
            @Nullable BuildProperties buildProperties) {
        this.http = builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(15))
                .build();
        this.registry = normalizeRegistry(registry);
        this.images = parseImages(imagesCsv);
        this.watchtowerUrl = watchtowerUrl;
        this.watchtowerToken = watchtowerToken;
        this.betaImages = parseImages(betaImagesCsv);
        this.licenseService = licenseService;
        this.currentVersion = buildProperties != null ? buildProperties.getVersion() : null;
        log.info("Update check init - registry={} images={} currentVersion={}",
                this.registry, this.images, this.currentVersion);
    }

    public boolean isEnabled() {
        return watchtowerToken != null && !watchtowerToken.isBlank() && !images.isEmpty();
    }

    /**
     * @return version courante exposee aux endpoints (ex: pour affichage UI).
     *         {@code null} si build-info.properties absent (dev en IDE sans build Maven).
     */
    public String getCurrentVersion() {
        return currentVersion;
    }

    public UpdateStatus check() {
        if (!isEnabled()) {
            return new UpdateStatus(false, false, false, null, List.of(), Instant.now());
        }
        if (currentVersion == null) {
            log.warn("Update check : currentVersion absente (build-info manquant). Tous UNKNOWN.");
            List<ImageStatus> statuses = new ArrayList<>();
            for (String image : images) {
                statuses.add(new ImageStatus(image, null, null, ImageStatusKind.UNKNOWN));
            }
            return new UpdateStatus(true, false, true, null, statuses, Instant.now());
        }

        ImagesEvaluation ev = evaluateImages(images, registry, null);
        return new UpdateStatus(true, ev.anyUpdate(), ev.anyUnknown(),
                currentVersion, ev.statuses(), Instant.now());
    }

    /**
     * Verifie l'etat du canal beta (images privees GHCR) avec auth basique.
     */
    public BetaStatus checkBeta() {
        if (!licenseService.isLicensingEnabled()) {
            return BetaStatus.disabled("licensing-not-configured");
        }
        LicenseSnapshot snap = licenseService.getCurrentSnapshot();
        if (snap.status() != LicenseStatus.VALID && snap.status() != LicenseStatus.GRACE) {
            return BetaStatus.disabled("license-" + snap.status().name().toLowerCase());
        }
        if (!snap.betaChannelEnabled()) {
            return BetaStatus.disabled("beta-toggle-off");
        }
        if (betaImages.isEmpty()) {
            return BetaStatus.disabled("no-beta-images-configured");
        }

        Optional<RegistryCredentials> creds = licenseService.fetchRegistryCredentials();
        if (creds.isEmpty()) {
            return new BetaStatus(true, false, true, List.of(), Instant.now(), "relay-unavailable");
        }

        String basicAuth = "Basic " + Base64.getEncoder().encodeToString(
                (creds.get().username() + ":" + creds.get().password()).getBytes(StandardCharsets.UTF_8));
        String betaRegistry = normalizeRegistry(creds.get().registry());

        ImagesEvaluation ev = evaluateImages(betaImages, betaRegistry, basicAuth);
        return new BetaStatus(true, ev.anyUpdate(), ev.anyUnknown(),
                ev.statuses(), Instant.now(), null);
    }

    /** Résultat de l'évaluation d'une liste d'images : statuts + drapeaux agrégés. */
    private record ImagesEvaluation(List<ImageStatus> statuses, boolean anyUpdate, boolean anyUnknown) {
    }

    /**
     * Boucle commune à {@link #check()} et {@link #checkBeta()} : pour chaque image,
     * récupère le tag semver le plus haut et le compare à la version courante.
     * Une image dont les tags sont injoignables ou non semver → {@code UNKNOWN}.
     */
    private ImagesEvaluation evaluateImages(List<String> imageList, String registryUrl,
                                            @Nullable String authHeader) {
        List<ImageStatus> statuses = new ArrayList<>();
        boolean anyUpdate = false;
        boolean anyUnknown = false;
        for (String image : imageList) {
            String latest = null;
            try {
                latest = fetchLatestSemverTag(registryUrl, image, authHeader);
            } catch (Exception e) {
                log.warn("Tags fetch failed for {}: {}", image, e.getMessage());
            }
            ImageStatusKind kind;
            if (latest == null) {
                kind = ImageStatusKind.UNKNOWN;
                anyUnknown = true;
            } else if (currentVersion != null && SemverComparator.compareSemver(currentVersion, latest) >= 0) {
                kind = ImageStatusKind.UP_TO_DATE;
            } else {
                kind = ImageStatusKind.UPDATE_AVAILABLE;
                anyUpdate = true;
            }
            statuses.add(new ImageStatus(image, currentVersion, latest, kind));
        }
        return new ImagesEvaluation(statuses, anyUpdate, anyUnknown);
    }

    public void apply() {
        if (!isEnabled()) {
            throw new IllegalStateException("Update apply not configured (WATCHTOWER_TOKEN missing)");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(watchtowerToken);
        http.exchange(
                watchtowerUrl + "/v1/update",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                Void.class);
    }

    // -----------------------------------------------------------------------
    // Registry HTTP API v2 - tags listing + auth bearer
    //
    // NB : le RestTemplate (champ `http`) reste porté par ce service — les tests
    // l'injectent par réflexion. Le parsing semver et le parsing du challenge
    // WWW-Authenticate sont, eux, externalisés (SemverComparator, WwwAuthenticate).
    // -----------------------------------------------------------------------

    /**
     * Interroge le registry pour la liste des tags d'une image, parse les
     * versions semver et retourne la plus elevee. {@code null} si echec
     * ou aucun tag valide.
     *
     * @param registryUrl URL normalisee (ex: "https://ghcr.io")
     * @param image       nom de l'image (ex: "igmlcreation/loremind-core")
     * @param authHeader  optionnel - "Basic ..." pour les registries prives
     */
    private String fetchLatestSemverTag(String registryUrl, String image, @Nullable String authHeader) {
        String url = registryUrl + "/v2/" + image + "/tags/list";
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (authHeader != null) {
            headers.set(HttpHeaders.AUTHORIZATION, authHeader);
        }
        TagsListResponse body;
        try {
            body = tagsCall(url, headers);
        } catch (HttpClientErrorException.Unauthorized e) {
            HttpHeaders respHeaders = e.getResponseHeaders();
            String www = respHeaders == null ? null : respHeaders.getFirst(HttpHeaders.WWW_AUTHENTICATE);
            String token = obtainBearerToken(www, authHeader);
            if (token == null) {
                log.warn("Cannot obtain bearer token for {} (registry response: {})", image, www);
                return null;
            }
            HttpHeaders bearerHeaders = new HttpHeaders();
            bearerHeaders.setAccept(List.of(MediaType.APPLICATION_JSON));
            bearerHeaders.setBearerAuth(token);
            body = tagsCall(url, bearerHeaders);
        }
        if (body == null || body.tags() == null || body.tags().isEmpty()) return null;
        return SemverComparator.findMaxSemver(body.tags());
    }

    private TagsListResponse tagsCall(String url, HttpHeaders headers) {
        ResponseEntity<TagsListResponse> resp = http.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), TagsListResponse.class);
        return resp.getBody();
    }

    /**
     * Suit le challenge {@code WWW-Authenticate: Bearer realm="..."} pour obtenir
     * un token. Si {@code basicAuth} est fourni, l'utilise pour l'echange (cas
     * registry prive). Sinon anonyme (cas registry public).
     */
    @SuppressWarnings("rawtypes")
    private String obtainBearerToken(@Nullable String wwwAuth, @Nullable String basicAuth) {
        if (wwwAuth == null) return null;
        String prefix = "Bearer ";
        if (!wwwAuth.regionMatches(true, 0, prefix, 0, prefix.length())) return null;
        Map<String, String> params = WwwAuthenticate.parseParams(wwwAuth.substring(prefix.length()));
        String realm = params.get("realm");
        if (realm == null) return null;
        StringBuilder url = new StringBuilder(realm);
        boolean hasQuery = realm.contains("?");
        for (String key : new String[]{"service", "scope"}) {
            String v = params.get(key);
            if (v != null) {
                String encoded = URLEncoder.encode(v, StandardCharsets.UTF_8)
                        .replace("%3A", ":")
                        .replace("%2F", "/");
                url.append(hasQuery ? '&' : '?').append(key).append('=').append(encoded);
                hasQuery = true;
            }
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            if (basicAuth != null) {
                headers.set(HttpHeaders.AUTHORIZATION, basicAuth);
            }
            ResponseEntity<Map> resp = http.exchange(url.toString(), HttpMethod.GET,
                    new HttpEntity<>(headers), Map.class);
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
    // Records / DTO
    // -----------------------------------------------------------------------

    public enum ImageStatusKind { UP_TO_DATE, UPDATE_AVAILABLE, UNKNOWN }

    public record UpdateStatus(
            boolean enabled,
            boolean updateAvailable,
            boolean anyUnknown,
            String currentVersion,
            List<ImageStatus> images,
            Instant checkedAt) {}

    /**
     * Statut par image. {@code localVersion} = version embarquee dans le binaire ;
     * {@code remoteVersion} = plus haute version semver trouvee dans le registry.
     * {@code updateAvailable} est derive de {@code status} (back-compat front).
     */
    public record ImageStatus(
            String image,
            String localVersion,
            String remoteVersion,
            ImageStatusKind status,
            boolean updateAvailable) {

        public ImageStatus(String image, String localVersion, String remoteVersion, ImageStatusKind status) {
            this(image, localVersion, remoteVersion, status, status == ImageStatusKind.UPDATE_AVAILABLE);
        }
    }

    public record BetaStatus(
            boolean enabled,
            boolean updateAvailable,
            boolean anyUnknown,
            List<ImageStatus> images,
            Instant checkedAt,
            String disabledReason) {

        public static BetaStatus disabled(String reason) {
            return new BetaStatus(false, false, false, List.of(), Instant.now(), reason);
        }
    }

    /** DTO pour deserialisation Jackson de /v2/.../tags/list. */
    record TagsListResponse(String name, List<String> tags) {}
}
