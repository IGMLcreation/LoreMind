package com.loremind.application.remoteaccess;

import com.loremind.domain.remoteaccess.NetworkSnapshot;
import com.loremind.domain.remoteaccess.NetworkSnapshot.FirewallRule;
import com.loremind.domain.remoteaccess.NetworkSnapshot.Network;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Test du verdict « une tablette peut-elle joindre ce PC ? » à partir des faits lus sur Windows. */
class NetworkReadinessTest {

    private static final Network HOME_PRIVATE = new Network("Bbox-Maison", "Wi-Fi", "Private");
    private static final Network HOME_PUBLIC = new Network("Bbox-Maison", "Wi-Fi", "Public");
    private static final FirewallRule ALLOW_PRIVATE = new FirewallRule("Allow", "Private", "True");

    private static NetworkReadiness eval(List<Network> networks, List<FirewallRule> rules) {
        return NetworkReadiness.evaluate(new NetworkSnapshot(true, "C:\\DM Loremind.exe", networks, rules, List.of()));
    }

    @Test
    void unsupported_whenNotWindows() {
        NetworkReadiness r = NetworkReadiness.evaluate(NetworkSnapshot.unsupported());
        assertFalse(r.supported());
        assertFalse(r.ready());
    }

    @Test
    void ready_onPrivateNetwork_withAllowRule() {
        NetworkReadiness r = eval(List.of(HOME_PRIVATE), List.of(ALLOW_PRIVATE));
        assertTrue(r.ready());
        assertTrue(r.firewallAllowed());
        assertTrue(r.publicNetworks().isEmpty());
    }

    @Test
    void notReady_withoutAnyRule_evenOnPrivateNetwork() {
        // Cas réel du 2026-10-17 : Wi-Fi passé en privé, mais aucune règle → la tablette charge sans fin.
        NetworkReadiness r = eval(List.of(HOME_PRIVATE), List.of());
        assertFalse(r.ready());
        assertFalse(r.firewallAllowed());
    }

    @Test
    void notReady_onPublicNetwork_evenWithPrivateRule() {
        NetworkReadiness r = eval(List.of(HOME_PUBLIC), List.of(ALLOW_PRIVATE));
        assertFalse(r.ready());
        assertEquals(List.of("Bbox-Maison"), r.publicNetworks());
    }

    @Test
    void publicNetwork_isNotAnObstacle_whenPublicExplicitlyAllowed() {
        NetworkReadiness r = eval(List.of(HOME_PUBLIC), List.of(new FirewallRule("Allow", "Private, Public", "True")));
        assertTrue(r.ready());
        assertTrue(r.publicNetworks().isEmpty());
    }

    @Test
    void blockRule_winsOverAllow() {
        NetworkReadiness r = eval(List.of(HOME_PRIVATE),
                List.of(ALLOW_PRIVATE, new FirewallRule("Block", "Any", "True")));
        assertFalse(r.ready());
        assertTrue(r.firewallBlocked());
    }

    @Test
    void disabledRules_areIgnored_andAnyCoversPrivate() {
        assertFalse(eval(List.of(HOME_PRIVATE), List.of(new FirewallRule("Allow", "Private", "False"))).ready());
        assertTrue(eval(List.of(HOME_PRIVATE), List.of(new FirewallRule("Allow", "Any", "True"))).ready());
    }

    @Test
    void thirdPartyFirewall_isReported_withoutPretendingToKnowItsRules() {
        // Cas réel du 2026-10-17 : Windows prêt, mais Bitdefender filtre en plus avec ses propres règles.
        NetworkReadiness r = NetworkReadiness.evaluate(new NetworkSnapshot(true, "C:\\DM Loremind.exe",
                List.of(HOME_PRIVATE), List.of(ALLOW_PRIVATE), List.of("Bitdefender Pare-feu")));
        assertTrue(r.ready(), "Windows est prêt…");
        assertEquals(List.of("Bitdefender Pare-feu"), r.thirdPartyFirewalls(), "…mais le pare-feu tiers est signalé");
    }

    @Test
    void publicNetwork_withoutName_usesInterfaceAlias() {
        NetworkReadiness r = eval(List.of(new Network(null, "Wi-Fi", "Public")), List.of(ALLOW_PRIVATE));
        assertEquals(List.of("Wi-Fi"), r.publicNetworks());
    }

    // --- Linux ---

    @Test
    void linux_withoutFirewall_isReady() {
        NetworkReadiness r = NetworkReadiness.evaluate(NetworkSnapshot.linux("none", 8765, null));
        assertTrue(r.ready());
        assertEquals("linux", r.platform());
        assertNull(r.fixCommand());
    }

    @Test
    void linux_ufw_portUnknown_proposesTheCommand() {
        NetworkReadiness r = NetworkReadiness.evaluate(NetworkSnapshot.linux("ufw", 8765, null));
        assertFalse(r.ready());
        assertNull(r.portOpen());
        assertEquals("sudo ufw allow 8765/tcp", r.fixCommand());
    }

    @Test
    void linux_firewalld_closedThenOpen() {
        NetworkReadiness closed = NetworkReadiness.evaluate(NetworkSnapshot.linux("firewalld", 9000, false));
        assertFalse(closed.ready());
        assertEquals("sudo firewall-cmd --permanent --add-port=9000/tcp && sudo firewall-cmd --reload", closed.fixCommand());

        NetworkReadiness open = NetworkReadiness.evaluate(NetworkSnapshot.linux("firewalld", 9000, true));
        assertTrue(open.ready());
        assertNull(open.fixCommand());
    }
}

