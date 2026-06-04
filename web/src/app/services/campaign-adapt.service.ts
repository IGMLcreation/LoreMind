import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

/** Évènements du flux SSE de conseils d'adaptation. */
export type AdaptStreamEvent =
  | { type: 'token'; value: string }
  | { type: 'done' }
  | { type: 'error'; message: string };

/** Message de la conversation d'adaptation. */
export interface AdaptMessage {
  role: 'user' | 'assistant';
  content: string;
}

/**
 * Service : adaptation conversationnelle d'un PDF à une campagne (streamée).
 * fetch() + SSE (POST multipart impossible avec EventSource), décodage ligne à ligne.
 */
@Injectable({ providedIn: 'root' })
export class CampaignAdaptService {

  adviseStream(campaignId: string, file: File, messages: AdaptMessage[]): Observable<AdaptStreamEvent> {
    return new Observable<AdaptStreamEvent>((subscriber) => {
      const controller = new AbortController();
      const form = new FormData();
      form.append('file', file);
      form.append('messages', JSON.stringify(messages ?? []));

      fetch(`/api/campaigns/${campaignId}/adapt-pdf/stream`, {
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
          await this.consume(response.body, subscriber);
        })
        .catch((err) => {
          if (controller.signal.aborted) return;
          subscriber.error(err);
        });

      return () => controller.abort();
    });
  }

  private async consume(
    body: ReadableStream<Uint8Array>,
    subscriber: { next: (e: AdaptStreamEvent) => void; error: (e: unknown) => void; complete: () => void }
  ): Promise<void> {
    const reader = body.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';
    let currentEvent: string | null = null;
    let currentData = '';

    const dispatch = () => {
      const name = currentEvent ?? 'message';
      if (name === 'error') {
        let message = "Échec de l'adaptation.";
        try { message = (JSON.parse(currentData) as { message?: string }).message ?? message; } catch { /* défaut */ }
        subscriber.error(new Error(message));
      } else if (name === 'done') {
        subscriber.next({ type: 'done' });
        subscriber.complete();
      } else if (name === 'token') {
        try {
          const tok = (JSON.parse(currentData) as { token?: string }).token;
          if (tok) subscriber.next({ type: 'token', value: tok });
        } catch { /* fragment ignoré */ }
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
      subscriber.complete();
    } catch (err) {
      subscriber.error(err);
    }
  }
}
