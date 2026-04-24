import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { shareReplay, tap } from 'rxjs/operators';
import { Template, TemplateCreate } from './template.model';

/**
 * Service HTTP pour la gestion des Templates.
 * Port de sortie du Frontend vers le Backend Java (/api/templates).
 *
 * `getByLoreId` est cache via shareReplay(1) — toute mutation
 * (create/update/delete) invalide l'ensemble du cache.
 */
@Injectable({ providedIn: 'root' })
export class TemplateService {
  private apiUrl = '/api/templates';

  private byLoreIdCache = new Map<string, Observable<Template[]>>();

  constructor(private http: HttpClient) {}

  private invalidate(): void {
    this.byLoreIdCache.clear();
  }

  /** Tous les templates d'un Lore (alimente le panneau sidebar). */
  getByLoreId(loreId: string): Observable<Template[]> {
    let obs = this.byLoreIdCache.get(loreId);
    if (!obs) {
      const params = new HttpParams().set('loreId', loreId);
      obs = this.http.get<Template[]>(this.apiUrl, { params }).pipe(
        tap({ error: () => this.byLoreIdCache.delete(loreId) }),
        shareReplay(1)
      );
      this.byLoreIdCache.set(loreId, obs);
    }
    return obs;
  }

  getById(id: string): Observable<Template> {
    return this.http.get<Template>(`${this.apiUrl}/${id}`);
  }

  create(payload: TemplateCreate): Observable<Template> {
    return this.http.post<Template>(this.apiUrl, payload).pipe(tap(() => this.invalidate()));
  }

  update(id: string, template: Template): Observable<Template> {
    return this.http.put<Template>(`${this.apiUrl}/${id}`, template).pipe(tap(() => this.invalidate()));
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`).pipe(tap(() => this.invalidate()));
  }

  search(q: string): Observable<Template[]> {
    const params = new HttpParams().set('q', q);
    return this.http.get<Template[]>(`${this.apiUrl}/search`, { params });
  }
}
