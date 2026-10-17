package com.loremind.infrastructure.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.remoteaccess.NetworkSnapshot;
import com.loremind.domain.remoteaccess.ports.HostNetworkAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Accès au réseau et au pare-feu de Windows pour l'accès distant (mode bureau).
 * <p>
 * Lecture via PowerShell, sans droits administrateur. L'autorisation dans le pare-feu
 * passe par {@code Start-Process -Verb RunAs} : Windows affiche sa confirmation
 * administrateur (UAC) et l'utilisateur garde la main. La règle créée n'ouvre DM Loremind
 * qu'aux réseaux PRIVÉS ; un Wi-Fi classé « Public » reste à basculer par l'utilisateur
 * (réglage système, jamais modifié par l'application).
 * <p>
 * Les scripts sont passés en {@code -EncodedCommand} (UTF-16LE base64) : aucun problème de
 * guillemets ni d'accents. Le seul paramètre variable est le chemin de l'exécutable,
 * fourni par le système (pas par l'utilisateur), et échappé pour une chaîne PowerShell.
 */
@Component
@Profile("local")
public class WindowsHostNetworkAccess implements HostNetworkAccess {

    private static final Logger log = LoggerFactory.getLogger(WindowsHostNetworkAccess.class);

    static final String RULE_NAME = "DM Loremind (accès tablette)";
    private static final long INSPECT_TIMEOUT_S = 20;
    /** Le temps pour l'utilisateur de lire et de répondre à la fenêtre UAC. */
    private static final long ELEVATION_TIMEOUT_S = 180;
    /** Code Windows ERROR_CANCELLED : l'utilisateur a refusé la confirmation administrateur. */
    private static final int EXIT_CANCELLED = 1223;

    private final ObjectMapper objectMapper;

    public WindowsHostNetworkAccess(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public NetworkSnapshot inspect() {
        String program = currentProgram();
        if (!isWindows() || program == null) return NetworkSnapshot.unsupported();
        try {
            String json = runPowerShell(inspectScript(program), INSPECT_TIMEOUT_S).output();
            return parseSnapshot(objectMapper, program, json);
        } catch (IOException | RuntimeException e) {
            log.warn("Accès distant : lecture du réseau / pare-feu impossible : {}", e.getMessage());
            return NetworkSnapshot.unsupported();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return NetworkSnapshot.unsupported();
        }
    }

    @Override
    public FirewallFixOutcome allowThroughFirewall() {
        String program = currentProgram();
        if (!isWindows() || program == null) return FirewallFixOutcome.UNSUPPORTED;
        String outer = "try {\n"
                + "  $p = Start-Process -FilePath 'powershell.exe' -Verb RunAs -WindowStyle Hidden -Wait -PassThru "
                + "-ArgumentList @('-NoProfile','-NonInteractive','-ExecutionPolicy','Bypass','-EncodedCommand','"
                + encode(allowScript(program)) + "')\n"
                + "  exit $p.ExitCode\n"
                + "} catch { exit " + EXIT_CANCELLED + " }\n";
        try {
            int exit = runPowerShell(outer, ELEVATION_TIMEOUT_S).exitCode();
            FirewallFixOutcome outcome = exit == 0 ? FirewallFixOutcome.APPLIED
                    : exit == EXIT_CANCELLED ? FirewallFixOutcome.CANCELLED : FirewallFixOutcome.FAILED;
            log.info("Accès distant : autorisation pare-feu → {}", outcome);
            return outcome;
        } catch (IOException | RuntimeException e) {
            log.warn("Accès distant : autorisation pare-feu impossible : {}", e.getMessage());
            return FirewallFixOutcome.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return FirewallFixOutcome.FAILED;
        }
    }

    @Override
    public void openNetworkSettings() {
        if (!isWindows()) return;
        try {
            // « État du réseau » : le réseau connecté et son bouton « Propriétés » (type de profil).
            new ProcessBuilder("cmd", "/c", "start", "", "ms-settings:network-status").start();
        } catch (IOException e) {
            log.warn("Ouverture des réglages réseau impossible : {}", e.getMessage());
        }
    }

    // --- Scripts --------------------------------------------------------------------------

    /** Réseaux connectés + règles entrantes visant ce programme (chemins avec %VARIABLES% compris). */
    static String inspectScript(String program) {
        return "$ErrorActionPreference = 'SilentlyContinue'\n"
                // Sortie en UTF-8 : sinon la page de codes de la console abîme les noms de réseau accentués.
                + "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8\n"
                + "$p = '" + psQuote(program) + "'\n"
                + "$nets =@(Get-NetConnectionProfile | ForEach-Object { [pscustomobject]@{ name = $_.Name; "
                + "alias = $_.InterfaceAlias; category = $_.NetworkCategory.ToString() } })\n"
                + "$rules = @(Get-NetFirewallApplicationFilter | Where-Object { $_.Program -and "
                + "([Environment]::ExpandEnvironmentVariables($_.Program) -ieq $p) } | Get-NetFirewallRule | "
                + "Where-Object { $_.Direction.ToString() -eq 'Inbound' } | ForEach-Object { [pscustomobject]@{ "
                + "action = $_.Action.ToString(); profile = $_.Profile.ToString(); enabled = $_.Enabled.ToString() } })\n"
                + "[pscustomobject]@{ networks = $nets; rules = $rules } | ConvertTo-Json -Depth 4 -Compress\n";
    }

    /**
     * Exécuté ÉLEVÉ : retire les blocages entrants visant CE programme (créés par un « Annuler »
     * dans la fenêtre du pare-feu, ils l'emporteraient sur toute autorisation), remplace notre
     * propre règle si elle existe, puis autorise sur les réseaux privés uniquement.
     */
    static String allowScript(String program) {
        String name = psQuote(RULE_NAME);
        return "try {\n"
                + "  $p = '" + psQuote(program) + "'\n"
                + "  Get-NetFirewallApplicationFilter | Where-Object { $_.Program -and "
                + "([Environment]::ExpandEnvironmentVariables($_.Program) -ieq $p) } | Get-NetFirewallRule | "
                + "Where-Object { $_.Direction.ToString() -eq 'Inbound' -and $_.Action.ToString() -eq 'Block' } | "
                + "Remove-NetFirewallRule\n"
                + "  Get-NetFirewallRule -DisplayName '" + name + "' -ErrorAction SilentlyContinue | Remove-NetFirewallRule\n"
                + "  New-NetFirewallRule -DisplayName '" + name + "' -Direction Inbound -Program $p -Action Allow "
                + "-Profile Private -ErrorAction Stop | Out-Null\n"
                + "  exit 0\n"
                + "} catch { exit 2 }\n";
    }

    static NetworkSnapshot parseSnapshot(ObjectMapper mapper, String program, String json) throws IOException {
        JsonNode root = mapper.readTree(json == null || json.isBlank() ? "{}" : json.trim());
        List<NetworkSnapshot.Network> networks = new ArrayList<>();
        for (JsonNode n : array(root.path("networks"))) {
            networks.add(new NetworkSnapshot.Network(text(n, "name"), text(n, "alias"), text(n, "category")));
        }
        List<NetworkSnapshot.FirewallRule> rules = new ArrayList<>();
        for (JsonNode r : array(root.path("rules"))) {
            rules.add(new NetworkSnapshot.FirewallRule(text(r, "action"), text(r, "profile"), text(r, "enabled")));
        }
        return new NetworkSnapshot(true, program, networks, rules);
    }

    /** ConvertTo-Json écrit un objet seul (pas un tableau) quand la liste n'a qu'un élément. */
    private static Iterable<JsonNode> array(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return List.of();
        return node.isArray() ? node : List.of(node);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    static String psQuote(String value) {
        return value.replace("'", "''");
    }

    static String encode(String script) {
        return Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
    }

    // --- Exécution ------------------------------------------------------------------------

    private record Result(int exitCode, String output) {}

    private static Result runPowerShell(String script, long timeoutSeconds) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-EncodedCommand", encode(script))
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        process.getOutputStream().close();
        // Lecture en parallèle : un tampon de sortie plein bloquerait le processus avant sa fin.
        CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> {
            try (InputStream out = process.getInputStream()) {
                return new String(out.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "";
            }
        });
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("PowerShell n'a pas répondu en " + timeoutSeconds + " s");
        }
        return new Result(process.exitValue(), output.join());
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    /** Exécutable du processus courant : DM Loremind.exe en bureau (java.exe en développement). */
    private static String currentProgram() {
        return ProcessHandle.current().info().command().orElse(null);
    }
}
