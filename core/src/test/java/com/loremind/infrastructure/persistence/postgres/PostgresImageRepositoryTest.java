package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.images.Image;
import com.loremind.domain.images.ports.ImageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests d'integration pour PostgresImageRepository.
 * Image est un Shared Kernel : juste metadata + cle opaque vers MinIO.
 */
@SpringBootTest
@Transactional
class PostgresImageRepositoryTest {

    @Autowired private ImageRepository repository;

    @Test
    void save_imageWithAllMetadata_roundTrips() {
        Image image = Image.builder()
                .filename("portrait-elfe.jpg")
                .contentType("image/jpeg")
                .sizeBytes(125_000L)
                .storageKey("images/abc123.jpg")
                .uploadedAt(LocalDateTime.now())
                .build();

        Image saved = repository.save(image);
        assertNotNull(saved.getId());

        Image r = repository.findById(saved.getId()).orElseThrow();
        assertEquals("portrait-elfe.jpg", r.getFilename());
        assertEquals("image/jpeg", r.getContentType());
        assertEquals(125_000L, r.getSizeBytes());
        assertEquals("images/abc123.jpg", r.getStorageKey());
        assertNotNull(r.getUploadedAt());
    }

    @Test
    void deleteById_removesImage() {
        Image saved = repository.save(Image.builder()
                .filename("x.png").contentType("image/png").sizeBytes(100L)
                .storageKey("k").uploadedAt(LocalDateTime.now()).build());

        assertTrue(repository.existsById(saved.getId()));
        repository.deleteById(saved.getId());
        assertFalse(repository.existsById(saved.getId()));
    }

    @Test
    void existsById_returnsFalse_forUnknownId() {
        // L'id cote DB est un BIGSERIAL parse via Long.parseLong cote adapter.
        // On passe donc un nombre "impossible" plutot qu'une chaine non numerique.
        assertFalse(repository.existsById("999999999"));
    }
}
