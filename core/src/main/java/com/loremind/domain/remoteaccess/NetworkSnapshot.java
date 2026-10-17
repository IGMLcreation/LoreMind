package com.loremind.domain.remoteaccess;

import java.util.List;

/**
 * Faits bruts sur la machine hôte, lus par le système (pas d'interprétation ici) :
 * réseaux connectés avec leur catégorie Windows, et règles ENTRANTES du pare-feu qui
 * visent l'exécutable de l'application.
 *
 * @param supported vérification possible sur ce système (Windows uniquement)
 * @param program   exécutable visé par les règles (DM Loremind.exe en bureau)
 * @param networks  réseaux connectés
 * @param rules     règles entrantes du pare-feu pour ce programme
 */
public record NetworkSnapshot(boolean supported, String program, List<Network> networks, List<FirewallRule> rules) {

    /** Catégorie Windows : {@code Public}, {@code Private} ou {@code DomainAuthenticated}. */
    public record Network(String name, String alias, String category) {}

    /**
     * @param action  {@code Allow} ou {@code Block}
     * @param profile profils couverts : {@code Any}, ou liste comme {@code Private, Public}
     * @param enabled {@code True} / {@code False}
     */
    public record FirewallRule(String action, String profile, String enabled) {}

    public static NetworkSnapshot unsupported() {
        return new NetworkSnapshot(false, null, List.of(), List.of());
    }
}
