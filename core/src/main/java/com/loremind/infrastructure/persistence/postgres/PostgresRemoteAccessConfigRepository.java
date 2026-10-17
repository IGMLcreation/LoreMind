package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.remoteaccess.RemoteAccessConfig;
import com.loremind.domain.remoteaccess.ports.RemoteAccessConfigRepository;
import com.loremind.infrastructure.persistence.entity.RemoteAccessConfigJpaEntity;
import com.loremind.infrastructure.persistence.jpa.RemoteAccessConfigJpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public class PostgresRemoteAccessConfigRepository implements RemoteAccessConfigRepository {

    static final String CURRENT_ID = "current";

    private final RemoteAccessConfigJpaRepository jpa;

    public PostgresRemoteAccessConfigRepository(RemoteAccessConfigJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<RemoteAccessConfig> find() {
        return jpa.findById(CURRENT_ID).map(e -> new RemoteAccessConfig(e.isEnabled(), e.getPort()));
    }

    @Override
    public RemoteAccessConfig save(RemoteAccessConfig config) {
        jpa.save(RemoteAccessConfigJpaEntity.builder()
                .id(CURRENT_ID)
                .enabled(config.enabled())
                .port(config.port())
                .updatedAt(Instant.now())
                .build());
        return config;
    }
}
