package com.loremind.domain.campaigncontext;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service de domaine (pur, sans effet de bord) : évalue les prérequis d'une quête
 * et en dérive le {@link QuestStatus} effectif.
 *
 * NB Java 17 : on utilise instanceof pattern matching (finalisé en Java 16) plutôt que
 * switch pattern matching (preview en 17, final en 21). La perte de l'exhaustivité
 * compile-time est compensée par le throw final qui fait crasher tout nouvel
 * implémentant non câblé.
 */
public final class PrerequisiteEvaluator {

    /**
     * Contexte minimal nécessaire à l'évaluation. On ne passe pas la Campaign entière
     * pour ne pas créer de couplage fort ; juste les faits nécessaires.
     */
    public record EvaluationContext(
            Set<String> completedQuestIds,
            int currentSessionCount,
            Map<String, Boolean> campaignFlags
    ) {}

    /** True si TOUS les prérequis sont satisfaits (ET logique). Vide => true. */
    public boolean areAllSatisfied(List<Prerequisite> prerequisites, EvaluationContext ctx) {
        if (prerequisites == null || prerequisites.isEmpty()) return true;
        return prerequisites.stream().allMatch(p -> isSatisfied(p, ctx));
    }

    /** Évalue un seul prérequis. */
    public boolean isSatisfied(Prerequisite prereq, EvaluationContext ctx) {
        if (prereq instanceof Prerequisite.QuestCompleted q) {
            return ctx.completedQuestIds().contains(q.questId());
        }
        if (prereq instanceof Prerequisite.SessionReached s) {
            return ctx.currentSessionCount() >= s.minSessionNumber();
        }
        if (prereq instanceof Prerequisite.FlagSet f) {
            return Boolean.TRUE.equals(ctx.campaignFlags().get(f.flagName()));
        }
        throw new IllegalStateException("Prerequisite non géré : " + prereq.getClass().getName());
    }

    /** Dérive le statut effectif à partir de la progression manuelle + des prérequis. */
    public QuestStatus computeStatus(
            ProgressionStatus progression,
            List<Prerequisite> prerequisites,
            EvaluationContext ctx
    ) {
        switch (progression) {
            case COMPLETED:   return QuestStatus.COMPLETED;
            case IN_PROGRESS: return QuestStatus.IN_PROGRESS;
            case NOT_STARTED:
                return areAllSatisfied(prerequisites, ctx)
                        ? QuestStatus.AVAILABLE
                        : QuestStatus.LOCKED;
            default:
                throw new IllegalStateException("ProgressionStatus non géré : " + progression);
        }
    }
}
