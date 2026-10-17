import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, Subject, tap } from 'rxjs';
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

  /**
   * Émet après chaque mutation qui change l'ensemble des séances en cours
   * (démarrage, fin, renommage, suppression) — écouté par {@link ActiveSessionsService}
   * pour rafraîchir le raccourci « Séance en cours » sans attendre une navigation.
   */
  readonly changed$ = new Subject<void>();

  constructor(private http: HttpClient) {}

  /** Lance une nouvelle session sur la Partie (Playthrough) donnée. */
  startSession(playthroughId: string): Observable<Session> {
    return this.http.post<Session>(this.apiUrl, { playthroughId }).pipe(tap(() => this.changed$.next()));
  }

  /** Toutes les séances en cours (toutes Parties confondues), la plus récente d'abord. */
  getActiveSessions(): Observable<Session[]> {
    return this.http.get<Session[]>(this.apiUrl, { params: new HttpParams().set('active', 'true') });
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
    return this.http.post<Session>(`${this.apiUrl}/${id}/end`, {}).pipe(tap(() => this.changed$.next()));
  }

  renameSession(id: string, name: string): Observable<Session> {
    return this.http.patch<Session>(`${this.apiUrl}/${id}`, { name }).pipe(tap(() => this.changed$.next()));
  }

  deleteSession(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`).pipe(tap(() => this.changed$.next()));
  }

  /** Épingle (sceneId) ou dés-épingle (null) la scène courante — mode cockpit. */
  setCurrentScene(id: string, sceneId: string | null): Observable<Session> {
    return this.http.put<Session>(`${this.apiUrl}/${id}/current-scene`, { sceneId });
  }

  /** Récap « précédemment… » : résume le journal de la séance précédente de la Partie. */
  recap(id: string): Observable<{ previousSessionName: string; recap: string }> {
    return this.http.post<{ previousSessionName: string; recap: string }>(`${this.apiUrl}/${id}/recap`, {});
  }
}
