// Interfaces TypeScript pour PageDTO (Backend Java).

export interface Page {
  id?: string;
  loreId: string;
  nodeId: string;
  templateId?: string | null;
  title: string;
  values?: Record<string, string>;
  /**
   * Pour chaque champ IMAGE du template, la liste ordonnee des IDs d'images
   * uploadees (Shared Kernel images). Structure separee de `values`.
   */
  imageValues?: Record<string, string[]>;
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
