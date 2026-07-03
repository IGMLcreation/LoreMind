import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Front, FrontCreate } from './front.model';

/**
 * Service HTTP pour les Fronts (menaces regroupant des horloges) d'une Partie.
 * API imbriquée sous le Playthrough : /api/playthroughs/{playthroughId}/fronts.
 */
@Injectable({ providedIn: 'root' })
export class FrontService {

  constructor(private http: HttpClient) {}

  private base(playthroughId: string): string {
    return `/api/playthroughs/${playthroughId}/fronts`;
  }

  list(playthroughId: string): Observable<Front[]> {
    return this.http.get<Front[]>(this.base(playthroughId));
  }

  create(playthroughId: string, payload: FrontCreate): Observable<Front> {
    return this.http.post<Front>(this.base(playthroughId), payload);
  }

  update(playthroughId: string, frontId: string, payload: FrontCreate): Observable<Front> {
    return this.http.put<Front>(`${this.base(playthroughId)}/${frontId}`, payload);
  }

  delete(playthroughId: string, frontId: string): Observable<void> {
    return this.http.delete<void>(`${this.base(playthroughId)}/${frontId}`);
  }
}
