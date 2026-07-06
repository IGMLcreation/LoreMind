package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.readiness.ReadinessEntityType;
import com.loremind.domain.campaigncontext.readiness.ReadinessSeverity;

/**
 * Un manque de préparation détecté sur une entité de scénario (read-model, Pilier B).
 *
 * <p>Le message est déjà rédigé (orienté action, bienveillant) côté back ; le front
 * l'affiche tel quel. {@code arcId}/{@code chapterId} sont le CONTEXTE de navigation
 * (nullable selon l'entité) : le front construit le lien profond vers l'éditeur à
 * partir de {@code entityType}, {@code entityId} et de ces ancêtres — aucune route
 * Angular n'est codée côté back.</p>
 *
 * @param entityType type de l'entité concernée
 * @param entityId   id de l'entité concernée
 * @param entityName libellé lisible de l'entité (peut être {@code null} si sans nom)
 * @param ruleId     identifiant stable de la règle (ex. {@code SCENE-011-COMBAT-NO-ENEMY})
 * @param message    message utilisateur prêt à afficher
 * @param severity   gravité du manque
 * @param arcId      arc parent (navigation), ou {@code null}
 * @param chapterId  chapitre parent (navigation), ou {@code null}
 */
public record ReadinessGap(
        ReadinessEntityType entityType,
        String entityId,
        String entityName,
        String ruleId,
        String message,
        ReadinessSeverity severity,
        String arcId,
        String chapterId
) {
}
