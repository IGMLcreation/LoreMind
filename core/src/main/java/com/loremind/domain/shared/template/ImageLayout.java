package com.loremind.domain.shared.template;

/**
 * Variante de rendu pour un champ de type IMAGE.
 * <p>
 * - GALLERY  : grille de vignettes (defaut, comportement historique)
 * - HERO     : premiere image en banniere pleine largeur, suivantes en petit
 * - MASONRY  : mosaique hauteurs variables facon Pinterest
 * - CAROUSEL : defilement horizontal
 * <p>
 * Uniquement significatif quand {@link FieldType} = IMAGE. Ignore sinon.
 */
public enum ImageLayout {
    GALLERY,
    HERO,
    MASONRY,
    CAROUSEL
}
