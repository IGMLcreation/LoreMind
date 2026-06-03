/**
 * Fiche de personnage joueur (PJ) d'une Partie (Playthrough).
 * Refonte Playthrough : les PJ appartiennent à la Partie (table jouée),
 * plus à la Campagne (scénario).
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
  playthroughId: string;
  order?: number;
}

export interface CharacterCreate {
  name: string;
  portraitImageId?: string | null;
  headerImageId?: string | null;
  values?: Record<string, string>;
  imageValues?: Record<string, string[]>;
  keyValueValues?: Record<string, Record<string, string>>;
  playthroughId: string;
}
