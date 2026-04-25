package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.campaigncontext.Scene;
import com.loremind.domain.campaigncontext.ports.SceneRepository;
import com.loremind.infrastructure.persistence.entity.SceneJpaEntity;
import com.loremind.infrastructure.persistence.jpa.SceneJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adaptateur d'infrastructure qui implémente le Port SceneRepository.
 */
@Repository
public class PostgresSceneRepository implements SceneRepository {

    private final SceneJpaRepository jpaRepository;

    public PostgresSceneRepository(SceneJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Scene save(Scene scene) {
        SceneJpaEntity jpaEntity = toJpaEntity(scene);
        SceneJpaEntity saved = jpaRepository.save(jpaEntity);
        return toDomainEntity(saved);
    }

    @Override
    public Optional<Scene> findById(String id) {
        Long longId = Long.parseLong(id);
        return jpaRepository.findById(longId)
                .map(this::toDomainEntity);
    }

    @Override
    public List<Scene> findByChapterId(String chapterId) {
        Long longChapterId = Long.parseLong(chapterId);
        return jpaRepository.findByChapterId(longChapterId).stream()
                .map(this::toDomainEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<Scene> findAll() {
        return jpaRepository.findAll().stream()
                .map(this::toDomainEntity)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(String id) {
        Long longId = Long.parseLong(id);
        jpaRepository.deleteById(longId);
    }

    @Override
    public boolean existsById(String id) {
        Long longId = Long.parseLong(id);
        return jpaRepository.existsById(longId);
    }

    private Scene toDomainEntity(SceneJpaEntity jpaEntity) {
        return Scene.builder()
                .id(jpaEntity.getId().toString())
                .name(jpaEntity.getName())
                .description(jpaEntity.getDescription())
                .chapterId(jpaEntity.getChapterId().toString())
                .order(jpaEntity.getOrder())
                .icon(jpaEntity.getIcon())
                .location(jpaEntity.getLocation())
                .timing(jpaEntity.getTiming())
                .atmosphere(jpaEntity.getAtmosphere())
                .playerNarration(jpaEntity.getPlayerNarration())
                .gmSecretNotes(jpaEntity.getGmSecretNotes())
                .choicesConsequences(jpaEntity.getChoicesConsequences())
                .combatDifficulty(jpaEntity.getCombatDifficulty())
                .enemies(jpaEntity.getEnemies())
                .relatedPageIds(jpaEntity.getRelatedPageIds() != null
                        ? new ArrayList<>(jpaEntity.getRelatedPageIds())
                        : new ArrayList<>())
                .illustrationImageIds(jpaEntity.getIllustrationImageIds() != null
                        ? new ArrayList<>(jpaEntity.getIllustrationImageIds())
                        : new ArrayList<>())
                .mapImageIds(jpaEntity.getMapImageIds() != null
                        ? new ArrayList<>(jpaEntity.getMapImageIds())
                        : new ArrayList<>())
                .branches(jpaEntity.getBranches() != null
                        ? new ArrayList<>(jpaEntity.getBranches())
                        : new ArrayList<>())
                .createdAt(jpaEntity.getCreatedAt())
                .updatedAt(jpaEntity.getUpdatedAt())
                .build();
    }

    private SceneJpaEntity toJpaEntity(Scene scene) {
        Long id = scene.getId() != null ? Long.parseLong(scene.getId()) : null;
        return SceneJpaEntity.builder()
                .id(id)
                .name(scene.getName())
                .description(scene.getDescription())
                .chapterId(Long.parseLong(scene.getChapterId()))
                .order(scene.getOrder())
                .icon(scene.getIcon())
                .location(scene.getLocation())
                .timing(scene.getTiming())
                .atmosphere(scene.getAtmosphere())
                .playerNarration(scene.getPlayerNarration())
                .gmSecretNotes(scene.getGmSecretNotes())
                .choicesConsequences(scene.getChoicesConsequences())
                .combatDifficulty(scene.getCombatDifficulty())
                .enemies(scene.getEnemies())
                .relatedPageIds(scene.getRelatedPageIds() != null
                        ? new ArrayList<>(scene.getRelatedPageIds())
                        : new ArrayList<>())
                .illustrationImageIds(scene.getIllustrationImageIds() != null
                        ? new ArrayList<>(scene.getIllustrationImageIds())
                        : new ArrayList<>())
                .mapImageIds(scene.getMapImageIds() != null
                        ? new ArrayList<>(scene.getMapImageIds())
                        : new ArrayList<>())
                .branches(scene.getBranches() != null
                        ? new ArrayList<>(scene.getBranches())
                        : new ArrayList<>())
                .createdAt(scene.getCreatedAt())
                .updatedAt(scene.getUpdatedAt())
                .build();
    }
}
