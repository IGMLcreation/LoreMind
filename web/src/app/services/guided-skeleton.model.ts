import { SceneType } from './campaign.model';

/**
 * Squelette d'aventure produit par une méthode guidée (Mentor d'écriture, phase M3),
 * envoyé à POST /api/campaigns/{id}/guided-skeleton. Miroir du record Java `GuidedSkeleton`.
 */
export interface GuidedChapterSpec {
  name: string;
  description?: string | null;
  playerObjectives?: string | null;
  narrativeStakes?: string | null;
}

export interface GuidedSceneSpec {
  name: string;
  description?: string | null;
  type: SceneType;
  location?: string | null;
  choicesConsequences?: string | null;
  gmSecretNotes?: string | null;
  enemies?: string | null;
}

export interface GuidedSkeleton {
  /** Arc linéaire existant… */
  arcId?: string | null;
  /** …ou nom du nouvel arc (exactement l'un des deux). */
  newArcName?: string | null;
  chapter: GuidedChapterSpec;
  scenes: GuidedSceneSpec[];
  linkSequentially: boolean;
  linkLabel?: string | null;
}

export interface GuidedSkeletonResult {
  arcId: string;
  chapterId: string;
  sceneIds: string[];
}
