package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.images.Image;
import com.loremind.domain.images.ports.ImageRepository;
import com.loremind.infrastructure.persistence.entity.ImageJpaEntity;
import com.loremind.infrastructure.persistence.jpa.ImageJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Adaptateur de sortie : implemente le port ImageRepository du domaine.
 * Fait la traduction Image (domaine) <-> ImageJpaEntity (JPA).
 */
@Repository
public class PostgresImageRepository implements ImageRepository {

    private final ImageJpaRepository jpaRepository;

    public PostgresImageRepository(ImageJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Image save(Image image) {
        ImageJpaEntity saved = jpaRepository.save(toJpa(image));
        return toDomain(saved);
    }

    @Override
    public Optional<Image> findById(String id) {
        return jpaRepository.findById(Long.parseLong(id)).map(this::toDomain);
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

    private Image toDomain(ImageJpaEntity e) {
        return Image.builder()
                .id(e.getId().toString())
                .filename(e.getFilename())
                .contentType(e.getContentType())
                .sizeBytes(e.getSizeBytes())
                .storageKey(e.getStorageKey())
                .uploadedAt(e.getUploadedAt())
                .build();
    }

    private ImageJpaEntity toJpa(Image img) {
        Long id = img.getId() != null ? Long.parseLong(img.getId()) : null;
        return ImageJpaEntity.builder()
                .id(id)
                .filename(img.getFilename())
                .contentType(img.getContentType())
                .sizeBytes(img.getSizeBytes())
                .storageKey(img.getStorageKey())
                .uploadedAt(img.getUploadedAt())
                .build();
    }
}
