import { PDF_SECTIONS, PdfExportOptions, PdfSection } from '../../../services/pdf-export.model';

/** Choix de la modale d'export PDF (état éditable). */
export interface PdfExportChoice {
  edition: 'gm' | 'player';
  format: 'A4' | 'A5' | 'LETTER';
  print: boolean;
  sections: ReadonlySet<PdfSection>;
  lore: 'ALL' | 'LINKED';
}

/**
 * Préréglages d'une version : MJ = livret complet ; joueurs = ce qu'on peut leur montrer
 * (narration, PNJ, lore limité aux pages liées, cartes en grand), sans bestiaire, quêtes,
 * tables ni annexes MJ.
 */
export function editionPreset(edition: 'gm' | 'player'): Pick<PdfExportChoice, 'sections' | 'lore'> {
  return edition === 'player'
    ? { sections: new Set<PdfSection>(['NARRATIVE', 'NPCS', 'LORE', 'MAPS']), lore: 'LINKED' }
    : { sections: new Set<PdfSection>(PDF_SECTIONS), lore: 'ALL' };
}

/** Coche ou décoche une partie (nouvel ensemble, l'ancien n'est pas modifié). */
export function toggleSection(sections: ReadonlySet<PdfSection>, section: PdfSection): Set<PdfSection> {
  const next = new Set(sections);
  if (next.has(section)) next.delete(section); else next.add(section);
  return next;
}

/** Options envoyées au serveur, parties dans l'ordre canonique du livret ; null si rien n'est coché. */
export function toExportOptions(choice: PdfExportChoice): PdfExportOptions | null {
  const sections = PDF_SECTIONS.filter(s => choice.sections.has(s));
  if (sections.length === 0) return null;
  return { edition: choice.edition, format: choice.format, print: choice.print, sections, lore: choice.lore };
}
