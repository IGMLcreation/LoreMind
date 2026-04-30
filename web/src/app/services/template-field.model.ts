/**
 * Interface TypeScript pour TemplateFieldDTO (kernel partage cote backend).
 * Decrit un champ d'un template (PJ, PNJ, Lore Page).
 *
 * type :  "TEXT" | "IMAGE" | "NUMBER"
 * layout : "GALLERY" | "HERO" | "MASONRY" | "CAROUSEL", null sauf si type=IMAGE
 */
export type FieldType = 'TEXT' | 'IMAGE' | 'NUMBER';
export type ImageLayout = 'GALLERY' | 'HERO' | 'MASONRY' | 'CAROUSEL';

export interface TemplateField {
  name: string;
  type: FieldType;
  layout?: ImageLayout | null;
}
