/**
 * Regroupement par dossier + tri par ordre manuel, mutualisé entre toutes les
 * vues ordonnables (listes PNJ/ennemis, grille de la page campagne, sidebar).
 * Une seule définition du tri des dossiers évite que la sidebar et les cartes
 * divergent (cf. revue de propreté).
 */

/** Comparateur d'ordre manuel (`order` croissant, défaut 0). */
export const byOrder = (a: { order?: number }, b: { order?: number }): number =>
  (a.order ?? 0) - (b.order ?? 0);

/** Comparateur de noms de dossier : alphabétique, insensible casse/accents (comme la sidebar). */
export const byFolderName = (a: string, b: string): number =>
  a.localeCompare(b, 'fr', { sensitivity: 'base' });

/** Un dossier (`folder`, `''` = non-classé) et ses éléments, déjà triés par ordre. */
export interface FolderGroup<T> {
  folder: string;
  items: T[];
}

/**
 * Groupe `items` par dossier : dossiers triés alphabétiquement, éléments
 * non-classés (folder vide) regroupés en dernier. Chaque groupe est trié par
 * `order`. Renvoie une structure neutre (`{ folder, items }`) que chaque vue
 * adapte à son besoin (cartes, arbre…).
 */
export function groupByFolder<T extends { order?: number; folder?: string | null }>(items: T[]): FolderGroup<T>[] {
  const sorted = [...items].sort(byOrder);
  const byFolder = new Map<string, T[]>();
  const ungrouped: T[] = [];
  for (const it of sorted) {
    const f = (it.folder ?? '').trim();
    if (f) {
      if (!byFolder.has(f)) byFolder.set(f, []);
      byFolder.get(f)!.push(it);
    } else {
      ungrouped.push(it);
    }
  }
  const groups: FolderGroup<T>[] = [...byFolder.keys()]
    .sort(byFolderName)
    .map(folder => ({ folder, items: byFolder.get(folder)! }));
  if (ungrouped.length) groups.push({ folder: '', items: ungrouped });
  return groups;
}
