package com.loremind.domain.campaigncontext.generation;

/**
 * Ébauche de scène proposée par l'IA (Pilier A — capacité « create »). Value Object
 * immuable et non persisté : l'utilisateur révise la liste, puis les ébauches ACCEPTÉES
 * sont créées comme vraies {@link Scene} dans le chapitre ciblé.
 *
 * @param name            titre court de la scène (obligatoire à la création)
 * @param description     résumé bref (facultatif)
 * @param playerNarration texte de mise en scène lu aux joueurs (facultatif)
 */
public record SceneDraft(String name, String description, String playerNarration) {
}
