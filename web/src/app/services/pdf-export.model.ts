/** Parties du livret PDF qu'on peut inclure ou non (miroir de PdfExportOptions.Section). */
export type PdfSection = 'NARRATIVE' | 'QUESTS' | 'NPCS' | 'ENEMIES' | 'TABLES' | 'LORE' | 'APPENDIX' | 'MAPS';

/** Ordre canonique des parties dans le livret. */
export const PDF_SECTIONS: PdfSection[] = ['NARRATIVE', 'QUESTS', 'NPCS', 'ENEMIES', 'TABLES', 'LORE', 'APPENDIX', 'MAPS'];

/**
 * Options de l'export PDF (modale) :
 * - edition   : 'gm' = livret MJ complet ; 'player' = version joueurs sans aucun secret.
 * - format    : A4, A5 ou US Letter.
 * - print     : sans aplats de couleur (économie d'encre).
 * - sections  : parties incluses.
 * - lore      : toutes les pages du lore, ou seulement celles liées au contenu de la campagne.
 */
export interface PdfExportOptions {
  edition: 'gm' | 'player';
  format: 'A4' | 'A5' | 'LETTER';
  print: boolean;
  sections: PdfSection[];
  lore: 'ALL' | 'LINKED';
}
