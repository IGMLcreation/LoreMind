/**
 * Fiche de personnage joueur (PJ) d'une campagne.
 * Refonte 2026-04-30 : abandon du markdownContent au profit d'un systeme
 * template-based pilote par le GameSystem de la campagne.
 *  - portraitImageId / headerImageId : champs universels hard-codes
 *  - values : Map<champ template TEXT/NUMBER, valeur>
 *  - imageValues : Map<champ template IMAGE, liste d'IDs d'images>
 */
export interface Character {
  id?: string;
  name: string;
  portraitImageId?: string | null;
  headerImageId?: string | null;
  values?: Record<string, string>;
  imageValues?: Record<string, string[]>;
  /** Champs KEY_VALUE_LIST : fieldName -> label -> value. */
  keyValueValues?: Record<string, Record<string, string>>;
  campaignId: string;
  order?: number;
}

export interface CharacterCreate {
  name: string;
  portraitImageId?: string | null;
  headerImageId?: string | null;
  values?: Record<string, string>;
  imageValues?: Record<string, string[]>;
  keyValueValues?: Record<string, Record<string, string>>;
  campaignId: string;
}
