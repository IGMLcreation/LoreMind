package com.loremind.domain.campaigncontext;

/**
 * Type structurel d'un Arc.
 * - LINEAR : narration séquentielle classique (chapitres joués dans l'ordre).
 * - HUB    : narration non linéaire ; les chapitres sont des "quêtes" satellites
 *            potentiellement parallèles, soumises à des prérequis pour être débloquées.
 *
 * Value Object du domaine (Bounded Context : Campaign).
 */
public enum ArcType {
    LINEAR,
    HUB
}
