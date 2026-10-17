import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { WritingReviewReport } from './writing-review.model';

/**
 * Service HTTP de la relecture d'éditeur (Mentor d'écriture, phase M2).
 * Read-model pur, déterministe, sans effet de bord.
 */
@Injectable({ providedIn: 'root' })
export class WritingReviewService {

  constructor(private http: HttpClient) {}

  getReview(campaignId: string): Observable<WritingReviewReport> {
    return this.http.get<WritingReviewReport>(`/api/campaigns/${campaignId}/writing-review`);
  }
}
