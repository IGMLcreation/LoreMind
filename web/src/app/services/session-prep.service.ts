import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SessionPrepReport } from './session-prep.model';

/**
 * Service HTTP « Préparer la prochaine séance » (Phase 3 co-MJ). Read-model pur :
 * position des joueurs + contenu probable + manques ciblés + horloges en mouvement.
 */
@Injectable({ providedIn: 'root' })
export class SessionPrepService {

  constructor(private http: HttpClient) {}

  getPrep(playthroughId: string): Observable<SessionPrepReport> {
    return this.http.get<SessionPrepReport>(`/api/playthroughs/${playthroughId}/session-prep`);
  }
}
