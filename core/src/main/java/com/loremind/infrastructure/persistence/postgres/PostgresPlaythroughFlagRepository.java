package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.playcontext.ports.PlaythroughFlagRepository;
import com.loremind.infrastructure.persistence.entity.PlaythroughFlagJpaEntity;
import com.loremind.infrastructure.persistence.jpa.PlaythroughFlagJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

@Repository
public class PostgresPlaythroughFlagRepository implements PlaythroughFlagRepository {

    private final PlaythroughFlagJpaRepository jpa;

    public PostgresPlaythroughFlagRepository(PlaythroughFlagJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Map<String, Boolean> findByPlaythroughId(String playthroughId) {
        Long pid = Long.parseLong(playthroughId);
        Map<String, Boolean> out = new HashMap<>();
        for (PlaythroughFlagJpaEntity f : jpa.findByPlaythroughId(pid)) {
            out.put(f.getName(), f.isValue());
        }
        return out;
    }

    @Override
    public void setFlag(String playthroughId, String name, boolean value) {
        Long pid = Long.parseLong(playthroughId);
        jpa.findByPlaythroughIdAndName(pid, name).ifPresentOrElse(
                existing -> {
                    existing.setValue(value);
                    jpa.save(existing);
                },
                () -> jpa.save(PlaythroughFlagJpaEntity.builder()
                        .playthroughId(pid)
                        .name(name)
                        .value(value)
                        .build())
        );
    }

    @Override
    public void deleteFlag(String playthroughId, String name) {
        Long pid = Long.parseLong(playthroughId);
        jpa.findByPlaythroughIdAndName(pid, name).ifPresent(f -> jpa.deleteById(f.getId()));
    }

    @Override
    public void deleteAllByPlaythroughId(String playthroughId) {
        jpa.deleteByPlaythroughId(Long.parseLong(playthroughId));
    }
}
