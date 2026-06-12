import { Injectable, NgZone } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Notebook, NotebookArchive, NotebookDetail, NotebookSource, NotebookChatEvent } from './notebook.model';

/**
 * Service des notebooks (atelier RAG) : CRUD, upload/indexation de sources,
 * et chat ancré STREAMÉ (SSE via fetch, comme ai-chat.service).
 */
@Injectable({ providedIn: 'root' })
export class NotebookService {
  private readonly apiUrl = '/api/notebooks';

  constructor(private http: HttpClient, private zone: NgZone) {}

  listByCampaign(campaignId: string): Observable<Notebook[]> {
    return this.http.get<Notebook[]>(`${this.apiUrl}/campaign/${campaignId}`);
  }

  get(id: string): Observable<NotebookDetail> {
    return this.http.get<NotebookDetail>(`${this.apiUrl}/${id}`);
  }

  create(campaignId: string, name: string): Observable<Notebook> {
    return this.http.post<Notebook>(this.apiUrl, { campaignId, name });
  }

  rename(id: string, name: string): Observable<Notebook> {
    return this.http.put<Notebook>(`${this.apiUrl}/${id}`, { name });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  /** Upload + indexation d'une source (multipart). Bloquant côté serveur (peut être long). */
  addSource(notebookId: string, file: File): Observable<NotebookSource> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http.post<NotebookSource>(`${this.apiUrl}/${notebookId}/sources`, form);
  }

  deleteSource(sourceId: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/sources/${sourceId}`);
  }

  /** « Vider la conversation » : archive le fil actif (rien n'est supprimé). */
  clearChat(notebookId: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${notebookId}/chat/clear`, {});
  }

  /** Conversations archivées, plus récentes d'abord. */
  getArchives(notebookId: string): Observable<NotebookArchive[]> {
    return this.http.get<NotebookArchive[]>(`${this.apiUrl}/${notebookId}/chat/archives`);
  }

  /**
   * Chat ancré streamé. fetch() + ReadableStream (HttpClient bufferise les SSE).
   * Émissions forcées dans la zone Angular pour la détection de changement.
   *
   * `sourceIds`  : sous-ensemble de sources à utiliser pour ce tour (cases cochées) ;
   *                undefined = toutes les sources prêtes du notebook.
   * `archiveIds` : archives de conversation cochées comme référence (clés archivedAt) ;
   *                leur contenu est injecté dans le contexte du prompt.
   */
  streamChat(notebookId: string, message: string, deep = false, sourceIds?: string[], archiveIds?: string[]): Observable<NotebookChatEvent> {
    return new Observable<NotebookChatEvent>((subscriber) => {
      const controller = new AbortController();
      const emit = (ev: NotebookChatEvent) => this.zone.run(() => subscriber.next(ev));

      (async () => {
        try {
          const response = await fetch(`${this.apiUrl}/${notebookId}/chat/stream`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Accept': 'text/event-stream' },
            credentials: 'include',
            body: JSON.stringify({
              message, deep,
              sourceIds: sourceIds ?? null,
              archiveIds: archiveIds?.length ? archiveIds : null
            }),
            signal: controller.signal,
          });
          if (!response.ok || !response.body) {
            emit({ type: 'error', message: `HTTP ${response.status}` });
            this.zone.run(() => subscriber.complete());
            return;
          }
          const reader = response.body.getReader();
          const decoder = new TextDecoder('utf-8');
          let buffer = '';
          let currentEvent: string | null = null;
          let currentData = '';

          const dispatch = () => {
            const name = currentEvent ?? 'message';
            if (name === 'token') {
              try { emit({ type: 'token', value: (JSON.parse(currentData) as { token?: string }).token ?? '' }); }
              catch { /* ignore */ }
            } else if (name === 'sources') {
              try {
                const o = JSON.parse(currentData) as { sources?: { source_id?: string; page?: number | null; score?: number }[] };
                emit({
                  type: 'sources',
                  sources: (o.sources ?? []).map(s => ({
                    sourceId: s.source_id ?? '', page: s.page ?? null, score: s.score ?? 0
                  }))
                });
              } catch { /* ignore */ }
            } else if (name === 'progress') {
              try {
                const o = JSON.parse(currentData) as { current?: number; total?: number };
                emit({ type: 'progress', current: o.current ?? 0, total: o.total ?? 0 });
              } catch { /* ignore */ }
            } else if (name === 'done') {
              emit({ type: 'done' });
            } else if (name === 'error') {
              let msg = 'Erreur du chat.';
              try { msg = (JSON.parse(currentData) as { message?: string }).message ?? msg; } catch { /* garde */ }
              emit({ type: 'error', message: msg });
            }
            currentEvent = null;
            currentData = '';
          };

          while (true) {
            const { value, done } = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, { stream: true });
            let idx: number;
            while ((idx = buffer.indexOf('\n')) >= 0) {
              const line = buffer.slice(0, idx).replace(/\r$/, '');
              buffer = buffer.slice(idx + 1);
              if (line === '') { if (currentEvent !== null || currentData !== '') dispatch(); continue; }
              if (line.startsWith('event:')) currentEvent = line.slice(6).trim();
              else if (line.startsWith('data:')) {
                const chunk = line.slice(5).replace(/^ /, '');
                currentData = currentData ? `${currentData}\n${chunk}` : chunk;
              }
            }
          }
          if (currentEvent !== null || currentData !== '') dispatch();
          this.zone.run(() => subscriber.complete());
        } catch (err) {
          if ((err as Error).name !== 'AbortError') {
            emit({ type: 'error', message: (err as Error).message || 'Erreur réseau' });
          }
          this.zone.run(() => subscriber.complete());
        }
      })();

      return () => controller.abort();
    });
  }
}
