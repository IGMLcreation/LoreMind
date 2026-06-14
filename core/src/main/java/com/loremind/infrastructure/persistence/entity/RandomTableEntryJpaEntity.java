package com.loremind.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Entité JPA d'une entrée de table aléatoire (enfant de {@link RandomTableJpaEntity}).
 * Ordonnée par {@code position}. La référence parente est exclue de toString/equals
 * pour éviter les récursions infinies (relation bidirectionnelle).
 */
@Entity
@Table(name = "random_table_entries", indexes = {
        @Index(name = "idx_random_table_entries_table_id", columnList = "random_table_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RandomTableEntryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "min_roll", nullable = false)
    private int minRoll;

    @Column(name = "max_roll", nullable = false)
    private int maxRoll;

    @Column(nullable = false)
    private String label;

    @Column(columnDefinition = "TEXT")
    private String detail;

    /** Position d'affichage dans la table (ordre des entrées). */
    @Column(nullable = false)
    private int position;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "random_table_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private RandomTableJpaEntity randomTable;
}
