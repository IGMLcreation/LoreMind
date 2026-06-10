/** Reflet des entités notebook côté Core (atelier RAG). */

export interface Notebook {
  id: string;
  name: string;
  campaignId: string;
}

export interface NotebookSource {
  id: string;
  notebookId: string;
  filename: string;
  /** INDEXING | READY | FAILED */
  status: string;
  chunkCount: number;
  pageCount: number;
}

/** Passage source utilisé par le RAG pour ancrer une réponse (transparence). */
export interface NotebookChatSource {
  sourceId: string;
  /** Numéro de page 1-based, null si inconnu. */
  page: number | null;
  score: number;
}

export interface NotebookMessage {
  id?: string;
  notebookId?: string;
  /** "user" | "assistant" */
  role: string;
  content: string;
  /** Passages utilisés (réponses streamées uniquement — non persisté). */
  sources?: NotebookChatSource[];
}

export interface NotebookDetail {
  id: string;
  name: string;
  campaignId: string;
  sources: NotebookSource[];
  messages: NotebookMessage[];
}

/** Évènements du chat ancré streamé. */
export type NotebookChatEvent =
  | { type: 'token'; value: string }
  | { type: 'sources'; sources: NotebookChatSource[] }
  | { type: 'progress'; current: number; total: number }
  | { type: 'done' }
  | { type: 'error'; message: string };
