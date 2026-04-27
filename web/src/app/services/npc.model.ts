/**
 * Fiche de personnage non-joueur (PNJ) d'une campagne.
 * MVP : markdownContent libre (description, motivation, stats, notes MJ).
 * Évolution prévue : templating partagé PJ/PNJ piloté par GameSystem.
 */
export interface Npc {
  id?: string;
  name: string;
  markdownContent?: string | null;
  campaignId: string;
  order?: number;
}

export interface NpcCreate {
  name: string;
  markdownContent?: string | null;
  campaignId: string;
}
