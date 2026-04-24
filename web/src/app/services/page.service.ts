import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map, shareReplay, tap } from 'rxjs/operators';
import { Page, PageCreate } from './page.model';

/**
 * Service HTTP pour la gestion des Pages.
 * Port de sortie du Frontend vers le Backend Java (/api/pages).
 *
 * `getByLoreId` est cache via shareReplay(1) — toute mutation
 * (create/update/delete) invalide l'ensemble du cache.
 */
@Injectable({ providedIn: 'root' })
export class PageService {
  private apiUrl = '/api/pages';

  private byLoreIdCache = new Map<string, Observable<Page[]>>();

  constructor(private http: HttpClient) {}

  private invalidate(): void {
    this.byLoreIdCache.clear();
  }

  /** Toutes les pages d'un Lore (flat, pour répartir ensuite par nodeId). */
  getByLoreId(loreId: string): Observable<Page[]> {
    let obs = this.byLoreIdCache.get(loreId);
    if (!obs) {
      const params = new HttpParams().set('loreId', loreId);
      obs = this.http.get<Page[]>(this.apiUrl, { params }).pipe(
        tap({ error: () => this.byLoreIdCache.delete(loreId) }),
        shareReplay(1)
      );
      this.byLoreIdCache.set(loreId, obs);
    }
    return obs;
  }

  /** Toutes les pages d'un noeud donné. */
  getByNodeId(nodeId: string): Observable<Page[]> {
    const params = new HttpParams().set('nodeId', nodeId);
    return this.http.get<Page[]>(this.apiUrl, { params });
  }

  getById(id: string): Observable<Page> {
    return this.http.get<Page>(`${this.apiUrl}/${id}`);
  }

  create(payload: PageCreate): Observable<Page> {
    return this.http.post<Page>(this.apiUrl, payload).pipe(tap(() => this.invalidate()));
  }

  update(id: string, page: Page): Observable<Page> {
    return this.http.put<Page>(`${this.apiUrl}/${id}`, page).pipe(tap(() => this.invalidate()));
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`).pipe(tap(() => this.invalidate()));
  }

  search(q: string): Observable<Page[]> {
    const params = new HttpParams().set('q', q);
    return this.http.get<Page[]>(`${this.apiUrl}/search`, { params });
  }

  /**
   * Demande à l'IA (Brain Python, via le Core Java) des suggestions de valeurs
   * pour les champs dynamiques de la page. Ne modifie PAS la page en base —
   * l'appelant est responsable de fusionner les valeurs et de sauvegarder.
   *
   * Peut prendre plusieurs dizaines de secondes selon le modèle LLM.
   */
  generateValues(pageId: string): Observable<Record<string, string>> {
    return this.http
      .post<{ values: Record<string, string> }>(`${this.apiUrl}/${pageId}/generate`, {})
      .pipe(map(res => res.values ?? {}));
  }
}
