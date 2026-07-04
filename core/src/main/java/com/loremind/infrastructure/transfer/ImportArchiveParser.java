package com.loremind.infrastructure.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.infrastructure.transfer.dto.ContentExport;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Déballage d'un zip d'import (cf. {@link ImportService}) : {@code data.json}
 * désérialisé en {@link ContentExport}, binaires images/fichiers gardés en mémoire
 * sous leur clé de stockage d'origine.
 */
@Component
class ImportArchiveParser {

    /** Contenu déballé d'un zip d'import : {@code data.json} + binaires images + fichiers. */
    record ParsedArchive(ContentExport export,
                         Map<String, byte[]> imageBinaries,
                         Map<String, byte[]> fileBinaries) {
    }

    private final ObjectMapper objectMapper;

    ImportArchiveParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Déballe le zip : {@code data.json → ContentExport} et {@code images/<clé> → binaire}
     * (le {@code manifest.json} est ignoré, info seulement). Lève si {@code data.json} manque.
     */
    ParsedArchive parse(InputStream zipStream) {
        ContentExport export = null;
        Map<String, byte[]> imageBinaries = new LinkedHashMap<>(); // storageKey -> binaire
        Map<String, byte[]> fileBinaries = new LinkedHashMap<>();  // storageKey -> binaire
        try (ZipInputStream zip = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if ("data.json".equals(name)) {
                    export = objectMapper.readValue(readAll(zip), ContentExport.class);
                } else if (name.startsWith("images/") && !entry.isDirectory()) {
                    // La cle de stockage est le chemin sans le prefixe "images/" du zip,
                    // c'est-a-dire EXACTEMENT le storageKey d'origine ("images/UUID.ext").
                    String storageKey = name.substring("images/".length());
                    imageBinaries.put(storageKey, readAll(zip));
                } else if (name.startsWith("files/") && !entry.isDirectory()) {
                    // Le prefixe zip "files/" enrobe le storageKey, lui-meme "files/UUID.ext" :
                    // on retire UNE fois le prefixe pour retrouver la cle d'origine.
                    String storageKey = name.substring("files/".length());
                    fileBinaries.put(storageKey, readAll(zip));
                }
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Echec de lecture du zip d'import", e);
        }
        if (export == null) {
            throw new IllegalArgumentException("Archive invalide : data.json introuvable");
        }
        return new ParsedArchive(export, imageBinaries, fileBinaries);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        in.transferTo(buffer);
        return buffer.toByteArray();
    }
}
