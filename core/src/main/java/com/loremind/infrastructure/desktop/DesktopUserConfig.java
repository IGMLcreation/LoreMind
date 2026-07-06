package com.loremind.infrastructure.desktop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Configuration UTILISATEUR du mode bureau, dans un fichier éditable
 * {@code ~/.loremind/loremind.properties} (port HTTP, identifiants admin).
 * <p>
 * Pourquoi un fichier et pas l'UI : le port et le mot de passe admin sont requis
 * AVANT que le serveur (et donc l'UI web) ne démarre — impossible de les régler
 * depuis l'app elle-même. Un fichier texte simple, lu au démarrage, est le
 * pattern standard d'une app de bureau. Les modifs prennent effet au prochain
 * lancement.
 * <p>
 * - {@code server.port} : lu ici (résolution du port) ET par Spring.
 * - {@code admin.username} / {@code admin.password} : chargés par Spring via
 *   {@code spring.config.import} (cf. application-local.properties) — ils
 *   surchargent les défauts du profil local.
 * <p>
 * Repli de port : si le port configuré est occupé, on en choisit un libre
 * automatiquement (évite un échec de démarrage cryptique sur conflit de port)
 * et on écrit le port réellement utilisé dans {@code ~/.loremind/.port} pour que
 * l'ouverture du navigateur (instance gagnante OU 2e double-clic) cible la bonne URL.
 */
public final class DesktopUserConfig {

    // Utilisé AVANT SpringApplication.run() : Logback s'initialise avec sa config
    // par défaut (console), puis Spring reprend la main — même destination que
    // l'ancien System.out, mais horodaté et uniforme avec le reste des logs.
    private static final Logger log = LoggerFactory.getLogger(DesktopUserConfig.class);

    /** Clé partagée fichier utilisateur / propriété système / Spring. */
    private static final String SERVER_PORT_PROPERTY = "server.port";

    private DesktopUserConfig() {}

    private static final String DEFAULT_TEMPLATE = """
            # ============================================================
            #  Configuration locale de LoreMind (mode bureau)
            # ------------------------------------------------------------
            #  Modifiez ces valeurs puis RELANCEZ LoreMind pour appliquer.
            # ============================================================

            # Port HTTP local de l'application (http://localhost:<port>).
            # Si ce port est deja occupe par une autre application, LoreMind
            # choisira automatiquement un autre port libre au demarrage.
            server.port=8080

            # Identifiants de la page Parametres (acces admin).
            # Accessible uniquement en local sur cette machine.
            admin.username=admin
            admin.password=admin
            """;

    /** Chemin du fichier de config utilisateur (pour l'ouvrir depuis le menu systray). */
    public static Path getConfigFile() {
        return configFile();
    }

    /** Dossier de données/config de l'instance ({@code ~/.loremind}). */
    public static Path getHomeDir() {
        return loremindHome();
    }

    /** Crée le fichier de config avec des valeurs par défaut commentées s'il n'existe pas. */
    public static void ensureExists() {
        Path file = configFile();
        if (Files.exists(file)) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, DEFAULT_TEMPLATE);
            log.info("[Desktop] Config utilisateur creee : {}", file);
        } catch (IOException e) {
            log.warn("[Desktop] Impossible de creer {} : {} — defauts utilises (port 8080, admin/admin).",
                    file, e.getMessage());
        }
    }

    /**
     * Résout le port à utiliser : le port configuré s'il est libre, sinon un port
     * libre choisi automatiquement. Publie le résultat dans la propriété système
     * {@code server.port} (que Spring lira en priorité) et dans {@code ~/.loremind/.port}.
     *
     * @return le port effectivement retenu.
     */
    public static int resolveAndPublishPort() {
        int wanted = configuredPort();
        int chosen = isPortFree(wanted) ? wanted : findFreePort(wanted);
        if (chosen != wanted) {
            log.info("[Desktop] Port {} occupe — repli sur le port libre {}.", wanted, chosen);
        }
        System.setProperty(SERVER_PORT_PROPERTY, String.valueOf(chosen));
        try {
            Files.writeString(portFile(), String.valueOf(chosen));
        } catch (IOException e) {
            log.warn("[Desktop] Ecriture du port impossible ({}).", e.getMessage());
        }
        return chosen;
    }

    /**
     * Port sur lequel l'application répond réellement (pour ouvrir le navigateur).
     * Ordre : propriété système (instance gagnante) → fichier .port (2e double-clic)
     * → port configuré → 8080.
     */
    public static int runningPort() {
        String sys = System.getProperty(SERVER_PORT_PROPERTY);
        if (sys != null && !sys.isBlank()) {
            try { return Integer.parseInt(sys.trim()); } catch (NumberFormatException ignored) { /* fallthrough */ }
        }
        try {
            Path p = portFile();
            if (Files.exists(p)) {
                return Integer.parseInt(Files.readString(p).trim());
            }
        } catch (IOException | NumberFormatException ignored) { /* fallthrough */ }
        return configuredPort();
    }

    /** Port lu dans le fichier de config utilisateur (défaut 8080). */
    private static int configuredPort() {
        Path file = configFile();
        if (Files.exists(file)) {
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
                String v = props.getProperty(SERVER_PORT_PROPERTY);
                if (v != null && !v.isBlank()) {
                    return Integer.parseInt(v.trim());
                }
            } catch (IOException | NumberFormatException ignored) { /* défaut */ }
        }
        return 8080;
    }

    private static boolean isPortFree(int port) {
        try (ServerSocket s = new ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"))) {
            s.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static int findFreePort(int fallbackIfNone) {
        try (ServerSocket s = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            return s.getLocalPort();
        } catch (IOException e) {
            return fallbackIfNone; // tres improbable ; on retente le port voulu
        }
    }

    private static Path configFile() {
        return loremindHome().resolve("loremind.properties");
    }

    private static Path portFile() {
        return loremindHome().resolve(".port");
    }

    private static Path loremindHome() {
        String home = System.getProperty("loremind.home");
        if (home != null && !home.isBlank()) return Path.of(home);
        return Path.of(System.getProperty("user.home"), ".loremind");
    }
}
