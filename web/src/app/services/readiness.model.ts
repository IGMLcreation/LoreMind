/**
 * Modèles du Pilier B (« guidage / readiness ») : bilan de préparation d'une
 * campagne renvoyé par GET /api/campaigns/{id}/readiness.
 */

export type ReadinessStatus = 'DRAFT' | 'PLAYABLE' | 'POLISHED';
export type ReadinessSeverity = 'BLOCKING' | 'RECOMMENDED' | 'OPTIONAL';
export type ReadinessEntityType =
  | 'CAMPAIGN' | 'ARC' | 'CHAPTER' | 'SCENE' | 'QUEST' | 'NPC' | 'ENEMY';

/** Un manque de préparation détecté, cliquable vers l'éditeur concerné. */
export interface ReadinessGap {
  entityType: ReadinessEntityType;
  entityId: string;
  entityName?: string | null;
  ruleId: string;
  message: string;
  severity: ReadinessSeverity;
  /** Contexte de navigation (le front construit le lien profond). */
  arcId?: string | null;
  chapterId?: string | null;
}

export interface CampaignReadinessAssessment {
  campaignId: string;
  overallStatus: ReadinessStatus;
  /** Nombre de gaps par sévérité : { BLOCKING, RECOMMENDED, OPTIONAL }. */
  counts: Record<string, number>;
  gaps: ReadinessGap[];
}
