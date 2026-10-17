package com.loremind.infrastructure.desktop;

import com.loremind.domain.remoteaccess.NetworkSnapshot;
import com.loremind.domain.remoteaccess.ports.HostNetworkAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Pare-feu sous Linux (Debian, Ubuntu, Fedora…) pour l'accès distant, SANS droits root.
 * <p>
 * Ce qu'on peut savoir sans root : quel pare-feu est actif — {@code ufw} via
 * {@code /etc/ufw/ufw.conf} (lisible par tous), {@code firewalld} via {@code systemctl} — et,
 * pour firewalld seulement, si le port est ouvert ({@code firewall-cmd --query-port}, autorisé
 * aux utilisateurs). Les règles d'ufw ne se lisent qu'en root : l'état du port reste alors
 * inconnu et le front affiche la commande à lancer. Pas d'autorisation en un clic : sur un
 * serveur sans écran, aucune fenêtre ne peut demander le mot de passe administrateur.
 * <p>
 * Choisie au démarrage par {@code HostNetworkAccessConfig} sous Linux.
 */
public class LinuxHostNetworkAccess implements HostNetworkAccess {

    private static final Logger log = LoggerFactory.getLogger(LinuxHostNetworkAccess.class);
    private static final long COMMAND_TIMEOUT_S = 10;

    static final String UFW = "ufw";
    static final String FIREWALLD = "firewalld";
    static final String NONE = "none";

    private final Path ufwConf;

    public LinuxHostNetworkAccess() {
        this(Path.of("/etc/ufw/ufw.conf"));
    }

    LinuxHostNetworkAccess(Path ufwConf) {
        this.ufwConf = ufwConf;
    }

    @Override
    public NetworkSnapshot inspect(int port) {
        if (ufwEnabled(readQuietly(ufwConf))) {
            // Règles d'ufw illisibles sans root : état du port inconnu.
            return NetworkSnapshot.linux(UFW, port, null);
        }
        if ("active".equals(run(List.of("systemctl", "is-active", FIREWALLD)).output().trim())) {
            return NetworkSnapshot.linux(FIREWALLD, port, firewalldPortOpen(port));
        }
        return NetworkSnapshot.linux(NONE, port, null);
    }

    @Override
    public FirewallFixOutcome allowThroughFirewall() {
        return FirewallFixOutcome.UNSUPPORTED;
    }

    @Override
    public void openNetworkSettings() {
        // Pas de réglage réseau « Public / Privé » à basculer sous Linux.
    }

    /** {@code firewall-cmd --query-port} : « yes » (0) / « no » (1) ; autre = inconnu. */
    private Boolean firewalldPortOpen(int port) {
        Result r = run(List.of("firewall-cmd", "--query-port=" + port + "/tcp"));
        return interpretQuery(r.exitCode(), r.output());
    }

    static Boolean interpretQuery(int exitCode, String output) {
        String out = output == null ? "" : output.trim().toLowerCase(Locale.ROOT);
        if (exitCode == 0 && out.startsWith("yes")) return true;
        if (exitCode == 1 && out.startsWith("no")) return false;
        return null;
    }

    /** {@code ENABLED=yes} dans ufw.conf (guillemets et casse tolérés). */
    static boolean ufwEnabled(String conf) {
        if (conf == null) return false;
        return conf.lines()
                .map(String::trim)
                .filter(l -> !l.startsWith("#"))
                .map(l -> l.replace("\"", "").replace("'", "").replace(" ", ""))
                .anyMatch(l -> l.equalsIgnoreCase("ENABLED=yes"));
    }

    private static String readQuietly(Path file) {
        try {
            return Files.isReadable(file) ? Files.readString(file, StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private record Result(int exitCode, String output) {}

    /** Commande système sans shell (pas d'interprétation), avec délai ; absente → code -1. */
    private static Result run(List<String> command) {
        try {
            Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
            p.getOutputStream().close();
            if (!p.waitFor(COMMAND_TIMEOUT_S, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return new Result(-1, "");
            }
            return new Result(p.exitValue(), new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.debug("Commande {} indisponible : {}", command.get(0), e.getMessage());
            return new Result(-1, "");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(-1, "");
        }
    }
}
