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
 * @param thirdPartyFirewalls pare-feu tiers déclarés à Windows (Bitdefender, Norton…) : ils filtrent
 *                  EN PLUS du pare-feu Windows, avec leurs propres règles, illisibles d'ici
 * @param platform  {@code windows} ou {@code linux}
 * @param linuxFirewall Linux : pare-feu actif détecté ({@code ufw}, {@code firewalld}, {@code none})
 * @param port      port réseau de l'accès distant (Linux : port à ouvrir)
 * @param portOpen  Linux : port autorisé par le pare-feu ; {@code null} = impossible à savoir sans
 *                  droits administrateur (ex. règles ufw illisibles)
 */
public record NetworkSnapshot(boolean supported, String program, List<Network> networks, List<FirewallRule> rules,
                              List<String> thirdPartyFirewalls, String platform, String linuxFirewall,
                              int port, Boolean portOpen) {

    public static final String WINDOWS = "windows";
    public static final String LINUX = "linux";

    /** Instantané Windows (réseaux, règles du pare-feu, pare-feu tiers). */
    public NetworkSnapshot(boolean supported, String program, List<Network> networks, List<FirewallRule> rules,
                           List<String> thirdPartyFirewalls) {
        this(supported, program, networks, rules, thirdPartyFirewalls, WINDOWS, null, 0, null);
    }

    /** Instantané Linux : pare-feu actif et état du port quand il est lisible. */
    public static NetworkSnapshot linux(String linuxFirewall, int port, Boolean portOpen) {
        return new NetworkSnapshot(true, null, List.of(), List.of(), List.of(), LINUX, linuxFirewall, port, portOpen);
    }

    /** Catégorie Windows : {@code Public}, {@code Private} ou {@code DomainAuthenticated}. */
    public record Network(String name, String alias, String category) {}

    /**
     * @param action  {@code Allow} ou {@code Block}
     * @param profile profils couverts : {@code Any}, ou liste comme {@code Private, Public}
     * @param enabled {@code True} / {@code False}
     */
    public record FirewallRule(String action, String profile, String enabled) {}

    public static NetworkSnapshot unsupported() {
        return new NetworkSnapshot(false, null, List.of(), List.of(), List.of());
    }
}
