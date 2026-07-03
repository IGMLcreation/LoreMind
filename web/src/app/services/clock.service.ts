import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Clock, ClockCreate } from './clock.model';

/**
 * Service HTTP pour les Horloges de progression (Clocks) d'une Partie (Play Context).
 * API imbriquée sous le Playthrough : /api/playthroughs/{playthroughId}/clocks.
 */
@Injectable({ providedIn: 'root' })
export class ClockService {

  constructor(private http: HttpClient) {}

  private base(playthroughId: string): string {
    return `/api/playthroughs/${playthroughId}/clocks`;
  }

  list(playthroughId: string): Observable<Clock[]> {
    return this.http.get<Clock[]>(this.base(playthroughId));
  }

  create(playthroughId: string, payload: ClockCreate): Observable<Clock> {
    return this.http.post<Clock>(this.base(playthroughId), payload);
  }

  update(playthroughId: string, clockId: string, payload: ClockCreate): Observable<Clock> {
    return this.http.put<Clock>(`${this.base(playthroughId)}/${clockId}`, payload);
  }

  advance(playthroughId: string, clockId: string): Observable<Clock> {
    return this.http.put<Clock>(`${this.base(playthroughId)}/${clockId}/advance`, {});
  }

  regress(playthroughId: string, clockId: string): Observable<Clock> {
    return this.http.put<Clock>(`${this.base(playthroughId)}/${clockId}/regress`, {});
  }

  delete(playthroughId: string, clockId: string): Observable<void> {
    return this.http.delete<void>(`${this.base(playthroughId)}/${clockId}`);
  }
}
