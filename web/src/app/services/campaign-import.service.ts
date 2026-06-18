import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, Subscriber } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import {
  CampaignImportApplyResult,
  CampaignImportProposal,
  CampaignImportStreamEvent
} from './campaign-import.model';
import { LanguageService } from './language.service';
import { parseSseStream, sseFetch } from '../shared/sse.util';

/**
 * Service HTTP pour l'import d'un PDF de campagne.
 *
 * `importStructureStream` utilise fetch() (POST multipart + SSE, impossible
 * avec EventSource) et décode le flux ligne par ligne, comme le chat.
 * `applyStructure` poste l'arbre révisé pour créer les entités.
 */
@Injectable({ providedIn: 'root' })
export class CampaignImportService {
  constructor(private http: HttpClient, private translate: TranslateService, private language: LanguageService) {}

  importStructureStream(campaignId: string, file: File): Observable<CampaignImportStreamEvent> {
    const form = new FormData();
    form.append('file', file);
    return sseFetch<CampaignImportStreamEvent>(
      `/api/campaigns/${campaignId}/import-structure/stream`,
      {
        method: 'POST',
        headers: { 'Accept': 'text/event-stream', 'X-User-Language': this.language.current },
        body: form
      },
      (body, subscriber) => this.consumeSse(body, subscriber)
    );
  }

  applyStructure(campaignId: string, proposal: CampaignImportProposal): Observable<CampaignImportApplyResult> {
    return this.http.post<CampaignImportApplyResult>(
      `/api/campaigns/${campaignId}/import-structure/apply`, proposal);
  }

  /** Mappe les évènements SSE bruts vers les évènements d'import typés. */
  private async consumeSse(
    body: ReadableStream<Uint8Array>,
    subscriber: Subscriber<CampaignImportStreamEvent>
  ): Promise<void> {
    // Le flux s'est-il terminé PROPREMENT (évènement done ou error reçu) ?
    // Sans ce suivi, une connexion coupée en plein import (timeout serveur,
    // proxy, Core redémarré) terminait l'Observable en silence : barre de
    // progression figée et aucun message pour l'utilisateur.
    let terminated = false;

    const dispatch = ({ event, data }: { event: string; data: string }) => {
      if (event === 'error') {
        let message = this.translate.instant('services.importFailed');
        try { message = (JSON.parse(data) as { message?: string }).message ?? message; } catch { /* défaut */ }
        terminated = true;
        subscriber.error(new Error(message));
      } else if (event === 'status') {
        // Message d'attente lisible (fournisseur saturé, morceau re-découpé…).
        try {
          const obj = JSON.parse(data) as { message?: string };
          if (obj.message) subscriber.next({ type: 'status', message: obj.message });
        } catch { /* bloc malformé ignoré */ }
      } else if (event === 'progress' || event === 'done') {
        try {
          const obj = JSON.parse(data);
          if (event === 'done') {
            terminated = true;
            subscriber.next({ type: 'done', arcs: obj.arcs ?? [], npcs: obj.npcs ?? [] });
            subscriber.complete();
          } else {
            subscriber.next({ type: 'progress', ...obj });
          }
        } catch { /* bloc malformé ignoré */ }
      }
    };

    try {
      await parseSseStream(body, dispatch);
      if (!terminated) {
        subscriber.error(new Error(
          this.translate.instant('services.importInterrupted')));
      }
    } catch (err) {
      if (!terminated) subscriber.error(err);
    }
  }
}
