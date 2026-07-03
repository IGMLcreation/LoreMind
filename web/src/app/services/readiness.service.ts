import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CampaignReadinessAssessment } from './readiness.model';

/**
 * Service HTTP du Pilier B (« guidage / readiness ») : récupère le bilan de
 * préparation d'une campagne. Read-model pur, déterministe, sans effet de bord.
 */
@Injectable({ providedIn: 'root' })
export class ReadinessService {

  constructor(private http: HttpClient) {}

  getReadiness(campaignId: string): Observable<CampaignReadinessAssessment> {
    return this.http.get<CampaignReadinessAssessment>(`/api/campaigns/${campaignId}/readiness`);
  }
}
