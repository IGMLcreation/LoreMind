import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PlaythroughFlag } from './campaign.model';

/**
 * Endpoints des flags narratifs d'une Partie (Playthrough).
 * Remplace l'ancien CampaignFlagService.
 */
@Injectable({ providedIn: 'root' })
export class PlaythroughFlagService {

  constructor(private http: HttpClient) {}

  list(playthroughId: string): Observable<PlaythroughFlag[]> {
    return this.http.get<PlaythroughFlag[]>(`/api/playthroughs/${playthroughId}/flags`);
  }

  setFlag(playthroughId: string, name: string, value: boolean): Observable<PlaythroughFlag> {
    return this.http.put<PlaythroughFlag>(
        `/api/playthroughs/${playthroughId}/flags/${encodeURIComponent(name)}`,
        { name, value }
    );
  }

  deleteFlag(playthroughId: string, name: string): Observable<void> {
    return this.http.delete<void>(
        `/api/playthroughs/${playthroughId}/flags/${encodeURIComponent(name)}`
    );
  }
}
