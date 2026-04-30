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
  order?: number;
}

export interface NpcCreate {
  name: string;
  portraitImageId?: string | null;
  headerImageId?: string | null;
  values?: Record<string, string>;
  imageValues?: Record<string, string[]>;
  keyValueValues?: Record<string, Record<string, string>>;
  campaignId: string;
}
