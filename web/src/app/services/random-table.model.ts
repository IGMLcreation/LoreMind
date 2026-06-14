/**
 * Reflet du RandomTableDTO côté Core. Une table aléatoire campagne + ses entrées.
 */
export interface RandomTableEntry {
  /** Borne basse du jet (incluse). */
  minRoll: number;
  /** Borne haute du jet (incluse). */
  maxRoll: number;
  /** Résultat court. */
  label: string;
  /** Détail markdown (« ce que c'est »). */
  detail?: string;
}

export interface RandomTable {
  id?: string;
  name: string;
  description?: string;
  /** Formule du dé : "1d20", "2d6", "d100"… */
  diceFormula: string;
  icon?: string;
  campaignId: string;
  order?: number;
  entries: RandomTableEntry[];
}

export interface RandomTableCreate {
  name: string;
  description?: string;
  diceFormula: string;
  icon?: string;
  campaignId: string;
  entries: RandomTableEntry[];
}
