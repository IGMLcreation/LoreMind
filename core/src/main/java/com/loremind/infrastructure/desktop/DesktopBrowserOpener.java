package com.loremind.infrastructure.desktop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.awt.SplashScreen;

/**
 * En mode bureau (profil "local"), ouvre le navigateur par defaut sur
 * l'application des que le serveur est pret. L'app n'ayant pas de fenetre
 * native, c'est ce qui donne a l'utilisateur un retour visuel immediat apres
 * le double-clic.
 * <p>
 * Concerne uniquement l'instance qui a effectivement demarre le serveur :
 * l'instance « perdante » du verrou unique ouvre le navigateur des le {@code main}
 * puis sort (cf. {@link DesktopSingleInstance}).
 */
@Component
@Profile("local")
public class DesktopBrowserOpener {

    private static final Logger log = LoggerFactory.getLogger(DesktopBrowserOpener.class);

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        // Ferme le splash natif (affiche par la JVM via -splash des le double-clic)
        // juste avant d'ouvrir le navigateur : le relais visuel est assure.
        closeSplash();
        log.info("[Desktop] Application prete — ouverture du navigateur.");
        DesktopSingleInstance.openAppInBrowser();
    }

    /**
     * Ferme l'ecran de demarrage si l'app a ete lancee avec {@code -splash:...}
     * (cas des installeurs jpackage). No-op silencieux sinon (lancement dev sans
     * splash, ou environnement sans affichage).
     */
    private void closeSplash() {
        try {
            SplashScreen splash = SplashScreen.getSplashScreen();
            if (splash != null) {
                splash.close();
            }
        } catch (Exception e) {
            log.debug("[Desktop] Pas de splash a fermer : {}", e.getMessage());
        }
    }
}
