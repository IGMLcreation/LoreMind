import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SceneDraftProposal } from './scene-draft.model';
import { Scene } from './campaign.model';

/**
 * Service HTTP du Pilier A — capacité « create » : peupler un chapitre en scènes.
 * `generate` propose (non persisté) ; `apply` CRÉE les scènes retenues dans le chapitre.
 */
@Injectable({ providedIn: 'root' })
export class SceneDraftService {

  constructor(private http: HttpClient) {}

  generate(chapterId: string, campaignId: string, instruction: string, count: number): Observable<SceneDraftProposal> {
    return this.http.post<SceneDraftProposal>(
      `/api/chapters/${chapterId}/draft-scenes/generate`,
      { campaignId, instruction: instruction ?? '', count }
    );
  }

  apply(chapterId: string, proposal: SceneDraftProposal): Observable<Scene[]> {
    return this.http.post<Scene[]>(`/api/chapters/${chapterId}/draft-scenes/apply`, proposal);
  }
}
