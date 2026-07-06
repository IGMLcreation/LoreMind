package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.files.StoredFile;
import com.loremind.domain.files.ports.StoredFileRepository;
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
 * Tests d'integration pour PostgresStoredFileRepository.
 * StoredFile = pendant generique d'Image (battlemaps : video/json), metadata +
 * cle opaque. Valide aussi la recherche par cle (utilisee a l'import).
 */
@SpringBootTest
@Transactional
class PostgresStoredFileRepositoryTest {

    @Autowired private StoredFileRepository repository;

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2024, java.time.Month.JANUARY, 1, 0, 0);

    @Test
    void save_fileWithAllMetadata_roundTrips() {
        StoredFile file = StoredFile.builder()
                .filename("cellier.mp4")
                .contentType("video/mp4")
                .sizeBytes(8_123_456L)
                .storageKey("files/abc123.mp4")
                .uploadedAt(FIXED_TIME)
                .build();

        StoredFile saved = repository.save(file);
        assertNotNull(saved.getId());

        StoredFile r = repository.findById(saved.getId()).orElseThrow();
        assertEquals("cellier.mp4", r.getFilename());
        assertEquals("video/mp4", r.getContentType());
        assertEquals(8_123_456L, r.getSizeBytes());
        assertEquals("files/abc123.mp4", r.getStorageKey());
        assertNotNull(r.getUploadedAt());
    }

    @Test
    void findByStorageKey_returnsMatch() {
        repository.save(StoredFile.builder()
                .filename("map.dd2vtt").contentType("application/json").sizeBytes(2048L)
                .storageKey("files/sidecar.json").uploadedAt(FIXED_TIME).build());

        assertTrue(repository.findByStorageKey("files/sidecar.json").isPresent());
        assertTrue(repository.findByStorageKey("files/inconnu.json").isEmpty());
    }

    @Test
    void deleteById_removesFile() {
        StoredFile saved = repository.save(StoredFile.builder()
                .filename("x.webm").contentType("video/webm").sizeBytes(100L)
                .storageKey("files/k.webm").uploadedAt(FIXED_TIME).build());

        assertTrue(repository.existsById(saved.getId()));
        repository.deleteById(saved.getId());
        assertFalse(repository.existsById(saved.getId()));
    }
}
