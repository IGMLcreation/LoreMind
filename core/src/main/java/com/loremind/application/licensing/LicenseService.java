package com.loremind.application.licensing;

import com.loremind.domain.licensing.License;
import com.loremind.domain.licensing.LicenseClaims;
import com.loremind.domain.licensing.LicenseSnapshot;
import com.loremind.domain.licensing.LicenseStatus;
import com.loremind.domain.licensing.RegistryCredentials;
import com.loremind.domain.licensing.ports.JwtVerifier;
import com.loremind.domain.licensing.ports.LicenseRelay;
import com.loremind.domain.licensing.ports.LicenseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Service application pour la gestion de la licence Patreon.
 * <p>
 * Responsabilites :
 * <ul>
 *   <li>Installer un nouveau JWT recu du relais (apres OAuth utilisateur)</li>
 *   <li>Calculer le {@link LicenseStatus} courant en respectant la grace period</li>
 *   <li>Renouveler le JWT avant expiration en appelant le relais</li>
 *   <li>Activer/desactiver le toggle "canal beta" cote utilisateur</li>
 *   <li>Distribuer les credentials registry pour le pull beta</li>
 * </ul>
 */
@Service
public class LicenseService {

    private static final Logger log = LoggerFactory.getLogger(LicenseService.class);

    private final LicenseRepository repository;
    private final JwtVerifier jwtVerifier;
    private final LicenseRelay relay;
    private final long gracePeriodSeconds;
    private final long refreshBeforeExpirySeconds;

    public LicenseService(
            LicenseRepository repository,
            JwtVerifier jwtVerifier,
            LicenseRelay relay,
            @Value("${licensing.grace-period-days:14}") int gracePeriodDays,
            @Value("${licensing.refresh-before-expiry-days:2}") int refreshBeforeExpiryDays) {
        this.repository = repository;
        this.jwtVerifier = jwtVerifier;
        this.relay = relay;
        this.gracePeriodSeconds = (long) gracePeriodDays * 86_400L;
        this.refreshBeforeExpirySeconds = (long) refreshBeforeExpiryDays * 86_400L;
    }

    /**
     * @return true si le verifier est configure (cle publique presente).
     *         L'UI peut masquer toute la section Patreon si false.
     */
    public boolean isLicensingEnabled() {
        return jwtVerifier.isConfigured();
    }

    /**
     * Genere ou retourne l'instance_id stable de cette installation.
     * Stocke dans la licence elle-meme. Si pas de licence, en cree un volatil
     * (sera persiste a la prochaine connexion).
     */
    public String getOrCreateInstanceId() {
        return repository.findCurrent()
                .map(License::getInstanceId)
                .orElseGet(() -> "li-" + UUID.randomUUID());
    }

    /**
     * Construit l'URL OAuth pour ouvrir dans le navigateur de l'utilisateur.
     */
    public String buildConnectUrl() {
        return relay.buildConnectUrl(getOrCreateInstanceId());
    }

    /**
     * Installe un JWT recu du relais (l'utilisateur l'a colle dans l'UI ou
     * recu via deep-link). Verifie la signature, extrait les claims, persiste.
     */
    public LicenseSnapshot installToken(String rawJwt) throws InstallException {
        if (!jwtVerifier.isConfigured()) {
            throw new InstallException("Licensing feature not enabled (no public key configured)");
        }
        LicenseClaims claims;
        try {
            claims = jwtVerifier.verify(rawJwt);
        } catch (JwtVerifier.JwtVerificationException e) {
            throw new InstallException("Invalid JWT: " + e.getMessage());
        }

        Instant now = Instant.now();
        if (claims.expiresAt().isBefore(now)) {
            throw new InstallException("JWT already expired");
        }

        Optional<License> existing = repository.findCurrent();
        License toSave = License.builder()
                .id("current")
                .rawJwt(rawJwt)
                .patreonUserId(claims.subject())
                .tierId(claims.tierId())
                .instanceId(claims.instanceId())
                .issuedAt(claims.issuedAt())
                .expiresAt(claims.expiresAt())
                .lastRefreshAttemptAt(now)
                .lastRefreshSucceeded(true)
                // Au premier install, on active le canal beta par defaut.
                // Sur reinstall apres deconnexion, on respecte la valeur precedente.
                .betaChannelEnabled(existing.map(License::isBetaChannelEnabled).orElse(true))
                .createdAt(existing.map(License::getCreatedAt).orElse(now))
                .build();

        License saved = repository.save(toSave);
        log.info("Patreon license installed for user={} tier={} expires={}",
                saved.getPatreonUserId(), saved.getTierId(), saved.getExpiresAt());
        return snapshotOf(saved, now);
    }

    /**
     * Etat courant de la licence pour exposition UI / decision technique.
     */
    public LicenseSnapshot getCurrentSnapshot() {
        Optional<License> opt = repository.findCurrent();
        if (opt.isEmpty()) return LicenseSnapshot.none();
        return snapshotOf(opt.get(), Instant.now());
    }

    /**
     * Supprime la licence (deconnexion volontaire de Patreon par l'utilisateur).
     */
    public void disconnect() {
        repository.deleteCurrent();
        log.info("Patreon license removed (user disconnect)");
    }

    /**
     * Active ou desactive le canal beta. Necessite une licence valide ou en grace.
     */
    public LicenseSnapshot setBetaChannelEnabled(boolean enabled) {
        License current = repository.findCurrent()
                .orElseThrow(() -> new IllegalStateException("No license installed"));
        current.setBetaChannelEnabled(enabled);
        License saved = repository.save(current);
        return snapshotOf(saved, Instant.now());
    }

    /**
     * Tente un refresh si la licence est proche de l'expiration. Idempotent.
     * Appele par le daemon planifie + manuellement via l'UI ("Reessayer").
     *
     * @return true si un refresh a ete tente (avec ou sans succes)
     */
    public boolean refreshIfNeeded() {
        Optional<License> opt = repository.findCurrent();
        if (opt.isEmpty()) return false;
        License current = opt.get();
        Instant now = Instant.now();
        long secondsUntilExpiry = Duration.between(now, current.getExpiresAt()).getSeconds();
        if (secondsUntilExpiry > refreshBeforeExpirySeconds) {
            return false;
        }
        return doRefresh(current, now);
    }

    /**
     * Force un refresh immediat (bouton UI "Reessayer maintenant").
     */
    public boolean forceRefresh() {
        return repository.findCurrent()
                .map(license -> doRefresh(license, Instant.now()))
                .orElse(false);
    }

    private boolean doRefresh(License current, Instant now) {
        log.info("Refreshing Patreon license (current expires {})", current.getExpiresAt());
        try {
            String newJwt = relay.refreshToken(current.getRawJwt());
            LicenseClaims claims = jwtVerifier.verify(newJwt);

            current.setRawJwt(newJwt);
            current.setIssuedAt(claims.issuedAt());
            current.setExpiresAt(claims.expiresAt());
            current.setTierId(claims.tierId());
            current.setLastRefreshAttemptAt(now);
            current.setLastRefreshSucceeded(true);
            repository.save(current);
            log.info("License refreshed successfully (new expiry {})", claims.expiresAt());
            return true;
        } catch (LicenseRelay.RelayException e) {
            current.setLastRefreshAttemptAt(now);
            current.setLastRefreshSucceeded(false);
            repository.save(current);
            if (e.getKind() == LicenseRelay.RelayErrorKind.REJECTED) {
                log.warn("Relay rejected refresh ({}): tier may have been cancelled", e.getMessage());
            } else {
                log.warn("Relay refresh transient failure ({}): {}", e.getKind(), e.getMessage());
            }
            return true;
        } catch (JwtVerifier.JwtVerificationException e) {
            current.setLastRefreshAttemptAt(now);
            current.setLastRefreshSucceeded(false);
            repository.save(current);
            log.error("Relay returned a JWT that fails verification: {}", e.getMessage());
            return true;
        }
    }

    /**
     * Recupere les credentials registry pour pull du canal beta.
     * @return empty si pas de licence valide ou relais en echec
     */
    public Optional<RegistryCredentials> fetchRegistryCredentials() {
        LicenseSnapshot snap = getCurrentSnapshot();
        if (snap.status() != LicenseStatus.VALID && snap.status() != LicenseStatus.GRACE) {
            return Optional.empty();
        }
        License current = repository.findCurrent().orElse(null);
        if (current == null) return Optional.empty();
        try {
            return Optional.of(relay.fetchRegistryCredentials(current.getRawJwt()));
        } catch (LicenseRelay.RelayException e) {
            log.warn("Cannot fetch registry credentials ({}): {}", e.getKind(), e.getMessage());
            return Optional.empty();
        }
    }

    private LicenseSnapshot snapshotOf(License l, Instant now) {
        LicenseStatus status = computeStatus(l, now);
        return new LicenseSnapshot(
                status,
                l.getPatreonUserId(),
                l.getTierId(),
                l.getInstanceId(),
                l.getExpiresAt(),
                l.getLastRefreshAttemptAt(),
                l.isLastRefreshSucceeded(),
                l.isBetaChannelEnabled()
        );
    }

    private LicenseStatus computeStatus(License l, Instant now) {
        if (l.getExpiresAt() == null) return LicenseStatus.NONE;
        if (now.isBefore(l.getExpiresAt())) return LicenseStatus.VALID;
        long secondsPastExpiry = Duration.between(l.getExpiresAt(), now).getSeconds();
        if (secondsPastExpiry <= gracePeriodSeconds) return LicenseStatus.GRACE;
        return LicenseStatus.EXPIRED;
    }

    public static class InstallException extends Exception {
        public InstallException(String message) {
            super(message);
        }
    }
}
