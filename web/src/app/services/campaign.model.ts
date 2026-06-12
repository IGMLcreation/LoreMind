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
  /** ID du GameSystem associé (weak reference cross-context). `null` = campagne générique. */
  gameSystemId?: string | null;
}

// Interface pour la création de Campaign (sans id)
export interface CampaignCreate {
  name: string;
  description: string;
  playerCount: number;
  loreId?: string | null;
  gameSystemId?: string | null;
}

/** Type structurel d'un Arc (miroir de l'enum Java ArcType). */
export type ArcType = 'LINEAR' | 'HUB';

/** Statut de progression piloté manuellement par le MJ (persisté). */
export type ProgressionStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED';

/**
 * Statut effectif d'une quête tel qu'affiché dans la vue Hub.
 * Calculé côté backend — ne jamais le re-dériver côté front.
 */
export type QuestStatus = 'LOCKED' | 'AVAILABLE' | 'IN_PROGRESS' | 'COMPLETED';

/**
 * Condition de déblocage d'une quête. Union TS discriminée sur `kind` —
 * miroir du sealed type Java Prerequisite. Le narrowing TS marche sur le `kind`.
 */
export type Prerequisite =
    | { kind: 'QUEST_COMPLETED'; questId: string }
    | { kind: 'SESSION_REACHED'; minSessionNumber: number }
    | { kind: 'FLAG_SET'; flagName: string };

export interface Arc {
  id?: string;
  name: string;
  description?: string;       // = Synopsis dans l'UI
  campaignId: string;
  order?: number;
  chapterCount?: number;

  /** Type structurel (défaut LINEAR côté backend si omis). */
  type?: ArcType;

  /** Cle d'icone choisie par l'utilisateur (cf. CAMPAIGN_ICON_OPTIONS). */
  icon?: string | null;

  // Champs narratifs enrichis
  themes?: string;
  stakes?: string;
  gmNotes?: string;
  rewards?: string;
  resolution?: string;

  /** IDs des pages du Lore liées à cet arc (weak cross-context refs). */
  relatedPageIds?: string[];

  /** IDs des images (Shared Kernel) illustrant cet arc (ambiance). */
  illustrationImageIds?: string[];

  /** IDs des images utilisees comme cartes / plans (outil de table). */
  mapImageIds?: string[];
}

// Payload pour la création d'un Arc (pas d'id)
export interface ArcCreate {
  name: string;
  description?: string;
  campaignId: string;
  order: number;
  type?: ArcType;
  icon?: string | null;

  themes?: string;
  stakes?: string;
  gmNotes?: string;
  rewards?: string;
  resolution?: string;

  relatedPageIds?: string[];
  illustrationImageIds?: string[];
  mapImageIds?: string[];
}

export interface Chapter {
  id?: string;
  name: string;
  description?: string;
  arcId: string;
  order?: number;
  icon?: string | null;

  /** Conditions de déblocage (ET logique). Donnée de SCÉNARIO. */
  prerequisites?: Prerequisite[];

  /**
   * Statut de progression read-only — peuplé par le backend uniquement quand
   * un playthroughId est passé en query param. Ne pas inclure en écriture.
   */
  progressionStatus?: ProgressionStatus;

  /**
   * Statut effectif read-only — idem, dépend du Playthrough.
   */
  effectiveStatus?: QuestStatus;

  // Champs narratifs enrichis
  gmNotes?: string;
  playerObjectives?: string;
  narrativeStakes?: string;

  relatedPageIds?: string[];
  illustrationImageIds?: string[];
  mapImageIds?: string[];
}

export interface ChapterCreate {
  name: string;
  description?: string;
  arcId: string;
  order: number;
  icon?: string | null;

  prerequisites?: Prerequisite[];

  gmNotes?: string;
  playerObjectives?: string;
  narrativeStakes?: string;

  relatedPageIds?: string[];
  illustrationImageIds?: string[];
  mapImageIds?: string[];
}

/**
 * Partie / instance jouée d'une Campagne par une table donnée.
 * Porte la progression dynamique des quêtes, les flags et les sessions.
 */
export interface Playthrough {
  id?: string;
  campaignId: string;
  name: string;
  description?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface PlaythroughCreate {
  campaignId: string;
  name: string;
  description?: string;
}

/**
 * Valeur courante d'un fait pour une Partie donnée.
 * Pas de déclaration explicite : un fait existe dès qu'au moins une quête le
 * référence via un prérequis FLAG_SET.
 */
export interface PlaythroughFlag {
  name: string;
  value: boolean;
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

/**
 * Sortie d'une pièce vers une autre pièce du même lieu explorable.
 * Pendant TS du record domaine RoomBranch.
 */
export interface RoomBranch {
  label: string;
  targetRoomId: string;
  condition?: string;
}

/**
 * Pièce d'un lieu explorable attachée à une Scene.
 * Dès qu'une Scene a au moins une Room, elle bascule sur le rendu « donjon ».
 */
export interface Room {
  /** UUID stable généré côté client à la création (sert de cible aux RoomBranch). */
  id: string;
  name: string;
  description?: string;
  enemies?: string;
  /** IDs des fiches du bestiaire présentes dans la pièce (weak refs). */
  enemyIds?: string[];
  loot?: string;
  traps?: string;
  gmNotes?: string;
  /** Étage : 0 = RdC, 1 = 1er etc. null/undefined = pas d'étage défini. */
  floor?: number | null;
  order: number;
  illustrationImageIds?: string[];
  mapImageId?: string | null;
  branches?: RoomBranch[];
}

export interface Scene {
  id?: string;
  name: string;
  description?: string;        // = Description courte dans l'UI
  chapterId: string;
  order?: number;
  icon?: string | null;

  // Champs narratifs enrichis
  location?: string;
  timing?: string;
  atmosphere?: string;
  playerNarration?: string;
  gmSecretNotes?: string;
  choicesConsequences?: string;
  combatDifficulty?: string;
  enemies?: string;

  /** IDs des fiches du bestiaire engagées dans la rencontre (weak refs). */
  enemyIds?: string[];

  relatedPageIds?: string[];
  illustrationImageIds?: string[];
  mapImageIds?: string[];

  /** Sorties narratives (graphe intra-chapitre). */
  branches?: SceneBranch[];

  /** Pièces explorables. Vide = scène classique, non-vide = lieu type donjon. */
  rooms?: Room[];
}

export interface SceneCreate {
  name: string;
  description?: string;
  chapterId: string;
  order: number;
  icon?: string | null;

  location?: string;
  timing?: string;
  atmosphere?: string;
  playerNarration?: string;
  gmSecretNotes?: string;
  choicesConsequences?: string;
  combatDifficulty?: string;
  enemies?: string;

  /** IDs des fiches du bestiaire engagées dans la rencontre (weak refs). */
  enemyIds?: string[];

  relatedPageIds?: string[];
  illustrationImageIds?: string[];
  mapImageIds?: string[];
  branches?: SceneBranch[];
  rooms?: Room[];
}
