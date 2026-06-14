import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ProgressionStatus } from './campaign.model';

/**
 * Endpoints de progression des quêtes pour un Playthrough.
 * Modèle "absence = NOT_STARTED" — envoyer NOT_STARTED supprime la ligne côté backend.
 */
@Injectable({ providedIn: 'root' })
export class QuestProgressionService {

  constructor(private http: HttpClient) {}

  /** Map chapterId -> ProgressionStatus pour le Playthrough donné. */
  list(playthroughId: string): Observable<Record<string, ProgressionStatus>> {
    return this.http.get<Record<string, ProgressionStatus>>(
        `/api/playthroughs/${playthroughId}/quest-progressions`
    );
  }

  setStatus(playthroughId: string, chapterId: string, status: ProgressionStatus): Observable<void> {
    return this.http.put<void>(
        `/api/playthroughs/${playthroughId}/quest-progressions/${chapterId}`,
        { status }
    );
  }
}
