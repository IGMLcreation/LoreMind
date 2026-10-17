import { editionPreset, toExportOptions, toggleSection, PdfExportChoice } from './pdf-export-presets';
import { safeFileName } from '../../../shared/download.util';

describe('PDF export presets', () => {
  const gm = (): PdfExportChoice => ({ edition: 'gm', format: 'A4', print: false, ...editionPreset('gm') });

  it('GM edition = complete booklet, whole lore', () => {
    expect(toExportOptions(gm())).toEqual({
      edition: 'gm', format: 'A4', print: false,
      sections: ['NARRATIVE', 'QUESTS', 'NPCS', 'ENEMIES', 'TABLES', 'LORE'], lore: 'ALL',
    });
  });

  it('player edition preselects player-safe parts and linked lore only', () => {
    const o = toExportOptions({ edition: 'player', format: 'A5', print: true, ...editionPreset('player') })!;
    expect(o.sections).toEqual(['NARRATIVE', 'NPCS', 'LORE']);
    expect(o.lore).toBe('LINKED');
    expect(o.format).toBe('A5');
    expect(o.print).toBe(true);
  });

  it('keeps the canonical order when a section is unchecked then checked again', () => {
    const base = gm();
    const sections = toggleSection(toggleSection(base.sections, 'NARRATIVE'), 'NARRATIVE');
    expect(toExportOptions({ ...base, sections })!.sections[0]).toBe('NARRATIVE');
    expect(base.sections.has('NARRATIVE')).toBe(true); // ensemble d'origine intact
  });

  it('refuses an empty selection', () => {
    expect(toExportOptions({ ...gm(), sections: new Set() })).toBeNull();
  });
});

describe('safeFileName', () => {
  it('matches the server-side slug', () => {
    expect(safeFileName("Les Cendres d'Été", 'campagne')).toBe('Les_Cendres_d_t');
    expect(safeFileName('  ', 'campagne')).toBe('campagne');
    expect(safeFileName(null, 'campagne')).toBe('campagne');
  });
});
