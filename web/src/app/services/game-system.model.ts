/**
 * Interface TypeScript pour GameSystemDTO (jumeau du DTO Java).
 *
 * rulesMarkdown : markdown monolithique, sections découpées par titres H2
 * (## Combat, ## Classes, etc.). Le découpage et la sélection des sections
 * à injecter dans le prompt IA sont faits côté backend Java.
 */
export interface GameSystem {
  id?: string;
  name: string;
  description?: string | null;
  rulesMarkdown?: string | null;
  author?: string | null;
  isPublic?: boolean;
}

/** Payload de création/mise à jour (sans id). */
export interface GameSystemCreate {
  name: string;
  description?: string | null;
  rulesMarkdown?: string | null;
  author?: string | null;
  isPublic: boolean;
}
