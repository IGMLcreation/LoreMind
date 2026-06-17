package com.loremind.infrastructure.desktop;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;

/**
 * Icone dans la zone de notification (barre des taches) en mode bureau
 * (profil "local"). Donne a l'utilisateur un controle visible de l'application,
 * qui tourne sinon en serveur sans fenetre : impossible autrement de la fermer
 * proprement (fermer l'onglet du navigateur laisse le Core et le Brain tourner).
 * <p>
 * Menu : « Ouvrir LoreMind » (ouvre le navigateur) et « Quitter LoreMind »
 * (ferme le contexte Spring — ce qui declenche le {@code @PreDestroy} de
 * {@link com.loremind.infrastructure.ai.BrainSidecar} et arrete donc aussi le
 * Brain — puis termine le process).
 * <p>
 * Necessite que le mode headless soit desactive (cf. LoreMindApplication.main,
 * qui appelle {@code setHeadless(false)} en profil local). Le module
 * {@code java.desktop} est embarque dans le runtime jpackage.
 */
@Component
@Profile("local")
public class SystemTrayManager {

    private static final Logger log = LoggerFactory.getLogger(SystemTrayManager.class);

    private final ConfigurableApplicationContext context;
    private final DesktopUpdateService updateService;
    private TrayIcon trayIcon;
    private PopupMenu popup;

    public SystemTrayManager(ConfigurableApplicationContext context,
                             DesktopUpdateService updateService) {
        this.context = context;
        this.updateService = updateService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void install() {
        if (!SystemTray.isSupported()) {
            log.warn("[Tray] Zone de notification non supportee sur ce systeme — "
                    + "pas d'icone. Pour quitter : menu de la fenetre console, ou gestionnaire des taches.");
            return;
        }
        try {
            popup = new PopupMenu();

            MenuItem open = new MenuItem("Ouvrir LoreMind");
            open.addActionListener(e -> DesktopSingleInstance.openAppInBrowser());
            popup.add(open);

            popup.addSeparator();

            MenuItem quit = new MenuItem("Quitter LoreMind");
            quit.addActionListener(e -> quit());
            popup.add(quit);

            trayIcon = new TrayIcon(createIcon(), "LoreMind", popup);
            trayIcon.setImageAutoSize(true);
            // Double-clic sur l'icone : ouvre l'application dans le navigateur.
            trayIcon.addActionListener(e -> DesktopSingleInstance.openAppInBrowser());

            SystemTray.getSystemTray().add(trayIcon);
            log.info("[Tray] Icone installee dans la zone de notification.");

            // Verification de mise a jour en arriere-plan (appel reseau GitHub) :
            // ne bloque pas le demarrage ; met a jour le menu/notifie si dispo.
            new Thread(this::checkForUpdate, "loremind-update-check").start();
        } catch (Exception e) {
            // Echec non bloquant : l'app reste utilisable, seul le confort de l'icone manque.
            log.warn("[Tray] Installation de l'icone impossible : {}", e.getMessage());
        }
    }

    /**
     * Interroge GitHub Releases ; si une version plus recente existe, ajoute un
     * item de menu « Telecharger » et affiche une bulle de notification. L'item
     * ouvre la page de la release dans le navigateur (telechargement manuel du
     * nouvel installeur).
     */
    private void checkForUpdate() {
        updateService.checkForUpdate().ifPresent(info -> {
            String label = "⬇ Telecharger la mise a jour (v" + info.latestVersion() + ")";
            MenuItem update = new MenuItem(label);
            update.addActionListener(e -> DesktopSingleInstance.openUrl(info.releaseUrl()));
            // En tete de menu pour la visibilite, suivi d'un separateur.
            popup.insert(update, 0);
            popup.insertSeparator(1);

            trayIcon.displayMessage(
                    "LoreMind — mise a jour disponible",
                    "Version " + info.latestVersion() + " disponible (vous avez " + info.currentVersion()
                            + "). Menu de l'icone → Telecharger.",
                    TrayIcon.MessageType.INFO);
        });
    }

    /**
     * Arret propre depuis le menu « Quitter » : on retire l'icone puis on ferme
     * le contexte Spring dans un thread dedie (l'action s'execute sur l'EDT AWT ;
     * fermer le contexte + arreter Tomcat/Brain depuis l'EDT pourrait le bloquer).
     */
    private void quit() {
        log.info("[Tray] Demande de fermeture de l'application.");
        new Thread(() -> {
            int code = SpringApplication.exit(context, () -> 0);
            System.exit(code);
        }, "loremind-shutdown").start();
    }

    /** Retire l'icone si le contexte se ferme par une autre voie (ex. Ctrl+C). */
    @PreDestroy
    public void remove() {
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
    }

    /**
     * Genere une petite icone (carre arrondi violet « L ») sans dependre d'un
     * fichier image — robuste quel que soit l'empaquetage.
     */
    private Image createIcon() {
        int size = 16;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x8B, 0x5C, 0xF6)); // violet de marque LoreMind
        g.fillRoundRect(0, 0, size, size, 5, 5);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.drawString("L", 4, 13);
        g.dispose();
        return img;
    }
}
