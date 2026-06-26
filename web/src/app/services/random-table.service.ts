import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { RandomTable, RandomTableCreate } from './random-table.model';

/**
 * CRUD des tables aléatoires (campagne). Mirroir de NpcService.
 */
@Injectable({ providedIn: 'root' })
export class RandomTableService {
  private apiUrl = '/api/random-tables';

  constructor(private http: HttpClient) {}

  getByCampaign(campaignId: string): Observable<RandomTable[]> {
    return this.http.get<RandomTable[]>(`${this.apiUrl}/campaign/${campaignId}`);
  }

  getById(id: string): Observable<RandomTable> {
    return this.http.get<RandomTable>(`${this.apiUrl}/${id}`);
  }

  create(payload: RandomTableCreate): Observable<RandomTable> {
    return this.http.post<RandomTable>(this.apiUrl, payload);
  }

  update(id: string, payload: RandomTable): Observable<RandomTable> {
    return this.http.put<RandomTable>(`${this.apiUrl}/${id}`, payload);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  /** Réordonne les tables aléatoires d'une campagne : order = position. */
  reorder(orderedIds: string[]): Observable<void> {
    return this.http.put<void>(`${this.apiUrl}/reorder`, { orderedIds });
  }

  /** Recherche par nom — alimente la recherche globale (Ctrl+K). */
  search(q: string): Observable<RandomTable[]> {
    return this.http.get<RandomTable[]>(`${this.apiUrl}/search`, { params: { q } });
  }

  /** Génère une PROPOSITION de table via l'IA (non persistée) à préremplir dans le formulaire. */
  generate(campaignId: string, description: string, diceFormula: string): Observable<RandomTable> {
    return this.http.post<RandomTable>(`${this.apiUrl}/generate`, { campaignId, description, diceFormula });
  }

  /** Improvisation IA d'un court récit sur un résultat tiré (en partie). */
  improvise(
    campaignId: string, tableName: string, resultLabel: string, resultDetail: string
  ): Observable<{ narration: string }> {
    return this.http.post<{ narration: string }>(
      `${this.apiUrl}/improvise`,
      { campaignId, tableName, resultLabel, resultDetail }
    );
  }
}
