package com.loremind.domain.lorecontext;

/**
 * Cadrage d'une image dans son bloc IMAGE d'une page de lore.
 * <p>
 * Donnée purement présentationnelle, définie PAR IMAGE et PAR PAGE :
 * <ul>
 *   <li>{@link #x}, {@link #y} : object-position en pourcentage (0..100).
 *       50/50 = centré (défaut).</li>
 *   <li>{@link #scale} : facteur de zoom (>= 1). 1 = ajusté plein cadre (cover).</li>
 * </ul>
 * <p>
 * Stockée sur la Page dans {@code imageFraming} (fieldKey → imageId → cadrage),
 * sérialisée en JSON. Entité pure du domaine : aucune dépendance technique.
 */
public record ImageFraming(double x, double y, double scale) {
}
