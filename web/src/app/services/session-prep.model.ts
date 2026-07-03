import { ReadinessGap } from './readiness.model';

/**
 * Modèles « Préparer la prochaine séance » (Phase 3 co-MJ) — miroir de SessionPrepReport.
 */

export interface PrepLastSession {
  id: string;
  name: string;
  startedAt?: string | null;
  endedAt?: string | null;
  active: boolean;
}

export interface PrepQuest {
  id: string;
  name: string;
  icon?: string | null;
}

/** Chapitre / scène probable, avec le contexte pour le lien profond. */
export interface PrepNode {
  nodeType: 'CHAPTER' | 'SCENE';
  id: string;
  name: string;
  arcId?: string | null;
  chapterId?: string | null;
}

export interface PrepClock {
  id: string;
  name: string;
  segments: number;
  filled: number;
  frontName?: string | null;
}

export interface SessionPrepReport {
  playthroughId: string;
  lastSession?: PrepLastSession | null;
  questsInProgress: PrepQuest[];
  questsAvailable: PrepQuest[];
  questsCompleted: PrepQuest[];
  hotspots: PrepNode[];
  gaps: ReadinessGap[];
  otherGapCount: number;
  clocks: PrepClock[];
}
