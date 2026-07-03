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
  it('produit les nœuds fixes (quêtes, PNJ, ennemis, tables, ateliers, catalogues, import) quand vide', () => {
    const tree = buildCampaignTree('c1', data(), t);
    expect(tree.map((n) => n.id)).toEqual([
      'quests-root', 'npcs-root', 'enemies-root', 'random-tables-root',
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

  it('imbrique chapitres et scènes', () => {
    const tree = buildCampaignTree('camp', data({
      arcs: [{ id: 'a1', name: 'Arc', type: 'LINEAR' }] as never,
      // 2 chapitres pour éviter le mode plat (Niveau 0), qui masque arc + chapitre
      // quand il n'y a qu'un seul arc d'un seul chapitre.
      chaptersByArc: { a1: [{ id: 'c1', name: 'Chap' }, { id: 'c2', name: 'Chap 2' }] } as never,
      scenesByChapter: { c1: [{ id: 's1', name: 'Scene' }] } as never,
    }), t);
    const arc = tree.find((n) => n.id === 'arc-a1')!;
    const chapter = arc.children!.find((c) => c.id === 'chapter-c1')!;
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

  it('arc LINÉAIRE = chapitres ; arc HUB = quêtes (bouton « nouvelle quête » uniquement)', () => {
    const hub = buildCampaignTree('c', data({
      arcs: [{ id: 'a', name: 'Hub', type: 'HUB' }] as never,
      quests: [{ id: 'q1', name: 'Enquête', arcId: 'a' }, { id: 'q2', name: 'Transverse', arcId: null }] as never,
    }), t);
    const hubArc = hub.find((n) => n.id === 'arc-a')!;
    expect(hubArc.children!.some((c) => c.id === 'quest-q1')).toBe(true);   // rattachée → enfant
    expect(hubArc.children!.some((c) => c.id === 'quest-q2')).toBe(false);  // transverse → pas ici
    // HUB : on ne crée QUE des quêtes (pas de « nouveau chapitre »).
    expect(hubArc.createActions!.map((a) => a.label)).toEqual(['campaignTree.newQuest']);
    expect(hubArc.createActions![0].queryParams).toEqual({ arcId: 'a' });

    // LINEAR : uniquement des chapitres ; une quête même arcId=arc n'apparaît PAS sous l'arc.
    const linear = buildCampaignTree('c', data({
      arcs: [{ id: 'a', name: 'Lin', type: 'LINEAR' }] as never,
      quests: [{ id: 'q3', name: 'X', arcId: 'a' }] as never,
    }), t);
    const linArc = linear.find((n) => n.id === 'arc-a')!;
    expect(linArc.children!.some((c) => c.id === 'quest-q3')).toBe(false);
    expect(linArc.createActions!.map((a) => a.label)).toEqual(['campaignTree.newChapter']);

    // Le nœud « Quêtes » dédié conserve son action de création.
    expect(hub.find((n) => n.id === 'quests-root')!.createActions![0].label).toBe('campaignTree.newQuest');
  });

  it('pastilles : un manque de QUÊTE n\'allume PAS une scène/PNJ portant le même id (séquences par table)', () => {
    // Chaque table a sa propre séquence IDENTITY : quête id 7 et scène id 7 coexistent.
    const gaps = [{
      entityType: 'QUEST', entityId: '7', entityName: 'Q', ruleId: 'QUEST-001-NO-NODES',
      message: 'm', severity: 'BLOCKING', arcId: null, chapterId: null,
    }];
    const tree = buildCampaignTree('c', data({
      arcs: [{ id: 'a1', name: 'Arc', type: 'LINEAR' }] as never,
      chaptersByArc: { a1: [{ id: 'c1', name: 'Chap' }, { id: 'c2', name: 'Chap 2' }] } as never,
      scenesByChapter: { c1: [{ id: '7', name: 'Scène homonyme' }] } as never,
      npcs: [{ id: '7', name: 'PNJ homonyme' }] as never,
      quests: [{ id: '7', name: 'Q', arcId: null }] as never,
    }), t, gaps as never);
    const arc = tree.find((n) => n.id === 'arc-a1')!;
    const scene = arc.children!.find((c) => c.id === 'chapter-c1')!.children!.find((s) => s.id === 'scene-7')!;
    expect(scene.statusDot).toBeUndefined();                                   // pas de faux positif
    expect(tree.find((n) => n.id === 'npcs-root')!.statusDot).toBeUndefined(); // idem PNJ
    expect(tree.find((n) => n.id === 'quests-root')!.statusDot).toBe('blocking'); // le vrai coupable
  });

  it('arc HUB : une quête FUSIONNE avec son chapitre jumeau (un seul nœud, scènes dessous)', () => {
    // Héritage V10 : la quête référence son chapitre jumeau via un nœud CHAPTER.
    const tree = buildCampaignTree('c', data({
      arcs: [{ id: 'a', name: 'Hub', type: 'HUB' }] as never,
      chaptersByArc: { a: [{ id: 'c1', name: 'Jumeau' }, { id: 'c2', name: 'Chapitre libre' }] } as never,
      scenesByChapter: { c1: [{ id: 's1', name: 'Scène du jumeau' }] } as never,
      quests: [{ id: 'q1', name: 'La quête', arcId: 'a', nodes: [{ nodeType: 'CHAPTER', nodeId: 'c1', order: 0 }] }] as never,
    }), t);
    const hubArc = tree.find((n) => n.id === 'arc-a')!;
    // La quête absorbe le jumeau : pas de nœud chapter-c1, ses scènes vivent sous quest-q1.
    expect(hubArc.children!.some((c) => c.id === 'chapter-c1')).toBe(false);
    const questNode = hubArc.children!.find((c) => c.id === 'quest-q1')!;
    expect(questNode.children!.some((c) => c.id === 'scene-s1')).toBe(true);
    expect(questNode.dropParentId).toBe('c1'); // le jumeau reste le conteneur des scènes
    // Le chapitre NON consommé reste affiché tel quel (contenu mixte/historique).
    expect(hubArc.children!.some((c) => c.id === 'chapter-c2')).toBe(true);
  });

  it("l'arc SYSTEM (« Quêtes libres ») est masqué de la narration", () => {
    const tree = buildCampaignTree('c', data({
      arcs: [
        { id: 'a1', name: 'Arc visible', type: 'LINEAR' },
        { id: 'sys', name: 'Quêtes libres', type: 'SYSTEM' },
      ] as never,
      chaptersByArc: {
        a1: [{ id: 'c1', name: 'Chap' }, { id: 'c2', name: 'Chap 2' }],
        sys: [{ id: 'cf', name: 'Conteneur' }],
      } as never,
    }), t);
    expect(tree.some((n) => n.id === 'arc-sys')).toBe(false); // pas dans l'arbre
    expect(tree.some((n) => n.id === 'arc-a1')).toBe(true);
  });

  it('quête LIBRE : fusionnée sous « Quêtes » avec les scènes de son conteneur SYSTEM et « + scène »', () => {
    const tree = buildCampaignTree('c', data({
      arcs: [{ id: 'sys', name: 'Quêtes libres', type: 'SYSTEM' }] as never,
      chaptersByArc: { sys: [{ id: 'cf', name: 'Libre', arcId: 'sys' }] } as never,
      scenesByChapter: { cf: [{ id: 's1', name: 'Scène à la volée' }] } as never,
      quests: [{ id: 'q1', name: 'Libre', arcId: null, nodes: [{ nodeType: 'CHAPTER', nodeId: 'cf', order: 0 }] }] as never,
    }), t);
    const questsRoot = tree.find((n) => n.id === 'quests-root')!;
    const questNode = questsRoot.children!.find((c) => c.id === 'quest-q1')!;
    expect(questNode.children!.some((c) => c.id === 'scene-s1')).toBe(true);
    expect(questNode.dropParentId).toBe('cf'); // le conteneur reçoit les drops de scènes
    // « + scène » pointe vers la création de scène DANS le conteneur (arc SYSTEM).
    expect(questNode.createActions![0].route).toBe('/campaigns/c/arcs/sys/chapters/cf/scenes/create');
  });
});
