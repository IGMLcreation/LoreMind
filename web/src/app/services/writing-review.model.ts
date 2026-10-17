import { ReadinessEntityType } from './readiness.model';

/**
 * Modèles de la relecture d'éditeur (Mentor d'écriture, phase M2) renvoyés par
 * GET /api/campaigns/{id}/writing-review. Le back renvoie la règle et ses paramètres ;
 * le texte (observation / pourquoi / question) vient de l'i18n `writingReview.rules.<ruleId>`.
 */

export type WritingRemarkCategory = 'AGENCY' | 'PACING' | 'STAKES' | 'NARRATION';

export interface WritingRemark {
  entityType: ReadinessEntityType;
  entityId: string;
  entityName?: string | null;
  ruleId: string;
  category: WritingRemarkCategory;
  /** Valeurs injectées dans le texte i18n (count, phrase, target…). */
  params: Record<string, string>;
  arcId?: string | null;
  chapterId?: string | null;
}

export interface WritingReviewReport {
  campaignId: string;
  remarks: WritingRemark[];
}

/** Ordre d'affichage des familles de remarques. */
export const WRITING_REMARK_CATEGORIES: WritingRemarkCategory[] = ['AGENCY', 'STAKES', 'PACING', 'NARRATION'];
