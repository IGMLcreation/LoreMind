package com.loremind.infrastructure.licensing;

import com.loremind.application.licensing.LicenseService;
import com.loremind.domain.licensing.LicenseSnapshot;
import com.loremind.domain.licensing.LicenseStatus;
import com.loremind.domain.licensing.RegistryCredentials;
import com.loremind.domain.licensing.ports.DockerConfigWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

/**
 * Daemon planifie qui :
 * <ul>
 *   <li>renouvelle le JWT licence via le relais avant expiration (J-2)</li>
 *   <li>met a jour les credentials registry GHCR pour Watchtower
 *       (volume partage docker-config) tant que le canal beta est ON</li>
 *   <li>nettoie les credentials si la licence est invalidee ou le toggle OFF</li>
 * </ul>
 * Idempotent : peut tourner toutes les 6h sans risque, fait du no-op
 * la plupart du temps.
 */
@Component
public class LicenseRefreshDaemon {

    private static final Logger log = LoggerFactory.getLogger(LicenseRefreshDaemon.class);

    /** 6 heures entre chaque cycle. Suffisant pour rattraper un J-2 sans surcharger. */
    private static final long FIXED_DELAY_MS = 6L * 60L * 60L * 1000L;
    /** Premier run apres 30s pour laisser le contexte Spring se stabiliser. */
    private static final long INITIAL_DELAY_MS = 30_000L;

    private final LicenseService licenseService;
    private final DockerConfigWriter dockerConfigWriter;

    public LicenseRefreshDaemon(LicenseService licenseService,
                                DockerConfigWriter dockerConfigWriter) {
        this.licenseService = licenseService;
        this.dockerConfigWriter = dockerConfigWriter;
    }

    @Scheduled(initialDelay = INITIAL_DELAY_MS, fixedDelay = FIXED_DELAY_MS)
    public void tick() {
        if (!licenseService.isLicensingEnabled()) {
            return;
        }
        try {
            licenseService.refreshIfNeeded();
            syncDockerConfig();
        } catch (Exception e) {
            log.error("LicenseRefreshDaemon tick failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Aligne le fichier docker config avec l'etat de la licence et le toggle :
     * <ul>
     *   <li>VALID/GRACE + beta ON  -> ecrit/refresh les creds</li>
     *   <li>tout autre cas         -> efface le fichier</li>
     * </ul>
     */
    private void syncDockerConfig() {
        LicenseSnapshot snap = licenseService.getCurrentSnapshot();
        boolean shouldHaveCreds = snap.betaChannelEnabled()
                && (snap.status() == LicenseStatus.VALID || snap.status() == LicenseStatus.GRACE);

        if (!shouldHaveCreds) {
            try {
                if (dockerConfigWriter.isPresent()) {
                    dockerConfigWriter.clear();
                }
            } catch (IOException e) {
                log.warn("Cannot clear docker config: {}", e.getMessage());
            }
            return;
        }

        Optional<RegistryCredentials> creds = licenseService.fetchRegistryCredentials();
        if (creds.isEmpty()) {
            log.warn("Beta enabled but cannot fetch registry credentials (relay down or rejected)");
            return;
        }
        try {
            dockerConfigWriter.writeCredentials(creds.get());
        } catch (IOException e) {
            log.error("Cannot write docker config: {}", e.getMessage());
        }
    }
}
