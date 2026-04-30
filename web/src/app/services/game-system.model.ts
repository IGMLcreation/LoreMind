import { TemplateField } from './template.model';

/**
 * Interface TypeScript pour GameSystemDTO (jumeau du DTO Java).
 *
 * rulesMarkdown : markdown monolithique, sections decoupees par titres H2.
 * characterTemplate / npcTemplate : champs templates pilotant le rendu des
 * fiches PJ / PNJ d'une campagne adossee a ce systeme (cf. refonte 2026-04-30).
 */
export interface GameSystem {
  id?: string;
  name: string;
  description?: string | null;
  rulesMarkdown?: string | null;
  characterTemplate?: TemplateField[];
  npcTemplate?: TemplateField[];
  author?: string | null;
  isPublic?: boolean;
}

/** Payload de creation/mise a jour (sans id). */
export interface GameSystemCreate {
  name: string;
  description?: string | null;
  rulesMarkdown?: string | null;
  characterTemplate?: TemplateField[];
  npcTemplate?: TemplateField[];
  author?: string | null;
  isPublic: boolean;
}
