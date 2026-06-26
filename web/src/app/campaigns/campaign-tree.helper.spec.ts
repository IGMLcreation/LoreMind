import { describe, it, expect } from 'vitest';
import { buildCampaignTree, CampaignTreeData } from './campaign-tree.helper';
import { TranslateService } from '@ngx-translate/core';

// TranslateService factice : renvoie la clé telle quelle (suffisant pour vérifier
// la structure de l'arbre sans dépendre des traductions).
const t = { instant: (k: string) => k } as unknown as TranslateService;

function data(partial: Partial<CampaignTreeData> = {}): CampaignTreeData {
  return {
    arcs: [], chaptersByArc: {}, scenesByChapter: {},
    npcs: [], randomTables: [], enemies: [],
    ...partial,
  };
}

describe('buildCampaignTree', () => {
  it('produit les nœuds fixes (PNJ, ennemis, tables, ateliers, catalogues, import) quand vide', () => {
    const tree = buildCampaignTree('c1', data(), t);
    expect(tree.map((n) => n.id)).toEqual([
      'npcs-root', 'enemies-root', 'random-tables-root',
      'notebooks-root', 'item-catalogs-root', 'import-pdf-root',
    ]);
  });

  it('trie les arcs par ordre manuel (champ order, réagencé au glisser-déposer)', () => {
    const arcs = [
      { id: 'a', name: 'Final', order: 2 },
      { id: 'b', name: 'Voyage', order: 1 },
      { id: 'c', name: 'Intro', order: 0 },
    ];
    const tree = buildCampaignTree('c1', data({ arcs: arcs as never }), t);
    const arcLabels = tree.filter((n) => n.id?.startsWith('arc-')).map((n) => n.label);
    expect(arcLabels).toEqual(['Intro', 'Voyage', 'Final']);
  });

  it('imbrique chapitres et scènes et marque les chapitres à prérequis', () => {
    const tree = buildCampaignTree('camp', data({
      arcs: [{ id: 'a1', name: 'Arc', type: 'LINEAR' }] as never,
      chaptersByArc: { a1: [{ id: 'c1', name: 'Chap', prerequisites: [{}] }] } as never,
      scenesByChapter: { c1: [{ id: 's1', name: 'Scene' }] } as never,
    }), t);
    const arc = tree.find((n) => n.id === 'arc-a1')!;
    const chapter = arc.children![0];
    expect(chapter.id).toBe('chapter-c1');
    expect(chapter.meta).toBe('🔒');           // cadenas si prérequis
    expect(chapter.children![0].id).toBe('scene-s1');
  });

  it('regroupe les PNJ par dossier et place les non classés dans un pseudo-dossier', () => {
    const npcs = [
      { id: 'n1', name: 'Alice', folder: 'Ville' },
      { id: 'n2', name: 'Bob', folder: 'Ville' },
      { id: 'n3', name: 'Carl' },
    ];
    const tree = buildCampaignTree('c', data({ npcs: npcs as never }), t);
    const npcsRoot = tree.find((n) => n.id === 'npcs-root')!;
    expect(npcsRoot.meta).toBe('3');
    const folder = npcsRoot.children!.find((c) => c.id === 'npc-folder-Ville')!;
    expect(folder.children!.map((c) => c.label)).toEqual(['Alice', 'Bob']);
    // Les non classés vont dans un pseudo-dossier « Sans dossier » (et non en enfants
    // directs) : tous les dossiers restent FRÈRES → glisser-déposer inter-dossiers fiable.
    const none = npcsRoot.children!.find((c) => c.id === 'npc-folder-__none__')!;
    expect(none.children!.some((c) => c.id === 'npc-n3')).toBe(true);
  });

  it("affiche le niveau de l'ennemi en méta", () => {
    const tree = buildCampaignTree('c', data({
      enemies: [{ id: 'e1', name: 'Gobelin', level: 3, folder: 'Cave' }] as never,
    }), t);
    const enemiesRoot = tree.find((n) => n.id === 'enemies-root')!;
    const folder = enemiesRoot.children!.find((c) => c.id === 'enemy-folder-Cave')!;
    expect(folder.children![0].meta).toBe('Niv. 3');
  });

  it('libelle « nouvelle quête » dans un arc HUB (vs « nouveau chapitre »)', () => {
    const hub = buildCampaignTree('c', data({ arcs: [{ id: 'a', name: 'Hub', type: 'HUB' }] as never }), t)
      .find((n) => n.id === 'arc-a')!;
    expect(hub.createActions![0].label).toBe('campaignTree.newQuest');

    const linear = buildCampaignTree('c', data({ arcs: [{ id: 'a', name: 'Lin', type: 'LINEAR' }] as never }), t)
      .find((n) => n.id === 'arc-a')!;
    expect(linear.createActions![0].label).toBe('campaignTree.newChapter');
  });
});
