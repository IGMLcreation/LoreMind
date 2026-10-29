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
 * Une version BETA (le canal beta est libre : chaque beta est publiee en
 * prerelease GitHub) interroge la liste complete des releases, pour etre
 * prevenue de la beta suivante comme de la version stable qui la remplace.
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
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10))
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

            boolean beta = isPrerelease(currentVersion);
            ResponseEntity<JsonNode> resp = http.exchange(
                    beta ? allReleasesUrl(releasesApiUrl) : releasesApiUrl,
                    HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
            JsonNode release = beta ? newestRelease(resp.getBody()) : resp.getBody();
            if (release == null || release.path("tag_name").isMissingNode()) {
                return Optional.empty();
            }
            String latest = version(release);                          // ex: "0.15.0"
            String releaseUrl = release.path("html_url").asText(null);  // page de la release

            if (!latest.isBlank() && (beta ? compareVersions(currentVersion, latest) : compareSemver(currentVersion, latest)) < 0) {
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

    static boolean isPrerelease(String version) {
        return version.split("\\+", 2)[0].contains("-");
    }

    /** {@code .../releases/latest} -> {@code .../releases?per_page=30} (prereleases incluses). */
    static String allReleasesUrl(String latestUrl) {
        String base = latestUrl.endsWith("/latest") ? latestUrl.substring(0, latestUrl.length() - "/latest".length()) : latestUrl;
        return base + "?per_page=30";
    }

    private static String version(JsonNode release) {
        String tag = release.path("tag_name").asText("");
        return tag.startsWith("v") ? tag.substring(1) : tag;
    }

    /** Release publiee la plus recente (stable ou beta), brouillons exclus. */
    @Nullable
    static JsonNode newestRelease(@Nullable JsonNode releases) {
        if (releases == null || !releases.isArray()) return null;
        JsonNode best = null;
        for (JsonNode r : releases) {
            if (r.path("draft").asBoolean(false) || version(r).isBlank()) continue;
            if (best == null || compareVersions(version(best), version(r)) < 0) best = r;
        }
        return best;
    }

    /**
     * Comme {@link #compareSemver} mais tient compte du suffixe : a version egale,
     * la stable passe devant la beta (1.5.0-beta &lt; 1.5.0), et deux betas se
     * comparent par leur numero (1.5.0-beta &lt; 1.5.0-beta.2 &lt; 1.5.0-beta.10).
     */
    static int compareVersions(String a, String b) {
        int cmp = compareSemver(a, b);
        if (cmp != 0) return cmp;
        String sa = suffix(a);
        String sb = suffix(b);
        if (sa.isEmpty() || sb.isEmpty()) return Boolean.compare(sa.isEmpty(), sb.isEmpty());
        return compareSuffixes(sa, sb);
    }

    private static String suffix(String version) {
        String noBuild = version.split("\\+", 2)[0];
        int dash = noBuild.indexOf('-');
        return dash < 0 ? "" : noBuild.substring(dash + 1);
    }

    /** Compare des suffixes en traitant les nombres comme des nombres (beta.10 &gt; beta.2). */
    private static int compareSuffixes(String a, String b) {
        String[] pa = a.split("(?<=\\d)(?=\\D)|(?<=\\D)(?=\\d)");
        String[] pb = b.split("(?<=\\d)(?=\\D)|(?<=\\D)(?=\\d)");
        for (int i = 0; i < Math.min(pa.length, pb.length); i++) {
            boolean na = Character.isDigit(pa[i].charAt(0));
            boolean nb = Character.isDigit(pb[i].charAt(0));
            int cmp = na && nb ? Long.compare(Long.parseLong(pa[i]), Long.parseLong(pb[i])) : pa[i].compareTo(pb[i]);
            if (cmp != 0) return cmp;
        }
        return Integer.compare(pa.length, pb.length);
    }

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
