import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { GameSystem, GameSystemCreate } from './game-system.model';

/**
 * Service HTTP pour les GameSystems (systèmes de JDR).
 */
@Injectable({ providedIn: 'root' })
export class GameSystemService {
  private apiUrl = 'http://localhost:8080/api/game-systems';

  constructor(private http: HttpClient) {}

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
}
