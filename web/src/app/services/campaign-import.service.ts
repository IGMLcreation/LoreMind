import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  CampaignImportApplyResult,
  CampaignImportProposal,
  CampaignImportStreamEvent
} from './campaign-import.model';

/**
 * Service HTTP pour l'import d'un PDF de campagne.
 *
 * `importStructureStream` utilise fetch() (POST multipart + SSE, impossible
 * avec EventSource) et décode le flux ligne par ligne, comme le chat.
 * `applyStructure` poste l'arbre révisé pour créer les entités.
 */
@Injectable({ providedIn: 'root' })
export class CampaignImportService {
  constructor(private http: HttpClient) {}

  importStructureStream(campaignId: string, file: File): Observable<CampaignImportStreamEvent> {
    return new Observable<CampaignImportStreamEvent>((subscriber) => {
      const controller = new AbortController();
      const form = new FormData();
      form.append('file', file);

      fetch(`/api/campaigns/${campaignId}/import-structure/stream`, {
        method: 'POST',
        headers: { 'Accept': 'text/event-stream' },
        body: form,
        signal: controller.signal
      })
        .then(async (response) => {
          if (!response.ok || !response.body) {
            subscriber.error(new Error(`HTTP ${response.status}`));
            return;
          }
          await this.consumeSse(response.body, subscriber);
        })
        .catch((err) => {
          if (controller.signal.aborted) return;
          subscriber.error(err);
        });

      return () => controller.abort();
    });
  }

  applyStructure(campaignId: string, proposal: CampaignImportProposal): Observable<CampaignImportApplyResult> {
    return this.http.post<CampaignImportApplyResult>(
      `/api/campaigns/${campaignId}/import-structure/apply`, proposal);
  }

  /** Décode un ReadableStream SSE et émet les évènements d'import typés. */
  private async consumeSse(
    body: ReadableStream<Uint8Array>,
    subscriber: { next: (e: CampaignImportStreamEvent) => void; error: (e: unknown) => void; complete: () => void }
  ): Promise<void> {
    const reader = body.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';
    let currentEvent: string | null = null;
    let currentData = '';
    // Le flux s'est-il terminé PROPREMENT (évènement done ou error reçu) ?
    // Sans ce suivi, une connexion coupée en plein import (timeout serveur,
    // proxy, Core redémarré) terminait l'Observable en silence : barre de
    // progression figée et aucun message pour l'utilisateur.
    let terminated = false;

    const dispatch = () => {
      const name = currentEvent ?? 'message';
      if (name === 'error') {
        let message = 'Échec de l\'import.';
        try { message = (JSON.parse(currentData) as { message?: string }).message ?? message; } catch { /* défaut */ }
        terminated = true;
        subscriber.error(new Error(message));
      } else if (name === 'status') {
        // Message d'attente lisible (fournisseur saturé, morceau re-découpé…).
        try {
          const obj = JSON.parse(currentData) as { message?: string };
          if (obj.message) subscriber.next({ type: 'status', message: obj.message });
        } catch { /* bloc malformé ignoré */ }
      } else if (name === 'progress' || name === 'done') {
        try {
          const obj = JSON.parse(currentData);
          if (name === 'done') {
            terminated = true;
            subscriber.next({ type: 'done', arcs: obj.arcs ?? [], npcs: obj.npcs ?? [] });
            subscriber.complete();
          } else {
            subscriber.next({ type: 'progress', ...obj });
          }
        } catch { /* bloc malformé ignoré */ }
      }
      currentEvent = null;
      currentData = '';
    };

    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        let idx: number;
        while ((idx = buffer.indexOf('\n')) >= 0) {
          const line = buffer.slice(0, idx).replace(/\r$/, '');
          buffer = buffer.slice(idx + 1);
          if (line === '') {
            if (currentEvent !== null || currentData !== '') dispatch();
            continue;
          }
          if (line.startsWith('event:')) {
            currentEvent = line.slice(6).trim();
          } else if (line.startsWith('data:')) {
            const chunk = line.slice(5).replace(/^ /, '');
            currentData = currentData ? `${currentData}\n${chunk}` : chunk;
          }
        }
      }
      if (currentEvent !== null || currentData !== '') dispatch();
      if (!terminated) {
        subscriber.error(new Error(
          'L\'import s\'est interrompu avant la fin (connexion coupée ou délai dépassé). Réessayez.'));
      }
    } catch (err) {
      if (!terminated) subscriber.error(err);
    }
  }
}
