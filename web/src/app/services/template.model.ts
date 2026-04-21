// Interfaces TypeScript pour TemplateDTO (Backend Java).

/**
 * Type d'un champ de Template. Miroir de com.loremind.domain.lorecontext.FieldType.
 * - 'TEXT'  : champ textuel libre (rendu en textarea)
 * - 'IMAGE' : galerie d'images (rendu en app-image-gallery)
 */
export type FieldType = 'TEXT' | 'IMAGE';

/**
 * Champ d'un Template : nom + type discriminant.
 * Miroir de TemplateFieldDTO (backend).
 */
export interface TemplateField {
  name: string;
  type: FieldType;
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
