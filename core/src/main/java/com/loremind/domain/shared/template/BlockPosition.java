package com.loremind.domain.shared.template;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Value Object decrivant le placement d'un bloc dans la grille du template
 * (kernel partage).
 * <p>
 * Modele : grille responsive a 12 colonnes (facon page-builder), inspire de
 * l'app "Lore" d'Amsel. Chaque bloc occupe un rectangle de la grille :
 * <ul>
 *   <li>{@link #x} : colonne de depart, 0..11</li>
 *   <li>{@link #y} : ligne de depart, 0..n</li>
 *   <li>{@link #w} : largeur en colonnes, 1..12</li>
 *   <li>{@link #h} : hauteur en lignes, 1..n</li>
 * </ul>
 * <p>
 * Tous les champs sont nullables : un {@code pos} absent (null) ou des
 * coordonnees nulles signifient "auto-flow" — le bloc est empile a la suite
 * des precedents, exactement comme le rendu historique en une seule colonne.
 * C'est ce qui garantit la retrocompatibilite des templates existants (qui
 * n'ont aucune donnee de placement) : ils continuent de s'afficher empiles.
 * <p>
 * Donnee purement presentationnelle : ignoree par l'IA, l'export campagne et
 * l'export Foundry, qui ne lisent que l'ordre et les valeurs par nom de champ.
 * <p>
 * Entite pure du domaine : aucune dependance technique.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockPosition {
    /** Colonne de depart dans la grille 12 colonnes (0..11). Null = auto-flow. */
    private Integer x;
    /** Ligne de depart (0..n). Null = auto-flow. */
    private Integer y;
    /** Largeur en colonnes (1..12). Null = pleine largeur par defaut. */
    private Integer w;
    /** Hauteur en lignes (1..n). Null = hauteur du contenu. */
    private Integer h;
}
