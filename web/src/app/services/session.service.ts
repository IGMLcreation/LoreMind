import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Session } from './session.model';

/**
 * Service HTTP pour le Play Context (gestion des Sessions de jeu).
 * Port de sortie vers le Backend Java (Architecture Hexagonale).
 */
@Injectable({
  providedIn: 'root'
})
export class SessionService {
  private apiUrl = '/api/sessions';

  constructor(private http: HttpClient) {}

  /** Lance une nouvelle session sur la campagne donnée. */
  startSession(campaignId: string): Observable<Session> {
    return this.http.post<Session>(this.apiUrl, { campaignId });
  }

  /** Récupère la session active (204 No Content si aucune). */
  getActiveSession(): Observable<Session | null> {
    return this.http.get<Session | null>(`${this.apiUrl}/active`, { observe: 'body' });
  }

  getSessions(campaignId?: string): Observable<Session[]> {
    let params = new HttpParams();
    if (campaignId) {
      params = params.set('campaignId', campaignId);
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
