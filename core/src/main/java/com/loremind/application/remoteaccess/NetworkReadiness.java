package com.loremind.application.remoteaccess;

import com.loremind.domain.remoteaccess.NetworkSnapshot;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Verdict « une tablette peut-elle joindre ce PC ? », calculé à partir des faits lus sur
 * le système. Logique pure (testable sans Windows).
 *
 * @param supported       vérification possible (Windows)
 * @param ready           rien ne bloque côté PC
 * @param publicNetworks  réseaux connectés classés « Public » que le pare-feu ne laisse pas passer
 * @param firewallAllowed une règle active autorise l'application sur les réseaux privés
 * @param firewallBlocked une règle active BLOQUE l'application (ex. « Annuler » dans la fenêtre Windows)
 * @param thirdPartyFirewalls pare-feu tiers présents : leurs réglages ne sont pas lisibles, donc {@code ready}
 *                        ne peut pas être garanti — le front avertit l'utilisateur de les régler lui-même
 * @param program         exécutable concerné
 * @param platform        {@code windows} ou {@code linux}
 * @param linuxFirewall   Linux : pare-feu actif ({@code ufw}, {@code firewalld}, {@code none})
 * @param portOpen        Linux : port autorisé ({@code null} = invérifiable sans droits administrateur)
 * @param fixCommand      Linux : commande à lancer pour ouvrir le port, ou {@code null}
 */
public record NetworkReadiness(
        boolean supported,
        boolean ready,
        List<String> publicNetworks,
        boolean firewallAllowed,
        boolean firewallBlocked,
        List<String> thirdPartyFirewalls,
        String program,
        String platform,
        String linuxFirewall,
        Boolean portOpen,
        String fixCommand
) {

    static final String PUBLIC = "Public";
    static final String PRIVATE = "Private";

    public static NetworkReadiness evaluate(NetworkSnapshot snapshot) {
        if (snapshot == null || !snapshot.supported()) {
            return new NetworkReadiness(false, false, List.of(), false, false, List.of(), null, null, null, null, null);
        }
        if (NetworkSnapshot.LINUX.equals(snapshot.platform())) {
            return evaluateLinux(snapshot);
        }
        List<NetworkSnapshot.FirewallRule> rules = snapshot.rules() == null ? List.of() : snapshot.rules();
        boolean allowedPrivate = covers(rules, "Allow", PRIVATE);
        boolean blockedPrivate = covers(rules, "Block", PRIVATE);
        boolean publicOpen = covers(rules, "Allow", PUBLIC) && !covers(rules, "Block", PUBLIC);

        List<String> publicNetworks = (snapshot.networks() == null ? List.<NetworkSnapshot.Network>of() : snapshot.networks())
                .stream()
                .filter(n -> PUBLIC.equalsIgnoreCase(n.category()))
                .map(n -> n.name() != null && !n.name().isBlank() ? n.name() : n.alias())
                .toList();
        // Un réseau public n'est un obstacle que si le pare-feu ne l'ouvre pas explicitement.
        List<String> blockingPublic = publicOpen ? List.of() : publicNetworks;

        // « ready » ne porte que sur ce qui est lisible (Windows) ; un pare-feu tiers est signalé à part.
        boolean ready = allowedPrivate && !blockedPrivate && blockingPublic.isEmpty();
        List<String> thirdParty = snapshot.thirdPartyFirewalls() == null ? List.of() : snapshot.thirdPartyFirewalls();
        return new NetworkReadiness(true, ready, blockingPublic, allowedPrivate, blockedPrivate, thirdParty,
                snapshot.program(), NetworkSnapshot.WINDOWS, null, null, null);
    }

    /**
     * Linux : prêt si aucun pare-feu (ufw, firewalld) n'est actif, ou si le port est ouvert.
     * Port invérifiable (ufw sans droits root) : pas prêt, mais pas en erreur — le front
     * affiche la commande « au cas où ».
     */
    private static NetworkReadiness evaluateLinux(NetworkSnapshot s) {
        String fw = s.linuxFirewall() == null ? "none" : s.linuxFirewall();
        boolean ready = "none".equals(fw) || Boolean.TRUE.equals(s.portOpen());
        return new NetworkReadiness(true, ready, List.of(), false, false, List.of(), null,
                NetworkSnapshot.LINUX, fw, s.portOpen(), ready ? null : linuxCommand(fw, s.port()));
    }

    static String linuxCommand(String firewall, int port) {
        return switch (firewall) {
            case "ufw" -> "sudo ufw allow " + port + "/tcp";
            case "firewalld" -> "sudo firewall-cmd --permanent --add-port=" + port + "/tcp && sudo firewall-cmd --reload";
            default -> null;
        };
    }

    /** Une règle ACTIVE de cette action couvre-t-elle ce profil (« Any » couvre tout) ? */
    private static boolean covers(List<NetworkSnapshot.FirewallRule> rules, String action, String profile) {
        return rules.stream()
                .filter(r -> action.equalsIgnoreCase(r.action()) && "True".equalsIgnoreCase(r.enabled()))
                .anyMatch(r -> profiles(r.profile()).stream().anyMatch(p -> p.equals("any") || p.equals(profile.toLowerCase(Locale.ROOT))));
    }

    private static List<String> profiles(String raw) {
        if (raw == null) return List.of();
        return Arrays.stream(raw.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
