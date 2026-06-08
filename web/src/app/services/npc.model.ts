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
  /** Dossier de classement (ex. « Bard's Gate »). Vide/absent = non classé. */
  folder?: string | null;
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
  folder?: string | null;
}
