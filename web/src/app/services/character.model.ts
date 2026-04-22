/**
 * Fiche de personnage joueur (PJ) d'une campagne.
 * MVP : markdownContent libre. Évolution prévue vers des fiches templatées
 * par GameSystem (stats structurées selon le JDR joué).
 */
export interface Character {
  id?: string;
  name: string;
  markdownContent?: string | null;
  campaignId: string;
  order?: number;
}

export interface CharacterCreate {
  name: string;
  markdownContent?: string | null;
  campaignId: string;
}
