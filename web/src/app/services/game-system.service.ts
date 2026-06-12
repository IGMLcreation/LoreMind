import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { GameSystem, GameSystemCreate, RulesImportResponse, RulesImportStreamEvent } from './game-system.model';

/**
 * Service HTTP pour les GameSystems (systèmes de JDR).
 */
@Injectable({ providedIn: 'root' })
export class GameSystemService {
  private apiUrl = '/api/game-systems';

  constructor(private http: HttpClient) {}

  getAll(): Observable<GameSystem[]> {
    return this.http.get<GameSystem[]>(this.apiUrl);
  }

  getById(id: string): Observable<GameSystem> {
    return this.http.get<GameSystem>(`${this.apiUrl}/${id}`);
  }

  create(payload: GameSystemCreate): Observable<GameSystem> {
    return this.http.post<GameSystem>(this.apiUrl, payload);
  }

  update(id: string, payload: GameSystemCreate): Observable<GameSystem> {
    return this.http.put<GameSystem>(`${this.apiUrl}/${id}`, payload);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  search(q: string): Observable<GameSystem[]> {
    const params = new HttpParams().set('q', q);
    return this.http.get<GameSystem[]>(`${this.apiUrl}/search`, { params });
  }

  /**
   * Importe un PDF de règles : renvoie une PROPOSITION de sections (titre →
   * markdown). Rien n'est persisté côté serveur — l'appelant injecte les
   * sections dans l'éditeur pour révision avant enregistrement.
   */
  importRules(file: File): Observable<RulesImportResponse> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<RulesImportResponse>(`${this.apiUrl}/import-rules`, form);
  }

  /**
   * Variante streamée : émet l'avancement au fil de l'eau puis `done`.
   * On utilise fetch() (pas EventSource : POST + multipart impossibles avec
   * EventSource) et on décode le flux SSE ligne par ligne. Annuler la
   * subscription annule le fetch (AbortController).
   */
  importRulesStream(file: File): Observable<RulesImportStreamEvent> {
    return new Observable<RulesImportStreamEvent>((subscriber) => {
      const controller = new AbortController();
      const form = new FormData();
      form.append('file', file);

      fetch(`${this.apiUrl}/import-rules/stream`, {
        method: 'POST',
        headers: { 'Accept': 'text/event-stream' },
        body: form,
        signal: controller.signal
      })
        .then(async (response) => {
          if (!response.ok || !response.body) {
            subscriber.error(new Error(`HTTP ${response.status}`));
            return;
          }
          await this.consumeImportSse(response.body, subscriber);
        })
        .catch((err) => {
          if (controller.signal.aborted) return;
          subscriber.error(err);
        });

      return () => controller.abort();
    });
  }

  /** Décode un ReadableStream SSE et émet les évènements d'import typés. */
  private async consumeImportSse(
    body: ReadableStream<Uint8Array>,
    subscriber: { next: (e: RulesImportStreamEvent) => void; error: (e: unknown) => void; complete: () => void }
  ): Promise<void> {
    const reader = body.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';
    let currentEvent: string | null = null;
    let currentData = '';
    // Le flux s'est-il terminé PROPREMENT (évènement done ou error reçu) ?
    // Sans ce suivi, une connexion coupée en plein import (timeout serveur,
    // proxy, Core redémarré) terminait l'Observable en silence : barre de
    // progression figée et aucun message pour l'utilisateur.
    let terminated = false;

    const dispatch = () => {
      const name = currentEvent ?? 'message';
      if (name === 'error') {
        let message = 'Échec de l\'import.';
        try { message = (JSON.parse(currentData) as { message?: string }).message ?? message; } catch { /* garde le défaut */ }
        terminated = true;
        subscriber.error(new Error(message));
      } else if (name === 'status') {
        // Message d'attente lisible (fournisseur saturé, morceau re-découpé…).
        try {
          const obj = JSON.parse(currentData) as { message?: string };
          if (obj.message) subscriber.next({ type: 'status', message: obj.message });
        } catch { /* bloc malformé ignoré */ }
      } else if (name === 'progress' || name === 'done') {
        try {
          const obj = JSON.parse(currentData);
          if (name === 'done') {
            terminated = true;
            subscriber.next({ type: 'done', ...obj });
            subscriber.complete();
          } else {
            subscriber.next({ type: 'progress', ...obj });
          }
        } catch { /* bloc malformé ignoré */ }
      }
      currentEvent = null;
      currentData = '';
    };

    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        let idx: number;
        while ((idx = buffer.indexOf('\n')) >= 0) {
          const line = buffer.slice(0, idx).replace(/\r$/, '');
          buffer = buffer.slice(idx + 1);
          if (line === '') {
            if (currentEvent !== null || currentData !== '') dispatch();
            continue;
          }
          if (line.startsWith('event:')) {
            currentEvent = line.slice(6).trim();
          } else if (line.startsWith('data:')) {
            const chunk = line.slice(5).replace(/^ /, '');
            currentData = currentData ? `${currentData}\n${chunk}` : chunk;
          }
        }
      }
      if (currentEvent !== null || currentData !== '') dispatch();
      if (!terminated) {
        subscriber.error(new Error(
          'L\'import s\'est interrompu avant la fin (connexion coupée ou délai dépassé). Réessayez.'));
      }
    } catch (err) {
      if (!terminated) subscriber.error(err);
    }
  }
}
