package com.loremind.infrastructure.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Optional;

/**
 * Verification des mises a jour pour l'application de BUREAU (profil "local").
 * <p>
 * Contrairement au mode Docker (registry + Watchtower, cf.
 * {@link com.loremind.infrastructure.updates.UpdateCheckService}), il n'y a pas
 * de mise a jour automatique : on interroge l'API <b>GitHub Releases</b> pour la
 * derniere release STABLE, on compare a la version courante du binaire, et si
 * une version plus recente existe on le signale (via l'icone systray, cf.
 * {@link SystemTrayManager}). L'utilisateur telecharge puis lance le nouvel
 * installeur (MSI de meme UpgradeCode = mise a jour en place).
 * <p>
 * {@code /releases/latest} ne renvoie que les releases stables (pas les
 * prereleases) : les utilisateurs stables ne sont donc pas notifies des betas.
 */
@Service
@Profile("local")
public class DesktopUpdateService {

    private static final Logger log = LoggerFactory.getLogger(DesktopUpdateService.class);

    private final RestTemplate http;
    private final boolean enabled;
    private final String releasesApiUrl;
    /** Version semver du binaire courant (ex: "0.14.0"), ou null en dev sans build-info. */
    private final String currentVersion;

    public DesktopUpdateService(
            RestTemplateBuilder builder,
            @Value("${desktop.update.enabled:true}") boolean enabled,
            @Value("${desktop.update.releases-api-url:https://api.github.com/repos/IGMLcreation/LoreMind/releases/latest}") String releasesApiUrl,
            @Nullable BuildProperties buildProperties) {
        this.http = builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
        this.enabled = enabled;
        this.releasesApiUrl = releasesApiUrl;
        this.currentVersion = buildProperties != null ? buildProperties.getVersion() : null;
    }

    /**
     * Interroge GitHub Releases. Retourne les infos de mise a jour SI une version
     * plus recente que la version courante existe, sinon {@code Optional.empty()}
     * (a jour, desactive, ou verification impossible — jamais d'exception propagee).
     */
    public Optional<UpdateInfo> checkForUpdate() {
        if (!enabled || currentVersion == null) {
            return Optional.empty();
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            // GitHub exige un User-Agent ; l'Accept versionne l'API.
            headers.set(HttpHeaders.USER_AGENT, "LoreMind-Desktop");
            headers.set(HttpHeaders.ACCEPT, "application/vnd.github+json");

            ResponseEntity<JsonNode> resp = http.exchange(
                    releasesApiUrl, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
            JsonNode body = resp.getBody();
            if (body == null || body.path("tag_name").isMissingNode()) {
                return Optional.empty();
            }
            String tag = body.path("tag_name").asText("");          // ex: "v0.15.0"
            String releaseUrl = body.path("html_url").asText(null);  // page de la release
            String latest = tag.startsWith("v") ? tag.substring(1) : tag;

            if (!latest.isBlank() && compareSemver(currentVersion, latest) < 0) {
                log.info("[Update] Nouvelle version disponible : {} (courante : {})", latest, currentVersion);
                return Optional.of(new UpdateInfo(currentVersion, latest, releaseUrl));
            }
            log.info("[Update] A jour (courante : {}, derniere release : {}).", currentVersion, latest);
            return Optional.empty();
        } catch (Exception e) {
            // Hors-ligne, rate-limit GitHub, etc. : non bloquant, on ne notifie juste pas.
            log.info("[Update] Verification GitHub Releases impossible : {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Infos d'une mise a jour disponible. */
    public record UpdateInfo(String currentVersion, String latestVersion, String releaseUrl) {}

    /**
     * Compare deux versions MAJOR.MINOR.PATCH (suffixe -beta/-rc ignore).
     * @return &lt;0 si a&lt;b, 0 si egales, &gt;0 si a&gt;b.
     */
    static int compareSemver(String a, String b) {
        int[] va = parse(a);
        int[] vb = parse(b);
        for (int i = 0; i < 3; i++) {
            int cmp = Integer.compare(va[i], vb[i]);
            if (cmp != 0) return cmp;
        }
        return 0;
    }

    private static int[] parse(String version) {
        String core = version.split("[-+]", 2)[0]; // retire -beta, -rc, +build...
        String[] parts = core.split("\\.");
        int[] out = new int[3];
        for (int i = 0; i < 3 && i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException ignored) {
                out[i] = 0;
            }
        }
        return out;
    }
}
