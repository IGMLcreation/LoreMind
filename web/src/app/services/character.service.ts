import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Character, CharacterCreate } from './character.model';

/** Résultat de recherche d'un PJ, enrichi du campaignId pour la navigation. */
export interface CharacterSearchResult {
  id: string;
  name: string;
  playthroughId: string;
  campaignId: string;
}

/**
 * Service HTTP pour les fiches de personnages (PJ) d'une campagne.
 */
@Injectable({ providedIn: 'root' })
export class CharacterService {
  private apiUrl = '/api/characters';

  constructor(private http: HttpClient) {}

  getByPlaythrough(playthroughId: string): Observable<Character[]> {
    return this.http.get<Character[]>(`${this.apiUrl}/playthrough/${playthroughId}`);
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

  /** Recherche par nom — alimente la recherche globale (Ctrl+K). */
  search(q: string): Observable<CharacterSearchResult[]> {
    const params = new HttpParams().set('q', q);
    return this.http.get<CharacterSearchResult[]>(`${this.apiUrl}/search`, { params });
  }
}
