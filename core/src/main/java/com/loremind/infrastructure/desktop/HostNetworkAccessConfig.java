package com.loremind.infrastructure.desktop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.remoteaccess.NetworkSnapshot;
import com.loremind.domain.remoteaccess.ports.HostNetworkAccess;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Locale;

/**
 * Mode bureau : choisit l'accès au pare-feu selon le système — Windows (PowerShell, UAC),
 * Linux (ufw / firewalld, lecture sans root), sinon rien à vérifier (macOS…).
 */
@Configuration
@Profile("local")
public class HostNetworkAccessConfig {

    @Bean
    public HostNetworkAccess hostNetworkAccess(ObjectMapper objectMapper) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.startsWith("windows")) return new WindowsHostNetworkAccess(objectMapper);
        if (os.contains("linux")) return new LinuxHostNetworkAccess();
        return new UnsupportedHostNetworkAccess();
    }

    /** Système sans vérification prise en charge : le front n'affiche pas de diagnostic. */
    static final class UnsupportedHostNetworkAccess implements HostNetworkAccess {
        @Override public NetworkSnapshot inspect(int port) { return NetworkSnapshot.unsupported(); }
        @Override public FirewallFixOutcome allowThroughFirewall() { return FirewallFixOutcome.UNSUPPORTED; }
        @Override public void openNetworkSettings() { /* rien à ouvrir */ }
    }
}
