package com.loremind;

import com.loremind.infrastructure.desktop.DesktopSingleInstance;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Classe principale de l'application LoreMind.
 * Point d'entrée Spring Boot qui démarre l'application.
 */
@SpringBootApplication
@EnableScheduling
public class LoreMindApplication {

    public static void main(String[] args) {
        // Mode bureau (profil "local") : garde-fou instance unique. Si l'app
        // tourne deja, on ouvre juste le navigateur et on sort proprement (code 0)
        // au lieu de demarrer un 2e serveur qui echouerait sur le verrou H2 — ce
        // qui evite le trompeur « Failed to launch JVM » du launcher jpackage.
        boolean local = DesktopSingleInstance.isLocalProfile(args);
        if (local && !DesktopSingleInstance.tryAcquire()) {
            DesktopSingleInstance.openAppInBrowser();
            return;
        }
        SpringApplication app = new SpringApplication(LoreMindApplication.class);
        if (local) {
            // Mode bureau : on a besoin d'AWT (icone de la zone de notification,
            // cf. SystemTrayManager). Spring Boot force headless=true par defaut,
            // ce qui leverait HeadlessException — on le desactive ici. En mode
            // serveur/Docker, on reste en headless (defaut), aucun impact.
            app.setHeadless(false);
        }
        app.run(args);
    }
}
