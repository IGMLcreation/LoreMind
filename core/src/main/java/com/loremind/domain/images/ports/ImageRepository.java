package com.loremind.domain.images.ports;

import com.loremind.domain.images.Image;

import java.util.Optional;

/**
 * Port de sortie pour la persistance des metadonnees d'images.
 * <p>
 * Architecture Hexagonale : ce port est defini dans le domaine ; il est
 * implemente par un adaptateur d'infrastructure (PostgresImageRepository).
 * <p>
 * Ne manipule QUE les metadonnees (filename, mimeType, storageKey...).
 * Le binaire est gere par un autre port : ImageStorage.
 * Cette separation suit le Single Responsibility Principle (SRP).
 */
public interface ImageRepository {

    Image save(Image image);

    Optional<Image> findById(String id);

    void deleteById(String id);

    boolean existsById(String id);
}
