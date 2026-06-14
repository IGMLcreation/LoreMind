import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Session } from './session.model';

/**
 * Service HTTP pour le Play Context (gestion des Sessions de jeu).
 * Depuis Playthrough : une Session est rattachée à un Playthrough, pas à une Campagne.
 */
@Injectable({
  providedIn: 'root'
})
export class SessionService {
  private apiUrl = '/api/sessions';

  constructor(private http: HttpClient) {}

  /** Lance une nouvelle session sur la Partie (Playthrough) donnée. */
  startSession(playthroughId: string): Observable<Session> {
    return this.http.post<Session>(this.apiUrl, { playthroughId });
  }

  /**
   * Récupère UNE session active dans l'app (legacy, multi-actives possibles désormais).
   * Préférer {@link getActiveByPlaythrough} quand on veut le statut d'une Partie précise.
   */
  getActiveSession(): Observable<Session | null> {
    return this.http.get<Session | null>(`${this.apiUrl}/active`, { observe: 'body' });
  }

  /** Récupère la session active de la Partie donnée (null si aucune). */
  getActiveByPlaythrough(playthroughId: string): Observable<Session | null> {
    return this.http.get<Session | null>(`${this.apiUrl}/active`, {
      params: new HttpParams().set('playthroughId', playthroughId),
      observe: 'body'
    });
  }

  getSessions(playthroughId?: string): Observable<Session[]> {
    let params = new HttpParams();
    if (playthroughId) {
      params = params.set('playthroughId', playthroughId);
    }
    return this.http.get<Session[]>(this.apiUrl, { params });
  }

  getSessionById(id: string): Observable<Session> {
    return this.http.get<Session>(`${this.apiUrl}/${id}`);
  }

  endSession(id: string): Observable<Session> {
    return this.http.post<Session>(`${this.apiUrl}/${id}/end`, {});
  }

  renameSession(id: string, name: string): Observable<Session> {
    return this.http.patch<Session>(`${this.apiUrl}/${id}`, { name });
  }

  deleteSession(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
