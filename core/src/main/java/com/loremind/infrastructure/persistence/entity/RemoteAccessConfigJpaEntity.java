package com.loremind.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entité JPA du réglage d'accès distant (migration V25). Singleton : une seule ligne,
 * {@code id = "current"}, comme la licence.
 */
@Entity
@Table(name = "remote_access_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemoteAccessConfigJpaEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private int port;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
