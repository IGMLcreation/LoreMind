package com.loremind.application.licensing;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestre la bascule de canal stable <-> beta via le sidecar `switcher`.
 *
 * <p>Le sidecar tourne en permanence et watch un fichier {@code command.json}
 * dans un volume partage. Quand on depose une commande, il :
 * <ol>
 *   <li>Sed la ligne IMAGE_NAMESPACE du .env</li>
 *   <li>Lance docker compose pull + up -d</li>
 *   <li>Ecrit son resultat dans {@code result.json}</li>
 * </ol>
 *
 * <p>Le Core n'a PAS acces au socket Docker — il delegue tout au sidecar
 * via fichiers, ce qui evite que la compromission du Core ne donne RCE
 * sur l'hote. Le sidecar valide strictement le contenu de la commande
 * (channel ∈ {stable, beta} uniquement).
 *
 * <p>Le canal actuel se deduit du prefixe d'image courant (recupere via
 * la variable d'env {@code IMAGE_NAMESPACE} ou {@code UPDATE_CHECK_IMAGES}) :
 * presence de "loremind-beta-" => canal beta, sinon stable.
 */
@Service
public class ChannelSwitcherService {

    private static final Logger log = LoggerFactory.getLogger(ChannelSwitcherService.class);

    public enum Channel { STABLE, BETA }

    public enum SwitchStatus { IN_PROGRESS, SUCCESS, ERROR }

    /** Snapshot du dernier resultat de switch ecrit par le sidecar. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SwitchResult(
            String id,
            SwitchStatus status,
            Channel channel,
            String message,
            Instant completedAt) {}

    private final Path switcherDataPath;
    private final String imageNamespace;
    private final ObjectMapper json = new ObjectMapper();

    public ChannelSwitcherService(
            @Value("${SWITCHER_DATA_PATH:/shared/switcher}") String switcherDataPath,
            // On lit IMAGE_NAMESPACE en priorite, puis UPDATE_CHECK_IMAGES en fallback
            // (la deuxieme est toujours injectee par compose, contrairement a la premiere
            // qui peut etre absente dans les .env legacy).
            @Value("${IMAGE_NAMESPACE:${UPDATE_CHECK_IMAGES:}}") String imageNamespaceRaw) {
        this.switcherDataPath = Path.of(switcherDataPath);
        this.imageNamespace = imageNamespaceRaw != null ? imageNamespaceRaw : "";
        log.info("ChannelSwitcherService initialized: dataPath={} imageNamespace={}",
                switcherDataPath, this.imageNamespace);
    }

    /**
     * Detection du canal courant a partir du prefixe d'image charge au demarrage.
     * Pas de magie : si le namespace contient "beta-" on est en beta, sinon stable.
     */
    public Channel getCurrentChannel() {
        return imageNamespace.contains("loremind-beta-") ? Channel.BETA : Channel.STABLE;
    }

    /**
     * Indique si le sidecar est disponible (volume partage accessible).
     * Si non, on degrade en lecture seule (l'UI affichera l'ancien message
     * avec instructions manuelles).
     */
    public boolean isSwitcherAvailable() {
        return Files.isDirectory(switcherDataPath) && Files.isWritable(switcherDataPath);
    }

    /**
     * Depose une commande de switch dans le volume partage. Renvoie l'ID
     * de la commande, que le client peut utiliser pour poller le status.
     *
     * @throws IllegalStateException si le sidecar n'est pas disponible
     * @throws IOException si l'ecriture du fichier echoue
     */
    public String requestSwitch(Channel target) throws IOException {
        if (!isSwitcherAvailable()) {
            throw new IllegalStateException("Switcher sidecar not available (volume mount missing)");
        }
        String id = UUID.randomUUID().toString();
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("id", id);
        command.put("channel", target.name().toLowerCase());
        command.put("requestedAt", Instant.now().toString());

        Path commandFile = switcherDataPath.resolve("command.json");
        Path tmp = Files.createTempFile(switcherDataPath, "command-", ".tmp");
        try {
            json.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), command);
            // Atomic move : evite que le sidecar lise un fichier partiellement ecrit.
            Files.move(tmp, commandFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            // Cleanup au cas ou move aurait echoue avant le rename.
            Files.deleteIfExists(tmp);
        }
        log.info("Switch command written: id={} channel={}", id, target);
        return id;
    }

    /**
     * Lit le dernier resultat ecrit par le sidecar, s'il existe.
     * Renvoie null si aucun switch n'a encore ete tente sur cette instance.
     */
    public SwitchResult getLastResult() {
        Path resultFile = switcherDataPath.resolve("result.json");
        if (!Files.exists(resultFile)) return null;
        try {
            return json.readValue(resultFile.toFile(), SwitchResult.class);
        } catch (IOException e) {
            log.warn("Cannot parse switcher result.json: {}", e.getMessage());
            return null;
        }
    }
}
