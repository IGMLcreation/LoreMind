package com.loremind.domain.images;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests unitaires du domaine pour Image (Shared Kernel).
 * Entite pure : metadata + cle opaque vers l'object storage.
 * On verifie juste la preservation des champs — aucune logique metier.
 */
class ImageTest {

    @Test
    void builder_preservesAllFields() {
        LocalDateTime now = LocalDateTime.now();
        Image image = Image.builder()
                .id("img-1")
                .filename("portrait-elfe.jpg")
                .contentType("image/jpeg")
                .sizeBytes(125_000L)
                .storageKey("images/abc123.jpg")
                .uploadedAt(now)
                .build();

        assertEquals("img-1", image.getId());
        assertEquals("portrait-elfe.jpg", image.getFilename());
        assertEquals("image/jpeg", image.getContentType());
        assertEquals(125_000L, image.getSizeBytes());
        assertEquals("images/abc123.jpg", image.getStorageKey());
        assertEquals(now, image.getUploadedAt());
    }

    @Test
    void builder_supportsCommonMimeTypes() {
        // Verifie que n'importe quelle chaine MIME passe : la validation se fait
        // cote application (ImageService) pas dans le domaine.
        for (String mime : new String[]{"image/jpeg", "image/png", "image/webp", "image/gif"}) {
            Image image = Image.builder().contentType(mime).build();
            assertEquals(mime, image.getContentType());
        }
    }
}
