package com.loremind.infrastructure.web.dto.shared;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO wire-friendly du placement d'un bloc dans la grille du template.
 * <p>
 * Miroir de {@link com.loremind.domain.shared.template.BlockPosition} :
 * grille 12 colonnes, {@code {x, y, w, h}} en unites de grille. Tous les
 * champs sont nullables (Integer) — absence = auto-flow empile cote front.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlockPositionDTO {
    /** Colonne de depart (0..11). */
    private Integer x;
    /** Ligne de depart (0..n). */
    private Integer y;
    /** Largeur en colonnes (1..12). */
    private Integer w;
    /** Hauteur en lignes (1..n). */
    private Integer h;
}
