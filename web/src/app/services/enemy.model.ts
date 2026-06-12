/**
 * Fiche d'ennemi (monstre/créature) d'une campagne — le bestiaire du MJ.
 * Même structure de templating que Npc : champs pilotés par le template
 * ENNEMI du GameSystem, + champs universels niveau/dossier.
 */
export interface Enemy {
  id?: string;
  name: string;
  /** Niveau / FP / dangerosité — texte libre (« 5 », « FP 8 », « Boss »). */
  level?: string | null;
  /** Dossier de classement (« Démons », « Humanoïdes »…). Vide = non classé. */
  folder?: string | null;
  portraitImageId?: string | null;
  headerImageId?: string | null;
  values?: Record<string, string>;
  imageValues?: Record<string, string[]>;
  keyValueValues?: Record<string, Record<string, string>>;
  campaignId: string;
  order?: number;
}

export interface EnemyCreate {
  name: string;
  level?: string | null;
  folder?: string | null;
  portraitImageId?: string | null;
  headerImageId?: string | null;
  values?: Record<string, string>;
  imageValues?: Record<string, string[]>;
  keyValueValues?: Record<string, Record<string, string>>;
  campaignId: string;
}
