import { describe, it, expect } from 'vitest';
import {
  hasBlockLayout,
  orderedBlocks,
  blockGridColumn,
  blockGridRow,
  blockKey,
} from './block-layout.helper';
import { TemplateField } from '../services/template.model';

function field(name: string, pos?: TemplateField['pos']): TemplateField {
  return { id: name, name, type: 'TEXT', pos };
}

describe('blockKey', () => {
  it('utilise l id du bloc quand il est present', () => {
    expect(blockKey({ id: 'blk-1', name: 'Ambiance', type: 'TEXT' })).toBe('blk-1');
  });

  it('retombe sur le nom quand l id est absent ou vide (templates legacy)', () => {
    expect(blockKey({ name: 'Ambiance', type: 'TEXT' })).toBe('Ambiance');
    expect(blockKey({ id: '  ', name: 'Ambiance', type: 'TEXT' })).toBe('Ambiance');
  });

  it('reste stable quand le nom change mais pas l id (renommage)', () => {
    expect(blockKey({ id: 'blk-1', name: 'Nouveau nom', type: 'TEXT' })).toBe('blk-1');
  });
});

describe('hasBlockLayout', () => {
  it('false quand aucun bloc n a de position (templates legacy)', () => {
    expect(hasBlockLayout([field('A'), field('B')])).toBe(false);
  });

  it('false pour une liste vide / nullish', () => {
    expect(hasBlockLayout([])).toBe(false);
    expect(hasBlockLayout(null)).toBe(false);
    expect(hasBlockLayout(undefined)).toBe(false);
  });

  it('true des qu un bloc porte x, y ou w', () => {
    expect(hasBlockLayout([field('A'), field('B', { x: 0 })])).toBe(true);
    expect(hasBlockLayout([field('A', { w: 6 })])).toBe(true);
    expect(hasBlockLayout([field('A', { y: 2 })])).toBe(true);
  });

  it('false quand pos est present mais entierement nul', () => {
    expect(hasBlockLayout([field('A', {})])).toBe(false);
  });
});

describe('orderedBlocks', () => {
  it('conserve l ordre du tableau (et les references) sans mise en page', () => {
    const a = field('A');
    const b = field('B');
    const out = orderedBlocks([a, b]);
    expect(out).toEqual([a, b]);
    expect(out[0]).toBe(a); // meme reference -> track field stable
  });

  it('trie par (ligne, colonne) quand une mise en page est presente', () => {
    const topRight = field('TR', { x: 6, y: 0, w: 6 });
    const topLeft = field('TL', { x: 0, y: 0, w: 6 });
    const bottom = field('B', { x: 0, y: 1, w: 12 });
    const sorted = orderedBlocks([bottom, topRight, topLeft]);
    expect(sorted.map(f => f.name)).toEqual(['TL', 'TR', 'B']);
  });

  it('ne mute pas le tableau source', () => {
    const src = [field('B', { x: 6, y: 0 }), field('A', { x: 0, y: 0 })];
    const copy = [...src];
    orderedBlocks(src);
    expect(src).toEqual(copy);
  });
});

describe('blockGridColumn', () => {
  it('null sans colonne definie', () => {
    expect(blockGridColumn(field('A'))).toBeNull();
    expect(blockGridColumn(field('A', { y: 3 }))).toBeNull();
  });

  it('convertit x (0-based) en colonne 1-based avec span w', () => {
    expect(blockGridColumn(field('A', { x: 0, w: 6 }))).toBe('1 / span 6');
    expect(blockGridColumn(field('A', { x: 6, w: 6 }))).toBe('7 / span 6');
  });

  it('pleine largeur (span 12) quand w absent', () => {
    expect(blockGridColumn(field('A', { x: 0 }))).toBe('1 / span 12');
  });
});

describe('blockGridRow', () => {
  it('null sans ligne definie', () => {
    expect(blockGridRow(field('A'))).toBeNull();
    expect(blockGridRow(field('A', { x: 0, w: 6 }))).toBeNull();
  });

  it('place sur la ligne y+1 avec la hauteur par defaut quand h absent', () => {
    expect(blockGridRow(field('A', { y: 0 }))).toBe('1 / span 4');
    expect(blockGridRow(field('A', { y: 2 }))).toBe('3 / span 4');
  });

  it('place avec span h quand h present', () => {
    expect(blockGridRow(field('A', { y: 1, h: 6 }))).toBe('2 / span 6');
  });
});
