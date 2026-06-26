// Interfaces TypeScript pour PageDTO (Backend Java).

/**
 * Cadrage (pan/zoom) d'une image dans son bloc IMAGE.
 * Miroir de com.loremind.domain.lorecontext.ImageFraming.
 * - x, y : object-position en pourcentage (0..100), 50/50 = centré.
 * - scale : facteur de zoom (>= 1), 1 = plein cadre (cover).
 */
export interface ImageFraming {
  x: number;
  y: number;
  scale: number;
}

export interface Page {
  id?: string;
  loreId: string;
  nodeId: string;
  templateId?: string | null;
  title: string;
  /** Position de la page dans son dossier (glisser-déposer). */
  order?: number;
  values?: Record<string, string>;
  /**
   * Pour chaque champ IMAGE du template, la liste ordonnee des IDs d'images
   * uploadees (Shared Kernel images). Structure separee de `values`.
   */
  imageValues?: Record<string, string[]>;
  /**
   * Cadrage (pan/zoom) des images : fieldKey → imageId → {x, y, scale}.
   * Purement présentationnel ; absence = cadrage par défaut (centré, plein cadre).
   */
  imageFraming?: Record<string, Record<string, ImageFraming>>;
  /**
   * Pour chaque champ KEY_VALUE_LIST (tableau libelle → valeur, comme sur les
   * fiches de personnage) : fieldName → (label → valeur).
   */
  keyValueValues?: Record<string, Record<string, string>>;
  /**
   * Pour chaque champ TABLE (colonnes figees au template, lignes libres) :
   * fieldName → lignes ordonnees, chaque ligne = colonne → cellule.
   */
  tableValues?: Record<string, Array<Record<string, string>>>;
  notes?: string | null;
  tags?: string[];
  relatedPageIds?: string[];
}

/** Payload de création : seuls les champs structurels sont envoyés. */
export interface PageCreate {
  loreId: string;
  nodeId: string;
  templateId: string;
  title: string;
}
