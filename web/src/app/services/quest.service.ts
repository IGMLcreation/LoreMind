import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Quest, QuestCreate } from './campaign.model';

/** Impact d'une suppression : scènes du conteneur (quête libre) qui partiront avec. */
export interface QuestDeletionImpact {
  scenes: number;
}

/**
 * Service HTTP pour les Quêtes (Niveau 1). API nestée sous la campagne :
 * /api/campaigns/{campaignId}/quests. Si {@code playthroughId} est fourni, les
 * quêtes renvoyées sont enrichies de leur progressionStatus / effectiveStatus.
 */
@Injectable({ providedIn: 'root' })
export class QuestService {

  constructor(private http: HttpClient) {}

  private base(campaignId: string): string {
    return `/api/campaigns/${campaignId}/quests`;
  }

  getByCampaign(campaignId: string, playthroughId?: string): Observable<Quest[]> {
    const options = playthroughId ? { params: { playthroughId } } : {};
    return this.http.get<Quest[]>(this.base(campaignId), options);
  }

  getById(campaignId: string, questId: string, playthroughId?: string): Observable<Quest> {
    const options = playthroughId ? { params: { playthroughId } } : {};
    return this.http.get<Quest>(`${this.base(campaignId)}/${questId}`, options);
  }

  create(campaignId: string, payload: QuestCreate): Observable<Quest> {
    return this.http.post<Quest>(this.base(campaignId), payload);
  }

  update(campaignId: string, questId: string, payload: Quest): Observable<Quest> {
    return this.http.put<Quest>(`${this.base(campaignId)}/${questId}`, payload);
  }

  delete(campaignId: string, questId: string): Observable<void> {
    return this.http.delete<void>(`${this.base(campaignId)}/${questId}`);
  }

  deletionImpact(campaignId: string, questId: string): Observable<QuestDeletionImpact> {
    return this.http.get<QuestDeletionImpact>(`${this.base(campaignId)}/${questId}/deletion-impact`);
  }

  reorder(campaignId: string, orderedIds: string[]): Observable<void> {
    return this.http.put<void>(`${this.base(campaignId)}/reorder`, { orderedIds });
  }

  /**
   * Progression d'une quête DANS UNE PARTIE (Play Context) : NOT_STARTED efface la
   * ligne (modèle « absence = non commencée »), IN_PROGRESS / COMPLETED la posent.
   * C'est ce qui pilote le statut effectif (Disponible / En cours / Terminée).
   */
  setProgression(playthroughId: string, questId: string,
                 status: 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED'): Observable<void> {
    return this.http.put<void>(`/api/playthroughs/${playthroughId}/quest-progressions/${questId}`, { status });
  }
}
