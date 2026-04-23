import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Character, CharacterCreate } from './character.model';

/**
 * Service HTTP pour les fiches de personnages (PJ) d'une campagne.
 */
@Injectable({ providedIn: 'root' })
export class CharacterService {
  private apiUrl = '/api/characters';

  constructor(private http: HttpClient) {}

  getByCampaign(campaignId: string): Observable<Character[]> {
    return this.http.get<Character[]>(`${this.apiUrl}/campaign/${campaignId}`);
  }

  getById(id: string): Observable<Character> {
    return this.http.get<Character>(`${this.apiUrl}/${id}`);
  }

  create(payload: CharacterCreate): Observable<Character> {
    return this.http.post<Character>(this.apiUrl, payload);
  }

  update(id: string, payload: Character): Observable<Character> {
    return this.http.put<Character>(`${this.apiUrl}/${id}`, payload);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
