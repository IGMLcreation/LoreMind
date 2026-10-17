import { TreeItem } from '../../services/layout.service';
import { countLabel, findPath, resolveStack, stackForActive } from './tree-drilldown';

const scene = (id: string): TreeItem => ({ id, label: id, route: `/s/${id}` });
const tree = (): TreeItem[] => [
  { id: 'parties', label: 'Parties', children: [scene('p1')] },
  {
    id: 'arc1', label: 'Acte I', route: '/arc1', children: [
      { id: 'ch1', label: 'Chapitre 1', route: '/ch1', children: [scene('s1'), scene('s2')] },
      { id: 'ch2', label: 'Chapitre 2', route: '/ch2' }
    ]
  },
  { id: 'npcs', label: 'PNJ', meta: '3', children: [{ id: 'new', label: '+ PNJ', isAction: true, route: '/new' }] },
  { id: 'tables', label: 'Tables', route: '/tables' }
];

describe('tree drill-down', () => {
  it('finds the path from the root to a node', () => {
    expect(findPath(tree(), 's2')?.map(n => n.id)).toEqual(['arc1', 'ch1', 's2']);
    expect(findPath(tree(), 'nope')).toBeNull();
  });

  it('resolves a stack of ids against a rebuilt tree, stopping at what disappeared', () => {
    expect(resolveStack(tree(), ['arc1', 'ch1']).map(n => n.id)).toEqual(['arc1', 'ch1']);
    expect(resolveStack(tree(), ['arc1', 'gone', 'x']).map(n => n.id)).toEqual(['arc1']);
    // ch2 n'a pas d'enfants : on ne peut pas « entrer » dedans.
    expect(resolveStack(tree(), ['arc1', 'ch2']).map(n => n.id)).toEqual(['arc1']);
  });

  it('opens the level of the current page', () => {
    // Sur une scène : on voit ses sœurs (le chapitre).
    expect(stackForActive(tree(), i => i.id === 's2')).toEqual(['arc1', 'ch1']);
    // Sur un arc : on voit son contenu.
    expect(stackForActive(tree(), i => i.id === 'arc1')).toEqual(['arc1']);
    // Sur une feuille racine, ou nulle part : la racine.
    expect(stackForActive(tree(), i => i.id === 'tables')).toEqual([]);
    expect(stackForActive(tree(), () => false)).toEqual([]);
  });

  it('prefers the deepest active node', () => {
    expect(stackForActive(tree(), i => i.id === 'arc1' || i.id === 'ch1')).toEqual(['arc1', 'ch1']);
  });

  it('shows the given badge, else the number of real children', () => {
    const [parties, arc1, npcs, tables] = tree();
    expect(countLabel(parties)).toBe('1');
    expect(countLabel(arc1)).toBe('2');
    expect(countLabel(npcs)).toBe('3');
    expect(countLabel(tables)).toBeNull();
  });
});
