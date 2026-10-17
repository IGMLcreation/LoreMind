package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.readiness.ReadinessEntityType;
import com.loremind.domain.campaigncontext.writingreview.WritingRemarkCategory;

import java.util.Map;

/**
 * Une remarque de relecture d'éditeur (Mentor d'écriture, phase M2) : un conseil
 * d'auteur sur la QUALITÉ de l'écriture, pas sur la complétude (ça, c'est le readiness).
 *
 * <p>Contrairement à {@link ReadinessGap}, le back ne rédige pas le texte : il
 * renvoie la règle et ses paramètres, et le front compose observation / pourquoi /
 * question depuis l'i18n ({@code writingReview.rules.<ruleId>}), en fr comme en en.</p>
 *
 * @param entityType type de l'entité concernée (réutilise celui du readiness pour les liens profonds)
 * @param entityId   id de l'entité concernée
 * @param entityName libellé lisible de l'entité
 * @param ruleId     identifiant stable de la règle (ex. {@code WRITE-002-FAKE-CHOICE})
 * @param category   famille de la remarque (regroupement à l'affichage)
 * @param params     valeurs à injecter dans le texte i18n (ex. {@code count}, {@code phrase})
 * @param arcId      arc parent (navigation), ou {@code null}
 * @param chapterId  chapitre parent (navigation), ou {@code null}
 */
public record WritingRemark(
        ReadinessEntityType entityType,
        String entityId,
        String entityName,
        String ruleId,
        WritingRemarkCategory category,
        Map<String, String> params,
        String arcId,
        String chapterId
) {
}
