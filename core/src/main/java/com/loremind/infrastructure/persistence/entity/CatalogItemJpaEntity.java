package com.loremind.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Entité JPA d'un objet de catalogue (enfant de {@link ItemCatalogJpaEntity}).
 * Ordonné par {@code position}. Référence parente exclue de toString/equals.
 */
@Entity
@Table(name = "catalog_items", indexes = {
        @Index(name = "idx_catalog_items_catalog_id", columnList = "catalog_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogItemJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 64)
    private String price;

    @Column(length = 128)
    private String category;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private int position;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "catalog_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ItemCatalogJpaEntity catalog;
}
