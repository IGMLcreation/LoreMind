package com.loremind.domain.licensing;

/**
 * Etat operationnel de la licence vis-a-vis de l'acces beta.
 * <p>
 * Calcule a partir de la presence de licence + son JWT exp + grace period.
 * <ul>
 *   <li>{@link #NONE} : aucune licence installee</li>
 *   <li>{@link #VALID} : JWT non expire, acces beta autorise</li>
 *   <li>{@link #GRACE} : JWT expire mais dans la periode de tolerance ;
 *       acces beta toujours autorise, l'UI doit avertir</li>
 *   <li>{@link #EXPIRED} : au-dela de la grace period, acces beta refuse</li>
 *   <li>{@link #UNVERIFIABLE} : JWT impossible a verifier (cle publique manquante,
 *       signature invalide, claims malformes) — traite comme NONE pour la securite</li>
 * </ul>
 */
public enum LicenseStatus {
    NONE,
    VALID,
    GRACE,
    EXPIRED,
    UNVERIFIABLE
}
