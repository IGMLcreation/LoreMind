import { TemplateField } from '../services/template.model';

/**
 * Helpers de mise en page "blocs" des pages de lore (grille 12 colonnes libre,
 * inspiree de l'app "Lore" d'Amsel). Partages par page-view (apercu) et
 * page-edit (saisie) pour rendre une page selon le placement defini sur son
 * template.
 *
 * Modele : grille 2D LIBRE. Chaque bloc occupe un rectangle explicite de la
 * grille via {@link TemplateField.pos} {x, y, w, h} (en unites de grille) :
 * on le place ou on veut (x ET y) et on le dimensionne en largeur ET hauteur.
 * Les lignes ont une hauteur fixe ({@link GRID_ROW_HEIGHT}px), donc `h` a un
 * sens visuel.
 *
 * Retrocompatibilite : un template dont AUCUN bloc ne porte de position est
 * rendu comme avant — une seule colonne, blocs empiles dans l'ordre du tableau.
 * La grille ne s'active que lorsqu'au moins un bloc a ete place via le builder.
 */

/** Nombre de colonnes de la grille. */
export const GRID_COLS = 12;
/** Hauteur d'une unite de ligne (px). Partagee builder + rendu pour coherence. */
export const GRID_ROW_HEIGHT = 32;
/** Hauteur par defaut d'un nouveau bloc, en unites de ligne. */
export const DEFAULT_BLOCK_H = 4;

/**
 * Clé STABLE d'ancrage des valeurs de Page : l'id du bloc, avec repli sur le
 * nom (les templates anterieurs ont id == name, donc transparent). Permet de
 * renommer un bloc sans orpheliner ses valeurs (qui restent rangees sous l'id).
 */
export function blockKey(field: TemplateField): string {
  return field.id && field.id.trim() ? field.id : field.name;
}

/** True si au moins un bloc porte une position de grille exploitable. */
export function hasBlockLayout(fields: TemplateField[] | null | undefined): boolean {
  return (fields ?? []).some(f => {
    const p = f.pos;
    return !!p && (p.x != null || p.y != null || p.w != null || p.h != null);
  });
}

/**
 * Retourne les blocs ordonnes pour le rendu : tries par (ligne, colonne) quand
 * une mise en page est presente, sinon l'ordre d'origine du tableau (rendu
 * historique empile). Ne mute jamais l'entree (copie avant tri), et conserve
 * les memes references d'objets (pour un `track field` stable cote template).
 */
export function orderedBlocks(fields: TemplateField[] | null | undefined): TemplateField[] {
  const list = fields ?? [];
  if (!hasBlockLayout(list)) return list;
  return [...list].sort((a, b) => {
    const ay = a.pos?.y ?? 0;
    const by = b.pos?.y ?? 0;
    if (ay !== by) return ay - by;
    return (a.pos?.x ?? 0) - (b.pos?.x ?? 0);
  });
}

/**
 * Valeur CSS `grid-column` d'un bloc (colonne de depart x+1, sur w colonnes).
 * Null si aucune coordonnee horizontale -> repli empile. A binder via
 * `[style.grid-column]`, inerte hors d'un conteneur grid.
 */
export function blockGridColumn(field: TemplateField): string | null {
  const p = field.pos;
  if (!p || (p.x == null && p.w == null)) return null;
  const x = (p.x ?? 0) + 1;
  const w = p.w ?? GRID_COLS;
  return `${x} / span ${w}`;
}

/**
 * Valeur CSS `grid-row` d'un bloc (ligne de depart y+1, sur h lignes).
 * Null si aucune coordonnee verticale -> repli empile (hauteur auto).
 */
export function blockGridRow(field: TemplateField): string | null {
  const p = field.pos;
  if (!p || (p.y == null && p.h == null)) return null;
  const y = (p.y ?? 0) + 1;
  const h = p.h ?? DEFAULT_BLOCK_H;
  return `${y} / span ${h}`;
}
