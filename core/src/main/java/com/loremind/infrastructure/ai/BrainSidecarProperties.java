package com.loremind.infrastructure.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Configuration du lancement du Brain (service IA Python) en SIDECAR, c.-a-d.
 * comme sous-processus du Core, en mode local-first.
 * <p>
 * En deploiement Docker, le Brain est un conteneur independant : ce mecanisme
 * est inactif (profil {@code local} uniquement). En application de bureau
 * empaquetee, il n'y a pas de Docker : le Core demarre lui-meme le Brain.
 *
 * @see BrainSidecar
 */
@Component
@Profile("local")
@ConfigurationProperties(prefix = "brain.sidecar")
public class BrainSidecarProperties {

    /** Active le lancement du Brain par le Core. */
    private boolean enabled = false;

    /**
     * Commande de lancement (programme + arguments). Vide = ne rien lancer
     * (cas du dev qui demarre le Brain a la main).
     * <ul>
     *   <li>Mode empaquete (jpackage) : chemin de l'exe PyInstaller, ex.
     *       {@code C:\Program Files\LoreMind\brain\loremind-brain.exe}</li>
     *   <li>Dev : {@code python,-m,uvicorn,app.main:app,--host,127.0.0.1,--port,8000}</li>
     * </ul>
     */
    private List<String> command = List.of();

    /**
     * Repertoire de travail du process Brain. Le Brain ecrit ses donnees (index
     * vectoriel, settings.json) sous {@code data/} RELATIF a ce dossier : on le
     * place donc sous loremind.home pour que tout vive au meme endroit.
     */
    private String workingDir;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public List<String> getCommand() { return command; }
    public void setCommand(List<String> command) { this.command = command; }

    public String getWorkingDir() { return workingDir; }
    public void setWorkingDir(String workingDir) { this.workingDir = workingDir; }
}
