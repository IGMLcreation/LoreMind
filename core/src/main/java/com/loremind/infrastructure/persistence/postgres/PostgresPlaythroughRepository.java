package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.playcontext.Playthrough;
import com.loremind.domain.playcontext.ports.PlaythroughRepository;
import com.loremind.infrastructure.persistence.entity.PlaythroughJpaEntity;
import com.loremind.infrastructure.persistence.jpa.PlaythroughJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class PostgresPlaythroughRepository implements PlaythroughRepository {

    private final PlaythroughJpaRepository jpa;

    public PostgresPlaythroughRepository(PlaythroughJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Playthrough save(Playthrough p) {
        PlaythroughJpaEntity saved = jpa.save(toJpa(p));
        return toDomain(saved);
    }

    @Override
    public Optional<Playthrough> findById(String id) {
        return jpa.findById(Long.parseLong(id)).map(this::toDomain);
    }

    @Override
    public List<Playthrough> findByCampaignId(String campaignId) {
        return jpa.findByCampaignId(Long.parseLong(campaignId)).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Playthrough> findAll() {
        return jpa.findAll().stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public void deleteById(String id) {
        jpa.deleteById(Long.parseLong(id));
    }

    @Override
    public boolean existsById(String id) {
        return jpa.existsById(Long.parseLong(id));
    }

    private Playthrough toDomain(PlaythroughJpaEntity e) {
        return Playthrough.builder()
                .id(e.getId().toString())
                .campaignId(e.getCampaignId().toString())
                .name(e.getName())
                .description(e.getDescription())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private PlaythroughJpaEntity toJpa(Playthrough p) {
        Long id = p.getId() != null ? Long.parseLong(p.getId()) : null;
        return PlaythroughJpaEntity.builder()
                .id(id)
                .campaignId(Long.parseLong(p.getCampaignId()))
                .name(p.getName())
                .description(p.getDescription())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
