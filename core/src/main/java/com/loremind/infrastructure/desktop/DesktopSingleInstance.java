package com.loremind.infrastructure.desktop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Utilitaires du mode BUREAU (profil "local", application empaquetee jpackage).
 * <p>
 * Resout deux problemes specifiques au lancement par double-clic :
 * <ol>
 *   <li><b>Instance unique</b> : un serveur web n'ouvre pas de fenetre. Un
 *       utilisateur qui ne voit rien re-double-clique souvent — la 2e instance
 *       trouvait la base H2 verrouillee et sortait en erreur, ce que le launcher
 *       jpackage traduit par un trompeur « Failed to launch JVM ». On detecte
 *       donc tres tot (avant Spring) qu'une instance tourne deja, et on se
 *       contente d'ouvrir le navigateur puis de sortir proprement (code 0).</li>
 *   <li><b>Ouverture du navigateur</b> : l'app n'ayant pas de fenetre native,
 *       on ouvre le navigateur par defaut sur l'URL locale pour que l'utilisateur
 *       voie l'application immediatement.</li>
 * </ol>
 * Volontairement sans dependance a {@code java.awt.Desktop} : ce module
 * ({@code java.desktop}) pourrait etre absent du runtime reduit par jlink.
 * On passe donc par la commande systeme d'ouverture d'URL.
 */
// S4036 : app desktop locale mono-utilisateur — lancement d'utilitaires systeme via PATH
// assume, sans contexte d'elevation ni d'input externe.
@SuppressWarnings("java:S4036")
public final class DesktopSingleInstance {

    // Utilisé avant SpringApplication.run() : Logback démarre en config console par
    // défaut — même destination que l'ancien System.err (cf. DesktopUserConfig).
    private static final Logger log = LoggerFactory.getLogger(DesktopSingleInstance.class);

    private static final String OS_NAME_PROPERTY = "os.name";
    /** Ouvre URL/fichier/dossier avec l'application par défaut sur Linux. */
    private static final String XDG_OPEN = "xdg-open";

    /**
     * Conserve le CHANNEL ouvert pour toute la duree de vie du process : un FileLock
     * reste detenu tant que son channel est ouvert (le verrou est libere a la fermeture
     * du channel ou a la sortie de la JVM, pas au GC de l'objet FileLock).
     */
    @SuppressWarnings("unused")
    private static FileChannel lockChannel;

    private DesktopSingleInstance() {}

    /** Vrai si le profil Spring actif inclut "local" (cas de l'app de bureau). */
    public static boolean isLocalProfile(String[] args) {
        String prop = System.getProperty("spring.profiles.active", "");
        String env = System.getenv().getOrDefault("SPRING_PROFILES_ACTIVE", "");
        if (containsLocal(prop) || containsLocal(env)) return true;
        if (args != null) {
            for (String a : args) {
                if (a.startsWith("--spring.profiles.active=") && containsLocal(a)) return true;
            }
        }
        return false;
    }

    private static boolean containsLocal(String s) {
        for (String p : s.split("[,=]")) {
            if (p.trim().equals("local")) return true;
        }
        return false;
    }

    /**
     * Tente de prendre le verrou d'instance unique (fichier {@code .instance.lock}
     * sous loremind.home). Retourne {@code true} si on est la PREMIERE instance
     * (verrou obtenu, on doit demarrer le serveur), {@code false} si une autre
     * instance le detient deja.
     * <p>
     * En cas d'erreur d'E/S inattendue, on retourne {@code true} (degradation
     * prudente : mieux vaut tenter de demarrer que bloquer l'app).
     */
    public static boolean tryAcquire() {
        try {
            Path dir = loremindHome();
            Files.createDirectories(dir);
            Path lockFile = dir.resolve(".instance.lock");
            lockChannel = FileChannel.open(lockFile,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock = lockChannel.tryLock();
            return lock != null; // null = deja verrouille par une autre instance
        } catch (IOException e) {
            log.warn("[Desktop] Verrou d'instance indisponible ({}) — on tente de demarrer quand meme.",
                    e.getMessage());
            return true;
        }
    }

    /** Ouvre le navigateur par defaut sur l'URL de l'application locale (port reel). */
    public static void openAppInBrowser() {
        openUrl("http://localhost:" + DesktopUserConfig.runningPort() + "/");
    }

    /** Ouvre le navigateur par defaut sur une URL quelconque (sans dependance AWT). */
    public static void openUrl(String url) {
        try {
            String os = System.getProperty(OS_NAME_PROPERTY, "").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                // rundll32 : ouverture d'URL fiable sans dependance graphique Java.
                pb = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url);
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", url);
            } else {
                pb = new ProcessBuilder(XDG_OPEN, url);
            }
            pb.start();
        } catch (IOException e) {
            log.warn("[Desktop] Impossible d'ouvrir le navigateur sur {} : {}. Ouvrez-le manuellement.",
                    url, e.getMessage());
        }
    }

    /** Ouvre un dossier dans le gestionnaire de fichiers du systeme. */
    public static void openFolder(Path dir) {
        try {
            Files.createDirectories(dir);
            String os = System.getProperty(OS_NAME_PROPERTY, "").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("explorer.exe", dir.toString());
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", dir.toString());
            } else {
                pb = new ProcessBuilder(XDG_OPEN, dir.toString());
            }
            pb.start();
        } catch (IOException e) {
            log.warn("[Desktop] Ouverture du dossier impossible : {}", e.getMessage());
        }
    }

    /** Ouvre un fichier texte dans l'editeur par defaut (Bloc-notes sous Windows). */
    public static void openInEditor(Path file) {
        try {
            String os = System.getProperty(OS_NAME_PROPERTY, "").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                // notepad : toujours present, ouvre proprement un .properties
                // (dont l'association par defaut n'est pas garantie).
                pb = new ProcessBuilder("notepad.exe", file.toString());
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", "-t", file.toString());
            } else {
                pb = new ProcessBuilder(XDG_OPEN, file.toString());
            }
            pb.start();
        } catch (IOException e) {
            log.warn("[Desktop] Ouverture du fichier impossible : {}", e.getMessage());
        }
    }

    private static Path loremindHome() {
        String home = System.getProperty("loremind.home");
        if (home != null && !home.isBlank()) return Path.of(home);
        return Path.of(System.getProperty("user.home"), ".loremind");
    }
}
