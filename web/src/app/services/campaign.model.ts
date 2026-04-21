// Interface TypeScript pour CampaignDTO (correspond au DTO Java)
export interface Campaign {
  id?: string;
  name: string;
  description: string;
  playerCount?: number;
  arcCount?: number;
  chapterCount?: number;
  /** ID du Lore associé (weak reference cross-context). `null` = pas d'univers lié. */
  loreId?: string | null;
}

// Interface pour la création de Campaign (sans id)
export interface CampaignCreate {
  name: string;
  description: string;
  playerCount: number;
  loreId?: string | null;
}

export interface Arc {
  id?: string;
  name: string;
  description?: string;       // = Synopsis dans l'UI
  campaignId: string;
  order?: number;
  chapterCount?: number;

  // Champs narratifs enrichis
  themes?: string;
  stakes?: string;
  gmNotes?: string;
  rewards?: string;
  resolution?: string;

  /** IDs des pages du Lore liées à cet arc (weak cross-context refs). */
  relatedPageIds?: string[];

  /** IDs des images (Shared Kernel) illustrant cet arc. */
  illustrationImageIds?: string[];
}

// Payload pour la création d'un Arc (pas d'id)
export interface ArcCreate {
  name: string;
  description?: string;
  campaignId: string;
  order: number;

  themes?: string;
  stakes?: string;
  gmNotes?: string;
  rewards?: string;
  resolution?: string;

  relatedPageIds?: string[];
  illustrationImageIds?: string[];
}

export interface Chapter {
  id?: string;
  name: string;
  description?: string;
  arcId: string;
  order?: number;

  // Champs narratifs enrichis
  gmNotes?: string;
  playerObjectives?: string;
  narrativeStakes?: string;

  relatedPageIds?: string[];
  illustrationImageIds?: string[];
}

export interface ChapterCreate {
  name: string;
  description?: string;
  arcId: string;
  order: number;

  gmNotes?: string;
  playerObjectives?: string;
  narrativeStakes?: string;

  relatedPageIds?: string[];
  illustrationImageIds?: string[];
}

/**
 * Branche narrative : sortie possible d'une scène vers une autre du même chapitre.
 * Pendant TS du Value Object Java SceneBranch.
 */
export interface SceneBranch {
  label: string;
  targetSceneId: string;
  condition?: string;
}

export interface Scene {
  id?: string;
  name: string;
  description?: string;        // = Description courte dans l'UI
  chapterId: string;
  order?: number;

  // Champs narratifs enrichis
  location?: string;
  timing?: string;
  atmosphere?: string;
  playerNarration?: string;
  gmSecretNotes?: string;
  choicesConsequences?: string;
  combatDifficulty?: string;
  enemies?: string;

  relatedPageIds?: string[];
  illustrationImageIds?: string[];

  /** Sorties narratives (graphe intra-chapitre). */
  branches?: SceneBranch[];
}

export interface SceneCreate {
  name: string;
  description?: string;
  chapterId: string;
  order: number;

  location?: string;
  timing?: string;
  atmosphere?: string;
  playerNarration?: string;
  gmSecretNotes?: string;
  choicesConsequences?: string;
  combatDifficulty?: string;
  enemies?: string;

  relatedPageIds?: string[];
  illustrationImageIds?: string[];
  branches?: SceneBranch[];
}
