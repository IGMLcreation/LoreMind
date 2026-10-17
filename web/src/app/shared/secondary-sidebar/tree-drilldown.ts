import { TreeItem } from '../../services/layout.service';

/**
 * Navigation PAR NIVEAUX dans l'arborescence (mode tiroir : téléphone, tablette).
 * Au lieu d'un arbre indenté à minuscules chevrons, on affiche un niveau à la fois :
 * toucher un nœud qui a des enfants « entre » dedans, un bouton retour remonte.
 * Logique pure, sans Angular (testée en vitest).
 */

/** Chemin racine → nœud (inclus), ou null si l'id n'existe pas. */
export function findPath(items: TreeItem[], id: string): TreeItem[] | null {
  for (const item of items) {
    if (item.id === id) return [item];
    const sub = findPath(item.children ?? [], id);
    if (sub) return [item, ...sub];
  }
  return null;
}

/**
 * Ré-résout une pile d'ids après une reconstruction de l'arbre (les objets changent,
 * les ids restent) : on suit les ids niveau par niveau et on s'arrête au premier
 * qui a disparu (nœud supprimé, ou plus d'enfants).
 */
export function resolveStack(items: TreeItem[], ids: string[]): TreeItem[] {
  const stack: TreeItem[] = [];
  let level = items;
  for (const id of ids) {
    const node = level.find(i => i.id === id);
    if (!node || !hasChildren(node)) break;
    stack.push(node);
    level = node.children ?? [];
  }
  return stack;
}

/**
 * Niveau à afficher pour la page courante : les ancêtres du nœud actif le plus
 * profond, plus le nœud lui-même s'il a des enfants (sur la page d'un arc, on voit
 * ses chapitres). Aucun nœud actif → racine.
 */
export function stackForActive(items: TreeItem[], isActive: (item: TreeItem) => boolean): string[] {
  const path = deepestActivePath(items, isActive);
  if (!path) return [];
  const active = path[path.length - 1];
  const containers = hasChildren(active) ? path : path.slice(0, -1);
  return containers.map(n => n.id);
}

function deepestActivePath(items: TreeItem[], isActive: (item: TreeItem) => boolean): TreeItem[] | null {
  for (const item of items) {
    const sub = deepestActivePath(item.children ?? [], isActive);
    if (sub) return [item, ...sub];
    if (isActive(item)) return [item];
  }
  return null;
}

export function hasChildren(item: TreeItem): boolean {
  return !!item.children && item.children.length > 0;
}

/** Compteur affiché à droite d'une ligne : le badge fourni, sinon le nombre d'enfants réels. */
export function countLabel(item: TreeItem): string | null {
  if (item.meta) return item.meta;
  const n = (item.children ?? []).filter(c => !c.isAction).length;
  return n > 0 ? String(n) : null;
}
