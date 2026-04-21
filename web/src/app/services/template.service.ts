import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Template, TemplateCreate } from './template.model';

/**
 * Service HTTP pour la gestion des Templates.
 * Port de sortie du Frontend vers le Backend Java (/api/templates).
 */
@Injectable({ providedIn: 'root' })
export class TemplateService {
  private apiUrl = 'http://localhost:8080/api/templates';

  constructor(private http: HttpClient) {}

  /** Tous les templates d'un Lore (alimente le panneau sidebar). */
  getByLoreId(loreId: string): Observable<Template[]> {
    const params = new HttpParams().set('loreId', loreId);
    return this.http.get<Template[]>(this.apiUrl, { params });
  }

  getById(id: string): Observable<Template> {
    return this.http.get<Template>(`${this.apiUrl}/${id}`);
  }

  create(payload: TemplateCreate): Observable<Template> {
    return this.http.post<Template>(this.apiUrl, payload);
  }

  update(id: string, template: Template): Observable<Template> {
    return this.http.put<Template>(`${this.apiUrl}/${id}`, template);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  search(q: string): Observable<Template[]> {
    const params = new HttpParams().set('q', q);
    return this.http.get<Template[]>(`${this.apiUrl}/search`, { params });
  }
}
