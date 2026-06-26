// Interfaces TypeScript pour PageDTO (Backend Java).

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
