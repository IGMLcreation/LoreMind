package com.loremind.infrastructure.desktop;

import com.loremind.domain.remoteaccess.NetworkSnapshot;
import com.loremind.domain.remoteaccess.ports.HostNetworkAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test de {@link LinuxHostNetworkAccess} : lecture de ufw.conf, interprétation de
 * {@code firewall-cmd --query-port}, et cas ufw actif sans droits root (port inconnu).
 */
class LinuxHostNetworkAccessTest {

    @Test
    void ufwEnabled_readsTheConfigFile() {
        assertTrue(LinuxHostNetworkAccess.ufwEnabled("# commentaire\nENABLED=yes\nLOGLEVEL=low"));
        assertTrue(LinuxHostNetworkAccess.ufwEnabled("ENABLED=\"yes\""));
        assertFalse(LinuxHostNetworkAccess.ufwEnabled("ENABLED=no"));
        assertFalse(LinuxHostNetworkAccess.ufwEnabled("#ENABLED=yes"));
        assertFalse(LinuxHostNetworkAccess.ufwEnabled(null));
    }

    @Test
    void firewalldQuery_yesNoOrUnknown() {
        assertEquals(Boolean.TRUE, LinuxHostNetworkAccess.interpretQuery(0, "yes\n"));
        assertEquals(Boolean.FALSE, LinuxHostNetworkAccess.interpretQuery(1, "no\n"));
        // Refus D-Bus / commande absente : on ne prétend rien.
        assertNull(LinuxHostNetworkAccess.interpretQuery(-1, ""));
        assertNull(LinuxHostNetworkAccess.interpretQuery(11, "Authorization failed."));
    }

    @Test
    void activeUfw_meansPortUnknownWithoutRoot(@TempDir Path dir) throws Exception {
        Path conf = dir.resolve("ufw.conf");
        Files.writeString(conf, "ENABLED=yes\n");

        NetworkSnapshot s = new LinuxHostNetworkAccess(conf).inspect(8765);

        assertTrue(s.supported());
        assertEquals("linux", s.platform());
        assertEquals("ufw", s.linuxFirewall());
        assertEquals(8765, s.port());
        assertNull(s.portOpen());
    }

    @Test
    void noOneClickFixOnLinux() {
        assertEquals(HostNetworkAccess.FirewallFixOutcome.UNSUPPORTED,
                new LinuxHostNetworkAccess(Path.of("absent")).allowThroughFirewall());
    }
}
