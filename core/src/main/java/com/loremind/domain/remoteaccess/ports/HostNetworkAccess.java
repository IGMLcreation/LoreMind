package com.loremind.domain.remoteaccess.ports;

import com.loremind.domain.remoteaccess.NetworkSnapshot;

/**
 * Port vers le système hôte pour que l'accès distant fonctionne vraiment : sans règle
 * de pare-feu, ou sur un réseau classé « Public », Windows jette en silence les requêtes
 * de la tablette (elle charge sans fin).
 */
public interface HostNetworkAccess {

    /** Résultat de la demande d'autorisation dans le pare-feu. */
    enum FirewallFixOutcome { APPLIED, CANCELLED, FAILED, UNSUPPORTED }

    /** Lecture seule, sans droits administrateur. */
    NetworkSnapshot inspect();

    /**
     * Autorise l'application en entrée sur les réseaux privés. Passe par la confirmation
     * administrateur du système (UAC) : bloque jusqu'à la réponse de l'utilisateur.
     */
    FirewallFixOutcome allowThroughFirewall();

    /** Ouvre la page des réglages réseau du système (pour passer un Wi-Fi en « Privé »). */
    void openNetworkSettings();
}
