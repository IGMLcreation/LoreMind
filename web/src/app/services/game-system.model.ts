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
  enemyTemplate?: TemplateField[];
  author?: string | null;
  isPublic?: boolean;
}

/**
 * Réponse de l'import d'un PDF de règles : proposition de sections à réviser.
 * `sections` = {titre → contenu markdown}. `ocrPageCount` > 0 ⇒ le PDF était
 * (au moins partiellement) un scan passé à l'OCR.
 */
export interface RulesImportResponse {
  sections: Record<string, string>;
  pageCount: number;
  ocrPageCount: number;
}

/**
 * Évènements du flux SSE d'import streamé.
 * - progress : avancement (total=0 ⇒ phase d'extraction en cours).
 * - status   : message d'attente lisible (fournisseur saturé → retry, morceau
 *              re-découpé, morceau ignoré…) — feedback live pour l'utilisateur.
 * - done     : résultat final (sections proposées).
 * - error    : message d'erreur côté serveur.
 */
export type RulesImportStreamEvent =
  | { type: 'progress'; current: number; total: number; pageCount: number; ocrPageCount: number; newSectionTitles: string[] }
  | { type: 'status'; message: string }
  | { type: 'done'; sections: Record<string, string>; pageCount: number; ocrPageCount: number }
  | { type: 'error'; message: string };

/** Payload de creation/mise a jour (sans id). */
export interface GameSystemCreate {
  name: string;
  description?: string | null;
  rulesMarkdown?: string | null;
  characterTemplate?: TemplateField[];
  npcTemplate?: TemplateField[];
  enemyTemplate?: TemplateField[];
  author?: string | null;
  isPublic: boolean;
}
