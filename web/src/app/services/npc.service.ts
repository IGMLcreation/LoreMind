import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Npc, NpcCreate } from './npc.model';

/**
 * Service HTTP pour les fiches de PNJ d'une campagne.
 */
@Injectable({ providedIn: 'root' })
export class NpcService {
  private apiUrl = '/api/npcs';

  constructor(private http: HttpClient) {}

  getByCampaign(campaignId: string): Observable<Npc[]> {
    return this.http.get<Npc[]>(`${this.apiUrl}/campaign/${campaignId}`);
  }

  /** PNJ de toutes les campagnes liées à un Lore — alimente le graphe du Lore. */
  getByLore(loreId: string): Observable<Npc[]> {
    return this.http.get<Npc[]>(`${this.apiUrl}/lore/${loreId}`);
  }

  getById(id: string): Observable<Npc> {
    return this.http.get<Npc>(`${this.apiUrl}/${id}`);
  }

  create(payload: NpcCreate): Observable<Npc> {
    return this.http.post<Npc>(this.apiUrl, payload);
  }

  update(id: string, payload: Npc): Observable<Npc> {
    return this.http.put<Npc>(`${this.apiUrl}/${id}`, payload);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  /** Recherche par nom — alimente la recherche globale (Ctrl+K). */
  search(q: string): Observable<Npc[]> {
    return this.http.get<Npc[]>(`${this.apiUrl}/search`, { params: { q } });
  }
}
