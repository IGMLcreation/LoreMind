package com.loremind.domain.campaigncontext.structure;

/**
 * Type structurel d'un Arc.
 * - LINEAR : narration séquentielle classique (chapitres joués dans l'ordre).
 * - HUB    : narration non linéaire ; contient des QUÊTES (entités Quest rattachées).
 * - SYSTEM : arc TECHNIQUE (« Quêtes libres », un par campagne au besoin) qui héberge
 *            les conteneurs de scènes des quêtes LIBRES (hors arc). Masqué de la
 *            narration dans l'arbre (ses quêtes s'affichent sous « Quêtes ») ; dans
 *            les exports (PDF / Foundry / backup) il apparaît sous son nom — ses
 *            scènes sont du vrai contenu jouable.
 * Value Object du domaine (Bounded Context : Campaign).
 */
public enum ArcType {
    LINEAR,
    HUB,
    SYSTEM
}
