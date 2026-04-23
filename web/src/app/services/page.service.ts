import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { Page, PageCreate } from './page.model';

/**
 * Service HTTP pour la gestion des Pages.
 * Port de sortie du Frontend vers le Backend Java (/api/pages).
 */
@Injectable({ providedIn: 'root' })
export class PageService {
  private apiUrl = '/api/pages';

  constructor(private http: HttpClient) {}

  /** Toutes les pages d'un Lore (flat, pour répartir ensuite par nodeId). */
  getByLoreId(loreId: string): Observable<Page[]> {
    const params = new HttpParams().set('loreId', loreId);
    return this.http.get<Page[]>(this.apiUrl, { params });
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
    return this.http.post<Page>(this.apiUrl, payload);
  }

  update(id: string, page: Page): Observable<Page> {
    return this.http.put<Page>(`${this.apiUrl}/${id}`, page);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
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
