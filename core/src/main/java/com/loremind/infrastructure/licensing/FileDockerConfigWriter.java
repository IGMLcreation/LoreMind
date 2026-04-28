package com.loremind.infrastructure.licensing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.loremind.domain.licensing.RegistryCredentials;
import com.loremind.domain.licensing.ports.DockerConfigWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;

/**
 * Implementation : ecriture du fichier {@code config.json} au format Docker
 * standard, dans un volume partage avec Watchtower.
 * <p>
 * Format produit :
 * <pre>{@code
 * {
 *   "auths": {
 *     "ghcr.io": {
 *       "auth": "<base64(username:password)>"
 *     }
 *   }
 * }
 * }</pre>
 */
@Component
public class FileDockerConfigWriter implements DockerConfigWriter {

    private static final Logger log = LoggerFactory.getLogger(FileDockerConfigWriter.class);

    private final Path configPath;
    private final ObjectMapper mapper = new ObjectMapper();

    public FileDockerConfigWriter(
            @Value("${licensing.docker-config-path:/shared/docker/config.json}") String pathStr) {
        this.configPath = Path.of(pathStr);
    }

    @Override
    public void writeCredentials(RegistryCredentials credentials) throws IOException {
        ensureParentDirectory();

        ObjectNode root;
        if (Files.exists(configPath)) {
            try {
                JsonNode existing = mapper.readTree(configPath.toFile());
                root = existing.isObject() ? (ObjectNode) existing : mapper.createObjectNode();
            } catch (IOException e) {
                log.warn("Existing docker config unreadable, overwriting: {}", e.getMessage());
                root = mapper.createObjectNode();
            }
        } else {
            root = mapper.createObjectNode();
        }

        ObjectNode auths = root.has("auths") && root.get("auths").isObject()
                ? (ObjectNode) root.get("auths")
                : root.putObject("auths");

        String b64 = Base64.getEncoder().encodeToString(
                (credentials.username() + ":" + credentials.password()).getBytes(StandardCharsets.UTF_8));

        ObjectNode entry = mapper.createObjectNode();
        entry.put("auth", b64);
        auths.set(credentials.registry(), entry);

        Files.writeString(configPath, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root),
                StandardCharsets.UTF_8);
        applyRestrictivePermissions();
        log.info("Docker config written at {} for registry {}", configPath, credentials.registry());
    }

    @Override
    public void clear() throws IOException {
        if (Files.exists(configPath)) {
            Files.delete(configPath);
            log.info("Docker config cleared at {}", configPath);
        }
    }

    @Override
    public boolean isPresent() {
        return Files.exists(configPath);
    }

    private void ensureParentDirectory() throws IOException {
        Path parent = configPath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }

    /** 0600 sur POSIX. Sur Windows (dev), no-op silencieux. */
    private void applyRestrictivePermissions() {
        try {
            Files.setPosixFilePermissions(configPath, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException e) {
            // Windows / FS qui ne supporte pas POSIX => ignore (le conteneur tourne sous Linux en prod)
        }
    }

}
