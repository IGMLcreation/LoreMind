import { LinkType, SceneType } from './campaign.model';

/**
 * Squelette d'aventure produit par une méthode guidée (Mentor d'écriture, phase M3),
 * envoyé à POST /api/campaigns/{id}/guided-skeleton. Miroir du record Java `GuidedSkeleton`.
 */
export interface GuidedChapterSpec {
  name: string;
  description?: string | null;
  playerObjectives?: string | null;
  narrativeStakes?: string | null;
  /** Notes réservées au MJ (vérité cachée, plan complet du méchant). */
  gmNotes?: string | null;
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
  /** Liens explicites entre scènes (indices dans `scenes`) : réseau d'une enquête. */
  links?: GuidedLinkSpec[] | null;
  /** Horloge créée dans chaque partie de la campagne (plan du méchant). */
  clock?: GuidedClockSpec | null;
}

export interface GuidedLinkSpec {
  from: number;
  to: number;
  label?: string | null;
  kind: LinkType;
}

export interface GuidedClockSpec {
  name: string;
  description?: string | null;
  segments: number;
}

export interface GuidedSkeletonResult {
  arcId: string;
  chapterId: string;
  sceneIds: string[];
  /** Horloges créées (une par partie de la campagne). */
  clocks?: number;
}
