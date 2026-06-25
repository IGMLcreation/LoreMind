import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Enemy, EnemyCreate } from './enemy.model';

/**
 * Service HTTP des fiches d'ennemis (bestiaire de campagne).
 */
@Injectable({ providedIn: 'root' })
export class EnemyService {
  private apiUrl = '/api/enemies';

  constructor(private http: HttpClient) {}

  getByCampaign(campaignId: string): Observable<Enemy[]> {
    return this.http.get<Enemy[]>(`${this.apiUrl}/campaign/${campaignId}`);
  }

  getById(id: string): Observable<Enemy> {
    return this.http.get<Enemy>(`${this.apiUrl}/${id}`);
  }

  create(payload: EnemyCreate): Observable<Enemy> {
    return this.http.post<Enemy>(this.apiUrl, payload);
  }

  update(id: string, payload: Enemy): Observable<Enemy> {
    return this.http.put<Enemy>(`${this.apiUrl}/${id}`, payload);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  /** Recherche par nom — alimente la recherche globale (Ctrl+K). */
  search(q: string): Observable<Enemy[]> {
    return this.http.get<Enemy[]>(`${this.apiUrl}/search`, { params: { q } });
  }

  /**
   * Importe un catalogue de monstres Foundry (exporté par le module) dans le
   * bestiaire de la campagne. Upsert par référence côté backend.
   */
  importFoundryMonsters(campaignId: string, catalog: unknown): Observable<MonsterImportResult> {
    return this.http.post<MonsterImportResult>(
      `/api/campaigns/${campaignId}/import-foundry-monsters`, catalog);
  }
}

export interface MonsterImportResult {
  created: number;
  updated: number;
}
