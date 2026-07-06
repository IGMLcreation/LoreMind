package com.loremind.infrastructure.ai;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Lance le Brain (service IA Python) comme SOUS-PROCESSUS du Core, en mode
 * local-first (application de bureau empaquetee, sans Docker).
 * <p>
 * Cycle de vie calque sur celui du Core :
 * <ul>
 *   <li>demarrage : a {@link ApplicationReadyEvent} (le serveur HTTP du Core
 *       est deja pret) ;</li>
 *   <li>arret : a {@link PreDestroy} (fermeture du contexte Spring) — on arrete
 *       proprement le Brain pour ne pas laisser de process orphelin.</li>
 * </ul>
 * <p>
 * Tolerance aux pannes : si le Brain ne peut pas etre lance (exe absent,
 * commande non configuree...), on LOGGUE sans faire echouer le Core. L'app
 * reste utilisable (Lore, Campagnes, Systeme de jeu) ; seules les fonctions IA
 * sont indisponibles jusqu'a correction.
 */
@Component
@Profile("local")
public class BrainSidecar {

    private static final Logger log = LoggerFactory.getLogger(BrainSidecar.class);

    private final BrainSidecarProperties props;
    private final String internalSecret;

    private final AtomicReference<Process> process = new AtomicReference<>();

    public BrainSidecar(BrainSidecarProperties props,
                        @Value("${brain.internal-secret:}") String internalSecret) {
        this.props = props;
        this.internalSecret = internalSecret;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!props.isEnabled()) {
            log.info("[Brain] Sidecar desactive (brain.sidecar.enabled=false).");
            return;
        }
        if (props.getCommand() == null || props.getCommand().isEmpty()) {
            log.warn("[Brain] Aucune commande configuree (brain.sidecar.command) : "
                    + "le Brain n'est pas lance. Les fonctions IA seront indisponibles.");
            return;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(props.getCommand());
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);

            File workingDir = resolveWorkingDir();
            if (workingDir != null) {
                pb.directory(workingDir);
            }

            // Secret partage Core <-> Brain : le Brain est fail-closed sans lui.
            // (cf. Settings.internal_shared_secret cote Python -> env INTERNAL_SHARED_SECRET)
            pb.environment().put("INTERNAL_SHARED_SECRET", internalSecret);

            Process started = pb.start();
            this.process.set(started);
            log.info("[Brain] Sidecar demarre (pid={}, cwd={}).",
                    started.pid(), workingDir != null ? workingDir : "<heritee>");
        } catch (IOException e) {
            log.error("[Brain] Echec du lancement du sidecar (commande={}). "
                            + "Les fonctions IA seront indisponibles. Cause : {}",
                    props.getCommand(), e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        Process p = this.process.get();
        if (p == null || !p.isAlive()) {
            return;
        }
        log.info("[Brain] Arret du sidecar (pid={})...", p.pid());
        p.destroy();
        try {
            if (!p.waitFor(10, TimeUnit.SECONDS)) {
                log.warn("[Brain] Arret propre depasse (10s) : kill force.");
                p.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
        }
    }

    /**
     * Resout (et cree au besoin) le repertoire de travail du Brain. Le Brain y
     * ecrit son dossier {@code data/} (index vectoriel, settings.json).
     */
    private File resolveWorkingDir() {
        String dir = props.getWorkingDir();
        if (dir == null || dir.isBlank()) {
            return null; // herite du cwd du Core
        }
        Path path = Path.of(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            log.warn("[Brain] Impossible de creer le repertoire de travail {} : {}. "
                    + "Lancement avec le cwd herite.", path, e.getMessage());
            return null;
        }
        return path.toFile();
    }
}
