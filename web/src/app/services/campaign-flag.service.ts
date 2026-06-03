import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * Liste les noms de faits référencés par les conditions des quêtes d'une Campagne.
 * Modèle "déclaration implicite" : un fait existe dès qu'au moins une quête le
 * référence dans ses prérequis FLAG_SET.
 */
@Injectable({ providedIn: 'root' })
export class CampaignFlagService {

  constructor(private http: HttpClient) {}

  /** Noms de faits dédupliqués et triés. */
  listReferenced(campaignId: string): Observable<string[]> {
    return this.http.get<string[]>(`/api/campaigns/${campaignId}/flags`);
  }
}
