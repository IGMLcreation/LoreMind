package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.files.StoredFile;
import com.loremind.domain.files.ports.StoredFileRepository;
import com.loremind.infrastructure.persistence.entity.StoredFileJpaEntity;
import com.loremind.infrastructure.persistence.jpa.StoredFileJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Adaptateur de sortie : implemente le port StoredFileRepository du domaine.
 * Fait la traduction StoredFile (domaine) <-> StoredFileJpaEntity (JPA).
 */
@Repository
public class PostgresStoredFileRepository implements StoredFileRepository {

    private final StoredFileJpaRepository jpaRepository;

    public PostgresStoredFileRepository(StoredFileJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public StoredFile save(StoredFile file) {
        StoredFileJpaEntity saved = jpaRepository.save(toJpa(file));
        return toDomain(saved);
    }

    @Override
    public Optional<StoredFile> findById(String id) {
        return jpaRepository.findById(Long.parseLong(id)).map(this::toDomain);
    }

    @Override
    public Optional<StoredFile> findByStorageKey(String storageKey) {
        return jpaRepository.findByStorageKey(storageKey).map(this::toDomain);
    }

    @Override
    public void deleteById(String id) {
        jpaRepository.deleteById(Long.parseLong(id));
    }

    @Override
    public boolean existsById(String id) {
        return jpaRepository.existsById(Long.parseLong(id));
    }

    // --- Conversions -------------------------------------------------------

    private StoredFile toDomain(StoredFileJpaEntity e) {
        return StoredFile.builder()
                .id(e.getId().toString())
                .filename(e.getFilename())
                .contentType(e.getContentType())
                .sizeBytes(e.getSizeBytes())
                .storageKey(e.getStorageKey())
                .uploadedAt(e.getUploadedAt())
                .build();
    }

    private StoredFileJpaEntity toJpa(StoredFile f) {
        Long id = f.getId() != null ? Long.parseLong(f.getId()) : null;
        return StoredFileJpaEntity.builder()
                .id(id)
                .filename(f.getFilename())
                .contentType(f.getContentType())
                .sizeBytes(f.getSizeBytes())
                .storageKey(f.getStorageKey())
                .uploadedAt(f.getUploadedAt())
                .build();
    }
}
