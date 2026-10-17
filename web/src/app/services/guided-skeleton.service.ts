import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { GuidedSkeleton, GuidedSkeletonResult } from './guided-skeleton.model';
import { GuidedSuggestRequest } from '../campaigns/guided/guided-assist';

/** Service HTTP des méthodes guidées (Mentor d'écriture, phase M3). */
@Injectable({ providedIn: 'root' })
export class GuidedSkeletonService {

  constructor(private http: HttpClient) {}

  create(campaignId: string, skeleton: GuidedSkeleton): Observable<GuidedSkeletonResult> {
    return this.http.post<GuidedSkeletonResult>(`/api/campaigns/${campaignId}/guided-skeleton`, skeleton);
  }

  /** IA : réponses proposées aux questions d'une méthode, à partir de l'idée du MJ (rien n'est enregistré). */
  suggest(campaignId: string, request: GuidedSuggestRequest): Observable<{ answers: Record<string, string> }> {
    return this.http.post<{ answers: Record<string, string> }>(`/api/campaigns/${campaignId}/guided-assist`, request);
  }
}
