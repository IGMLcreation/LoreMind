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
 * @param program         exécutable concerné
 */
public record NetworkReadiness(
        boolean supported,
        boolean ready,
        List<String> publicNetworks,
        boolean firewallAllowed,
        boolean firewallBlocked,
        String program
) {

    static final String PUBLIC = "Public";
    static final String PRIVATE = "Private";

    public static NetworkReadiness evaluate(NetworkSnapshot snapshot) {
        if (snapshot == null || !snapshot.supported()) {
            return new NetworkReadiness(false, false, List.of(), false, false, null);
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

        boolean ready = allowedPrivate && !blockedPrivate && blockingPublic.isEmpty();
        return new NetworkReadiness(true, ready, blockingPublic, allowedPrivate, blockedPrivate, snapshot.program());
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
