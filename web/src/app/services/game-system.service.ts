import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, Subscriber } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { GameSystem, GameSystemCreate, RulesImportResponse, RulesImportStreamEvent } from './game-system.model';
import { LanguageService } from './language.service';
import { parseSseStream, sseFetch } from '../shared/sse.util';

/**
 * Service HTTP pour les GameSystems (systèmes de JDR).
 */
@Injectable({ providedIn: 'root' })
export class GameSystemService {
  private apiUrl = '/api/game-systems';

  constructor(private http: HttpClient, private translate: TranslateService, private language: LanguageService) {}

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
   * Importe une structure d'acteur Foundry (exportée par le module) : remplace le
   * template ennemi par les champs mappés + pose le type d'acteur. Renvoie le système.
   */
  importFoundryStructure(id: string, structure: unknown): Observable<GameSystem> {
    return this.http.post<GameSystem>(`${this.apiUrl}/${id}/import-foundry-structure`, structure);
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
    const form = new FormData();
    form.append('file', file);
    return sseFetch<RulesImportStreamEvent>(
      `${this.apiUrl}/import-rules/stream`,
      {
        method: 'POST',
        headers: { 'Accept': 'text/event-stream', 'X-User-Language': this.language.current },
        body: form
      },
      (body, subscriber) => this.consumeImportSse(body, subscriber)
    );
  }

  /** Mappe les évènements SSE bruts vers les évènements d'import typés. */
  private async consumeImportSse(
    body: ReadableStream<Uint8Array>,
    subscriber: Subscriber<RulesImportStreamEvent>
  ): Promise<void> {
    // Le flux s'est-il terminé PROPREMENT (évènement done ou error reçu) ?
    // Sans ce suivi, une connexion coupée en plein import (timeout serveur,
    // proxy, Core redémarré) terminait l'Observable en silence : barre de
    // progression figée et aucun message pour l'utilisateur.
    let terminated = false;

    const dispatch = ({ event, data }: { event: string; data: string }) => {
      if (event === 'error') {
        let message = this.translate.instant('services.importFailed');
        try { message = (JSON.parse(data) as { message?: string }).message ?? message; } catch { /* garde le défaut */ }
        terminated = true;
        subscriber.error(new Error(message));
      } else if (event === 'status') {
        // Message d'attente lisible (fournisseur saturé, morceau re-découpé…).
        try {
          const obj = JSON.parse(data) as { message?: string };
          if (obj.message) subscriber.next({ type: 'status', message: obj.message });
        } catch { /* bloc malformé ignoré */ }
      } else if (event === 'progress' || event === 'done') {
        try {
          const obj = JSON.parse(data);
          if (event === 'done') {
            terminated = true;
            subscriber.next({ type: 'done', ...obj });
            subscriber.complete();
          } else {
            subscriber.next({ type: 'progress', ...obj });
          }
        } catch { /* bloc malformé ignoré */ }
      }
    };

    try {
      await parseSseStream(body, dispatch);
      if (!terminated) {
        subscriber.error(new Error(
          this.translate.instant('services.importInterrupted')));
      }
    } catch (err) {
      if (!terminated) subscriber.error(err);
    }
  }
}
