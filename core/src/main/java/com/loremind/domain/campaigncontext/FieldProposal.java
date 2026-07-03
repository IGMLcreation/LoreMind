package com.loremind.domain.campaigncontext;

/**
 * Proposition IA pour UN champ textuel d'une entité (Pilier A — co-création).
 *
 * <p>Value Object immuable, non persisté (éphémère, comme les records {@code *Proposal}
 * de l'import). La {@code key} est alignée sur les champs exposés par
 * {@code NarrativeEntityContextBuilder.fromScene()} : c'est la source de vérité qui relie
 * la génération, l'affichage (diff avant/après) et l'application (patch ciblé).</p>
 *
 * @param key           clé du champ (ex. {@code "playerNarration"}, {@code "atmosphere"})
 * @param currentValue  valeur actuelle du champ (echo pour la diff UI ; ignorée à l'apply)
 * @param proposedValue valeur proposée par l'IA pour ce champ
 */
public record FieldProposal(String key, String currentValue, String proposedValue) {
}
