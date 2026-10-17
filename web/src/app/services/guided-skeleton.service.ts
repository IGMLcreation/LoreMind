import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { GuidedSkeleton, GuidedSkeletonResult } from './guided-skeleton.model';

/** Service HTTP des méthodes guidées (Mentor d'écriture, phase M3). */
@Injectable({ providedIn: 'root' })
export class GuidedSkeletonService {

  constructor(private http: HttpClient) {}

  create(campaignId: string, skeleton: GuidedSkeleton): Observable<GuidedSkeletonResult> {
    return this.http.post<GuidedSkeletonResult>(`/api/campaigns/${campaignId}/guided-skeleton`, skeleton);
  }
}
