import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Playthrough, PlaythroughCreate } from './campaign.model';

export interface PlaythroughDeletionImpact {
  sessions: number;
  characters: number;
  flags: number;
  progressions: number;
}

@Injectable({ providedIn: 'root' })
export class PlaythroughService {

  private apiUrl = '/api/playthroughs';

  constructor(private http: HttpClient) {}

  listByCampaign(campaignId: string): Observable<Playthrough[]> {
    const params = new HttpParams().set('campaignId', campaignId);
    return this.http.get<Playthrough[]>(this.apiUrl, { params });
  }

  getById(id: string): Observable<Playthrough> {
    return this.http.get<Playthrough>(`${this.apiUrl}/${id}`);
  }

  create(payload: PlaythroughCreate): Observable<Playthrough> {
    return this.http.post<Playthrough>(this.apiUrl, payload);
  }

  update(id: string, payload: Playthrough): Observable<Playthrough> {
    return this.http.put<Playthrough>(`${this.apiUrl}/${id}`, payload);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  deletionImpact(id: string): Observable<PlaythroughDeletionImpact> {
    return this.http.get<PlaythroughDeletionImpact>(`${this.apiUrl}/${id}/deletion-impact`);
  }
}
