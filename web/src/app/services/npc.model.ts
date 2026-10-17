/**
 * Fiche de personnage non-joueur (PNJ) d'une campagne.
 * Refonte 2026-04-30 : meme structure que Character (template-based).
 */
export interface Npc {
  id?: string;
  name: string;
  portraitImageId?: string | null;
  headerImageId?: string | null;
  values?: Record<string, string>;
  imageValues?: Record<string, string[]>;
  keyValueValues?: Record<string, Record<string, string>>;
  campaignId: string;
  /** IDs de Pages de Lore référencées (sa ville, sa faction…). */
  relatedPageIds?: string[];
  /** Dossier de classement (ex. « Bard's Gate »). Vide/absent = non classé. */
  folder?: string | null;
  order?: number;
  /** Rôle dans l'histoire (indépendant du système de jeu). */
  story?: NpcStory | null;
}

/** Place d'un PNJ dans l'histoire. */
export type NpcRole = 'OPPONENT' | 'ALLY' | 'RESOURCE' | 'EXTRA';

export const NPC_ROLES: NpcRole[] = ['OPPONENT', 'ALLY', 'RESOURCE', 'EXTRA'];

/** Fiche « rôle dans l'histoire » d'un PNJ (miroir du record Java NpcStory). */
export interface NpcStory {
  role?: NpcRole | null;
  /** Ce qu'il veut, et ce qu'il est prêt à faire. */
  motivation?: string | null;
  /** Ce qu'il sait (réservé au MJ). */
  knowledge?: string | null;
  /** Signe distinctif à jouer (geste, phrasé, tic). */
  mannerism?: string | null;
}

export interface NpcCreate {
  name: string;
  portraitImageId?: string | null;
  headerImageId?: string | null;
  values?: Record<string, string>;
  imageValues?: Record<string, string[]>;
  keyValueValues?: Record<string, Record<string, string>>;
  campaignId: string;
  relatedPageIds?: string[];
  folder?: string | null;
  story?: NpcStory | null;
}
