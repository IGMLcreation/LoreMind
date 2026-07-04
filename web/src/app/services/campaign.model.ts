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
  /** Positions des nœuds du graphe de campagne (JSON `"<kind>:<id>" -> {x,y}`). Null = auto. */
  graphPositions?: string | null;
}

// Interface pour la création de Campaign (sans id)
export interface CampaignCreate {
  name: string;
  description: string;
  playerCount: number;
  loreId?: string | null;
  gameSystemId?: string | null;
}

/**
 * Type structurel d'un Arc (miroir de l'enum Java ArcType).
 * SYSTEM = arc technique « Quêtes libres » (conteneurs des quêtes hors arc), masqué de la narration.
 */
export type ArcType = 'LINEAR' | 'HUB' | 'SYSTEM';

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
}

/** Type de nœud narratif référencé par une quête (miroir Java NodeType). */
export type NodeType = 'CHAPTER' | 'SCENE';

/** Lien faible d'une quête vers un nœud narratif (miroir Java QuestNodeRef). */
export interface QuestNodeRef {
  nodeType: NodeType;
  nodeId: string;
  order: number;
}

/**
 * Quête (Niveau 1) — entité de première classe, ORTHOGONALE à l'arbre
 * Arc→Chapitre→Scène, rattachée à la campagne. Porte ses prérequis (réutilise
 * l'union Prerequisite) et référence des nœuds via QuestNodeRef.
 */
export interface Quest {
  id?: string;
  campaignId: string;
  /** Arc de rattachement (nullable). Non nul ⇒ quête d'un arc HUB ; null ⇒ transverse. */
  arcId?: string | null;
  name: string;
  description?: string;
  icon?: string | null;
  order?: number;

  /** Conditions de déblocage (ET logique). Donnée de SCÉNARIO. */
  prerequisites?: Prerequisite[];

  /** Nœuds narratifs (Chapitres / Scènes) traversés par la quête. */
  nodes?: QuestNodeRef[];

  /** Read-only — peuplé par le backend quand un playthroughId est passé. */
  progressionStatus?: ProgressionStatus;
  effectiveStatus?: QuestStatus;

  gmNotes?: string;
  playerObjectives?: string;
  narrativeStakes?: string;

  relatedPageIds?: string[];
  illustrationImageIds?: string[];
}

export interface QuestCreate {
  name: string;
  /** Arc de rattachement (nullable). Renseigné quand la quête est créée depuis un arc HUB. */
  arcId?: string | null;
  description?: string;
  icon?: string | null;
  order?: number;
  prerequisites?: Prerequisite[];
  nodes?: QuestNodeRef[];
  gmNotes?: string;
  playerObjectives?: string;
  narrativeStakes?: string;
  relatedPageIds?: string[];
  illustrationImageIds?: string[];
}

export interface Chapter {
  id?: string;
  name: string;
  description?: string;
  arcId: string;
  order?: number;
  icon?: string | null;

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
  icon?: string | null;

  gmNotes?: string;
  playerObjectives?: string;
  narrativeStakes?: string;

  relatedPageIds?: string[];
  illustrationImageIds?: string[];
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

/** Type narratif d'un nœud (Scène) — Niveau 2. Miroir de l'enum Java SceneType. */
export type SceneType = 'GENERIC' | 'LOCATION' | 'ENCOUNTER' | 'NPC' | 'EVENT' | 'REVELATION';

/** Type d'un lien narratif entre scènes — Niveau 2. Miroir de l'enum Java LinkType. */
export type LinkType = 'EXIT' | 'CLUE' | 'LEAD';

/**
 * Branche narrative : sortie possible d'une scène vers une autre du même chapitre.
 * Pendant TS du Value Object Java SceneBranch.
 */
export interface SceneBranch {
  label: string;
  targetSceneId: string;
  condition?: string;
  /** Type de lien (Niveau 2). Absent => EXIT côté backend. */
  kind?: LinkType;
}

/**
 * Battlemap étiquetée d'une scène (export Foundry) : variante Jour/Nuit, étage…
 * Pendant TS du record domaine SceneBattlemap.
 */
export interface SceneBattlemap {
  /** Libellé libre de la variante (ex : "Jour", "Nuit"). Peut être vide. */
  label: string;
  /** ID du fichier media (image/video). Null = carte sans fond. */
  mediaFileId: string | null;
  /** ID du fichier sidecar Universal VTT (.json/.dd2vtt). Null si absent. */
  dataFileId: string | null;
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

  /** Type narratif du nœud (Niveau 2). Absent => GENERIC. */
  type?: SceneType;

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

  /**
   * Battlemaps Foundry : variantes étiquetées (Jour/Nuit, étages…), chacune =
   * media (image/video) + sidecar JSON Universal VTT. Non affichées dans
   * l'appli ; transportées à l'export Foundry.
   */
  battlemaps?: SceneBattlemap[];

  /** Position du nœud dans la vue graphe du chapitre (Niveau 2). Absent => layout auto. */
  graphX?: number;
  graphY?: number;

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

  /** Type narratif du nœud (Niveau 2). */
  type?: SceneType;

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
  battlemaps?: SceneBattlemap[];
  graphX?: number;
  graphY?: number;
  branches?: SceneBranch[];
  rooms?: Room[];
}
