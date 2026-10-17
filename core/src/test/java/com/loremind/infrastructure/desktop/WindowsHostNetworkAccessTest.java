package com.loremind.infrastructure.desktop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.remoteaccess.NetworkSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test de {@link WindowsHostNetworkAccess} : lecture du JSON de PowerShell (objet seul ou
 * tableau), contenu des scripts, et — sur Windows seulement — un vrai appel en lecture seule.
 */
class WindowsHostNetworkAccessTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parse_handlesSingleObjectsAndArrays() throws Exception {
        // ConvertTo-Json écrit un objet (pas un tableau) quand il n'y a qu'un élément.
        String json = "{\"networks\":{\"name\":\"Bbox\",\"alias\":\"Wi-Fi\",\"category\":\"Private\"},"
                + "\"rules\":[{\"action\":\"Allow\",\"profile\":\"Private\",\"enabled\":\"True\"},"
                + "{\"action\":\"Block\",\"profile\":\"Public\",\"enabled\":\"True\"}]}";

        NetworkSnapshot s = WindowsHostNetworkAccess.parseSnapshot(mapper, "C:\\app.exe", json);

        assertTrue(s.supported());
        assertEquals(1, s.networks().size());
        assertEquals("Private", s.networks().get(0).category());
        assertEquals(2, s.rules().size());
        assertEquals("Block", s.rules().get(1).action());
        assertTrue(s.thirdPartyFirewalls().isEmpty());
    }

    @Test
    void parse_readsThirdPartyFirewalls_asSingleValueOrArray() throws Exception {
        NetworkSnapshot one = WindowsHostNetworkAccess.parseSnapshot(mapper, "C:\\app.exe",
                "{\"networks\":[],\"rules\":[],\"thirdPartyFirewalls\":\"Bitdefender Pare-feu\"}");
        assertEquals(java.util.List.of("Bitdefender Pare-feu"), one.thirdPartyFirewalls());

        NetworkSnapshot two = WindowsHostNetworkAccess.parseSnapshot(mapper, "C:\\app.exe",
                "{\"networks\":[],\"rules\":[],\"thirdPartyFirewalls\":[\"Norton\",\"\"]}");
        assertEquals(java.util.List.of("Norton"), two.thirdPartyFirewalls());
    }

    @Test
    void parse_handlesEmptyLists_andEmptyOutput() throws Exception {
        NetworkSnapshot empty = WindowsHostNetworkAccess.parseSnapshot(mapper, "C:\\app.exe", "{\"networks\":[],\"rules\":[]}");
        assertTrue(empty.networks().isEmpty());
        assertTrue(empty.rules().isEmpty());

        NetworkSnapshot blank = WindowsHostNetworkAccess.parseSnapshot(mapper, "C:\\app.exe", "  ");
        assertTrue(blank.networks().isEmpty());
    }

    @Test
    void scripts_quoteThePath_andOnlyOpenPrivateNetworks() {
        String path = "C:\\Users\\l'MJ\\DM Loremind.exe";
        String allow = WindowsHostNetworkAccess.allowScript(path);

        assertTrue(allow.contains("'C:\\Users\\l''MJ\\DM Loremind.exe'"), "apostrophe doublée dans la chaîne PowerShell");
        assertTrue(allow.contains("-Profile Private"));
        assertFalse(allow.contains("Public"), "la règle ne doit jamais ouvrir les réseaux publics");
        assertTrue(allow.contains("Remove-NetFirewallRule"), "les blocages de CE programme sont retirés");
        assertTrue(WindowsHostNetworkAccess.inspectScript(path).contains("ConvertTo-Json"));
        assertTrue(WindowsHostNetworkAccess.inspectScript(path).contains("SecurityCenter2"));
    }

    @Test
    void encode_isUtf16LeBase64_asExpectedByEncodedCommand() {
        String encoded = WindowsHostNetworkAccess.encode("é");
        assertEquals("é", new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_16LE));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void inspect_readsTheRealSystem_onWindows() {
        NetworkSnapshot s = new WindowsHostNetworkAccess(mapper).inspect(8765);
        assertTrue(s.supported(), "PowerShell doit répondre en lecture seule sur Windows");
        assertNotNull(s.program());
        assertNotNull(s.thirdPartyFirewalls());
    }
}
