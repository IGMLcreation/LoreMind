package com.loremind.infrastructure.updates;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Retire les identifiants GHCR laisses par l'ancien canal beta Patreon.
 *
 * <p>Jusqu'a la 1.5, le Core ecrivait un {@code config.json} Docker (jeton GHCR recu
 * du relais Patreon) dans un volume partage avec Watchtower et le switcher. Le canal
 * beta est desormais libre et ses images publiques : ce jeton ne sert plus, et une
 * fois revoque il ferait ECHOUER les telechargements (un registry refuse des
 * identifiants invalides, meme pour une image publique). Le fichier n'a jamais
 * contenu que ces identifiants : on le supprime au demarrage.
 *
 * <p>No-op sur les installations recentes : le docker-compose.yml ne monte plus ce
 * volume ni ne definit {@code LICENSING_DOCKER_CONFIG_PATH}.
 */
@Component
public class LegacyRegistryCredentialsCleanup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyRegistryCredentialsCleanup.class);

    private final String configPath;

    public LegacyRegistryCredentialsCleanup(@Value("${LICENSING_DOCKER_CONFIG_PATH:}") String configPath) {
        this.configPath = configPath;
    }

    @Override
    public void run(ApplicationArguments args) {
        cleanup();
    }

    /** @return true si un ancien fichier d'identifiants a ete supprime */
    boolean cleanup() {
        if (configPath == null || configPath.isBlank()) return false;
        Path file = Path.of(configPath);
        try {
            if (Files.deleteIfExists(file)) {
                log.info("[Updates] Anciens identifiants GHCR du canal beta supprimes ({}).", file);
                return true;
            }
        } catch (IOException | SecurityException e) {
            log.warn("[Updates] Impossible de supprimer les anciens identifiants GHCR ({}) : {}", file, e.getMessage());
        }
        return false;
    }
}
