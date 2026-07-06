package com.loremind.domain.campaigncontext.readiness;

/**
 * Type de l'entité de scénario ciblée par un manque de readiness. Le front s'en
 * sert (avec {@code arcId}/{@code chapterId} du gap) pour construire le lien profond
 * vers l'éditeur concerné.
 */
public enum ReadinessEntityType {
    CAMPAIGN,
    ARC,
    CHAPTER,
    SCENE,
    QUEST,
    NPC,
    ENEMY
}
