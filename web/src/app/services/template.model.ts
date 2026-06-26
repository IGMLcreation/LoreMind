// Interfaces TypeScript pour TemplateDTO (Backend Java).

/**
 * Type d'un champ de Template. Miroir de com.loremind.domain.shared.template.FieldType.
 * - 'TEXT'           : champ textuel libre (rendu en textarea)
 * - 'IMAGE'          : galerie d'images (rendu en app-image-gallery)
 * - 'NUMBER'         : valeur numerique (rendu en input number)
 * - 'KEY_VALUE_LIST' : liste de paires {label, value} avec labels figes au template
 * - 'TABLE'          : tableau a colonnes figees (labels = noms de colonnes) et
 *                      lignes libres ajoutees au remplissage (boutique, inventaire…)
 */
export type FieldType = 'TEXT' | 'IMAGE' | 'NUMBER' | 'KEY_VALUE_LIST' | 'TABLE';

/**
 * Variante de rendu pour un champ IMAGE. Miroir de
 * com.loremind.domain.lorecontext.ImageLayout. Ignore pour TEXT.
 * - 'GALLERY'  : grille de vignettes (defaut)
 * - 'HERO'     : premiere image en banniere, suivantes en petit
 * - 'MASONRY'  : mosaique hauteurs variables
 * - 'CAROUSEL' : defilement horizontal
 */
export type ImageLayout = 'GALLERY' | 'HERO' | 'MASONRY' | 'CAROUSEL' | 'EDITORIAL' | 'MAPS';

/**
 * Placement d'un bloc dans la grille 12 colonnes du template.
 * Miroir de com.loremind.domain.shared.template.BlockPosition.
 * Tout nullable : absence (ou coordonnees nulles) = auto-flow empile,
 * exactement le rendu historique en une seule colonne.
 */
export interface BlockPosition {
  /** Colonne de depart (0..11). */
  x?: number | null;
  /** Ligne de depart (0..n). */
  y?: number | null;
  /** Largeur en colonnes (1..12). */
  w?: number | null;
  /** Hauteur en lignes (1..n). */
  h?: number | null;
}

/**
 * Champ d'un Template : nom + type discriminant.
 * Miroir de TemplateFieldDTO (backend).
 */
export interface TemplateField {
  /**
   * Identifiant STABLE du bloc : cle d'ancrage des valeurs de Page, survit aux
   * renommages. Retro-rempli avec le nom cote backend pour les templates
   * anterieurs (donc id == name au depart).
   */
  id?: string;
  name: string;
  type: FieldType;
  /** Uniquement pour type='IMAGE'. Absent/null = 'GALLERY'. */
  layout?: ImageLayout | null;
  /**
   * Labels predefinis (ordre significatif) :
   * KEY_VALUE_LIST = libelles des lignes ; TABLE = noms des colonnes.
   */
  labels?: string[] | null;
  /** Chemin Foundry du champ (system.<path>) quand le template est calqué Foundry. */
  foundryPath?: string | null;
  /** Placement dans la grille 12 colonnes. Null = auto-flow empile (rendu historique). */
  pos?: BlockPosition | null;
}

/**
 * Construit un TemplateField propre pour un type donne (attributs par defaut,
 * conservation des attributs compatibles de `previous` lors d'un changement de
 * type). Partage par les editeurs de template du Lore (create/edit).
 */
export function buildLoreTemplateField(
  name: string,
  type: FieldType,
  previous?: TemplateField
): TemplateField {
  switch (type) {
    case 'IMAGE':
      return { name, type, layout: previous?.layout ?? 'GALLERY' };
    case 'KEY_VALUE_LIST':
    case 'TABLE':
      // Les labels (lignes KV / colonnes TABLE) survivent au changement de type
      // entre ces deux variantes.
      return { name, type, labels: previous?.labels ?? [] };
    default:
      return { name, type: 'TEXT' };
  }
}

/** Retire les libelles vides (lignes KV / colonnes TABLE) avant sauvegarde. */
export function cleanFieldLabels(fields: TemplateField[]): TemplateField[] {
  return fields.map(f =>
    f.type === 'KEY_VALUE_LIST' || f.type === 'TABLE'
      ? { ...f, labels: (f.labels ?? []).map(l => l.trim()).filter(l => !!l) }
      : f
  );
}

export interface Template {
  id?: string;
  loreId: string;
  name: string;
  description: string;
  defaultNodeId?: string | null;
  fields: TemplateField[];
  fieldCount?: number;
}

/** Payload de création : id absent, fieldCount absent (calculé côté serveur). */
export interface TemplateCreate {
  loreId: string;
  name: string;
  description: string;
  defaultNodeId?: string | null;
  fields: TemplateField[];
}
