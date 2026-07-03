import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { EntityFieldPatchProposal } from './entity-assist.model';

/**
 * Service HTTP du Pilier A (co-création), générique par type d'entité narrative
 * ({@code arc|chapter|scene}). One-shot (pas de SSE). `generate` propose (non persisté) ;
 * `apply` patche l'entité persistée (primitif pour les flux hors-éditeur — l'éditeur, lui,
 * patche le formulaire).
 */
@Injectable({ providedIn: 'root' })
export class EntityAssistService {

  constructor(private http: HttpClient) {}

  generateFields(entityType: string, entityId: string, campaignId: string, instruction?: string): Observable<EntityFieldPatchProposal> {
    return this.http.post<EntityFieldPatchProposal>(
      `/api/assist/${entityType}/${entityId}/generate`,
      { campaignId, instruction: instruction ?? '' }
    );
  }

  applyFields(entityType: string, entityId: string, proposal: EntityFieldPatchProposal): Observable<unknown> {
    return this.http.post(`/api/assist/${entityType}/${entityId}/apply`, proposal);
  }
}
