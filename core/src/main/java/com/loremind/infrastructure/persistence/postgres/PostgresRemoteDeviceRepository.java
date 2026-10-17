package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.remoteaccess.RemoteDevice;
import com.loremind.domain.remoteaccess.ports.RemoteDeviceRepository;
import com.loremind.infrastructure.persistence.entity.RemoteDeviceJpaEntity;
import com.loremind.infrastructure.persistence.jpa.RemoteDeviceJpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class PostgresRemoteDeviceRepository implements RemoteDeviceRepository {

    private final RemoteDeviceJpaRepository jpa;

    public PostgresRemoteDeviceRepository(RemoteDeviceJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public RemoteDevice save(RemoteDevice device) {
        RemoteDeviceJpaEntity entity = RemoteDeviceJpaEntity.builder()
                .id(device.getId() != null ? Long.parseLong(device.getId()) : null)
                .name(device.getName())
                .tokenHash(device.getTokenHash())
                .userAgent(device.getUserAgent())
                .createdAt(device.getCreatedAt() != null ? device.getCreatedAt() : Instant.now())
                .lastSeenAt(device.getLastSeenAt())
                .build();
        return toDomain(jpa.save(entity));
    }

    @Override
    public Optional<RemoteDevice> findByTokenHash(String tokenHash) {
        return jpa.findByTokenHash(tokenHash).map(this::toDomain);
    }

    @Override
    public List<RemoteDevice> findAll() {
        return jpa.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public void touch(String id, Instant lastSeenAt) {
        jpa.touch(Long.parseLong(id), lastSeenAt);
    }

    @Override
    public boolean deleteById(String id) {
        Long key = Long.parseLong(id);
        if (!jpa.existsById(key)) return false;
        jpa.deleteById(key);
        return true;
    }

    private RemoteDevice toDomain(RemoteDeviceJpaEntity e) {
        return RemoteDevice.builder()
                .id(String.valueOf(e.getId()))
                .name(e.getName())
                .tokenHash(e.getTokenHash())
                .userAgent(e.getUserAgent())
                .createdAt(e.getCreatedAt())
                .lastSeenAt(e.getLastSeenAt())
                .build();
    }
}
