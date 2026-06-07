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

export interface NotebookMessage {
  id?: string;
  notebookId?: string;
  /** "user" | "assistant" */
  role: string;
  content: string;
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
  | { type: 'progress'; current: number; total: number }
  | { type: 'done' }
  | { type: 'error'; message: string };
