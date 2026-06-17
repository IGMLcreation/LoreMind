package com.loremind.infrastructure.storage;

import com.loremind.domain.images.ports.ImageStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Adaptateur d'infrastructure : implemente le port ImageStorage en stockant
 * les binaires sur le SYSTEME DE FICHIERS local.
 * <p>
 * Pendant a {@link MinioImageStorageAdapter} pour le mode "local-first"
 * (application de bureau empaquetee via jpackage, sans Docker ni MinIO).
 * Active uniquement quand {@code storage.backend=filesystem} ; en l'absence
 * de cette propriete, c'est l'adaptateur MinIO qui prend le relais (defaut).
 * <p>
 * On reutilise EXACTEMENT le meme schema de cle que MinIO ({@code images/UUID.ext})
 * pour que les cles restent interchangeables entre les deux backends : une base
 * migree de l'un vers l'autre continue de fonctionner sans reecriture.
 */
@Component
@ConditionalOnProperty(name = "storage.backend", havingValue = "filesystem")
public class FilesystemImageStorageAdapter implements ImageStorage {

    private final Path root;

    public FilesystemImageStorageAdapter(@Value("${storage.filesystem.path}") String basePath) {
        this.root = Path.of(basePath).toAbsolutePath().normalize();
    }

    @PostConstruct
    void ensureRootExists() {
        try {
            Files.createDirectories(root);
            System.out.println("[Storage] Backend filesystem actif — racine : " + root);
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
            throw new UncheckedIOException("Echec de l'ecriture de l'image sur disque : " + target, e);
        }
    }

    @Override
    public InputStream download(String storageKey) {
        Path source = resolveKey(storageKey);
        if (!Files.exists(source)) {
            // Cle orpheline : meme contrat que MinIO (null plutot qu'exception).
            return null;
        }
        try {
            return Files.newInputStream(source);
        } catch (IOException e) {
            throw new UncheckedIOException("Echec de la lecture de l'image : " + source, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolveKey(storageKey));
        } catch (IOException e) {
            // Suppression idempotente : on loggue mais on ne propage pas (cf. MinIO).
            System.err.println("[Storage] Erreur suppression (non bloquante) : " + e.getMessage());
        }
    }

    /**
     * Resout une cle opaque en chemin physique, en se premunissant contre la
     * traversee de repertoire : le chemin resolu DOIT rester sous {@code root}
     * (une cle malveillante du type {@code ../../etc/passwd} est rejetee).
     */
    private Path resolveKey(String storageKey) {
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Cle de stockage invalide (hors racine) : " + storageKey);
        }
        return resolved;
    }

    /** Identique a MinioImageStorageAdapter : cle unique + extension d'origine. */
    private String generateStorageKey(String originalFilename) {
        return "images/" + UUID.randomUUID() + extractExtension(originalFilename);
    }

    private String extractExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "";
        String ext = filename.substring(dot).toLowerCase();
        // On n'accepte que les extensions connues pour eviter les injections de path.
        return ext.matches("\\.(jpg|jpeg|png|webp|gif)") ? ext : "";
    }
}
