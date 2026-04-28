package com.loremind.domain.licensing.ports;

import com.loremind.domain.licensing.RegistryCredentials;

import java.io.IOException;

/**
 * Port de sortie : ecriture du docker config.json partage avec Watchtower.
 * <p>
 * Le fichier sert a Watchtower pour s'authentifier au registry prive (GHCR)
 * lors du pull des images du canal beta. Volume Docker {@code docker-config}
 * monte sur Core (en ecriture) et sur Watchtower (en lecture, via la variable
 * {@code DOCKER_CONFIG}).
 */
public interface DockerConfigWriter {

    /**
     * Ecrit ou met a jour les credentials pour le registry indique.
     * Cree le fichier s'il n'existe pas, conserve les autres registries deja
     * presents (en theorie : aucun, mais defensif).
     */
    void writeCredentials(RegistryCredentials credentials) throws IOException;

    /**
     * Supprime le fichier de credentials. Appele quand la licence est invalidee
     * ou que le toggle beta passe a OFF.
     */
    void clear() throws IOException;

    /**
     * @return true si le fichier de creds existe actuellement.
     */
    boolean isPresent();
}
