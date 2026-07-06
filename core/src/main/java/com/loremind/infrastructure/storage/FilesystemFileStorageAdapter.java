package com.loremind.infrastructure.storage;

import com.loremind.domain.files.ports.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Adaptateur filesystem pour le port {@link FileStorage} (fichiers generiques).
 * <p>
 * Pendant de {@link FilesystemImageStorageAdapter} : meme racine de stockage
 * ({@code storage.filesystem.path}), mais prefixe de cle {@code files/} pour
 * coexister sans collision avec les images. Active en mode local-first
 * ({@code storage.backend=filesystem}).
 */
@Component
@ConditionalOnProperty(name = "storage.backend", havingValue = "filesystem")
public class FilesystemFileStorageAdapter implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(FilesystemFileStorageAdapter.class);

    private final Path root;

    public FilesystemFileStorageAdapter(@Value("${storage.filesystem.path}") String basePath) {
        this.root = Path.of(basePath).toAbsolutePath().normalize();
    }

    @PostConstruct
    void ensureRootExists() {
        try {
            Files.createDirectories(root.resolve("files"));
            log.info("[Storage] Backend filesystem (fichiers) actif — racine : {}", root);
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de creer le dossier de stockage : " + root, e);
        }
    }

    @Override
    public String upload(String filename, String contentType, InputStream data, long sizeBytes) {
        String storageKey = generateStorageKey(filename);
        Path target = resolveKey(storageKey);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(data, target);
            return storageKey;
        } catch (IOException e) {
            throw new UncheckedIOException("Echec de l'ecriture du fichier sur disque : " + target, e);
        }
    }

    @Override
    public void store(String storageKey, String contentType, InputStream data, long sizeBytes) {
        Path target = resolveKey(storageKey);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(data, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Echec de l'ecriture du fichier sur disque (cle imposee) : " + target, e);
        }
    }

    @Override
    public InputStream download(String storageKey) {
        Path source = resolveKey(storageKey);
        if (!Files.exists(source)) {
            return null;
        }
        try {
            return Files.newInputStream(source);
        } catch (IOException e) {
            throw new UncheckedIOException("Echec de la lecture du fichier : " + source, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolveKey(storageKey));
        } catch (IOException e) {
            log.warn("[Storage] Erreur suppression fichier (non bloquante) : {}", e.getMessage());
        }
    }

    /** Resout une cle opaque en chemin physique, en bloquant la traversee de repertoire. */
    private Path resolveKey(String storageKey) {
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Cle de stockage invalide (hors racine) : " + storageKey);
        }
        return resolved;
    }

    private String generateStorageKey(String originalFilename) {
        return "files/" + UUID.randomUUID() + extractExtension(originalFilename);
    }

    /** Extensions acceptees : images + video + sidecars Universal VTT (json/dd2vtt/uvtt). */
    private String extractExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "";
        String ext = filename.substring(dot).toLowerCase();
        return ext.matches("\\.(jpg|jpeg|png|webp|gif|mp4|webm|json|dd2vtt|uvtt)") ? ext : "";
    }
}
