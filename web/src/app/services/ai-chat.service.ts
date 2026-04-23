import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

/**
 * Un message d'une conversation IA (vue front).
 * Aligné sur le DTO ChatMessageDTO côté Java.
 */
export interface ChatMessage {
  role: 'user' | 'assistant' | 'system';
  content: string;
}

/**
 * Événements émis par le flux SSE durant un chat streamé.
 * - token : un fragment de texte vient d'arriver (à concaténer dans la bulle).
 * - done  : le stream s'est terminé proprement (l'observable va compléter).
 * - error : une erreur s'est produite côté serveur (l'observable va erreur-compléter).
 */
/**
 * Instantané d'occupation de la fenêtre de contexte (émis 1x par tour, avant le streaming).
 * Les valeurs sont exprimées en tokens (~cl100k_base, ±10% vs tokenizer natif du modèle).
 */
export interface ChatUsage {
  system: number;
  history: number;
  current: number;
  max: number;
}

export type ChatStreamEvent =
  | { type: 'usage'; usage: ChatUsage }
  | { type: 'token'; value: string }
  | { type: 'done' }
  | { type: 'error'; message: string };

/**
 * Service qui encapsule l'appel SSE au backend Java (POST /api/ai/chat/stream).
 *
 * On n'utilise pas EventSource (API navigateur natif) car elle ne supporte
 * que GET sans body. On fait donc un fetch() avec un ReadableStream qu'on
 * décode ligne par ligne pour extraire les événements SSE.
 */
/** Type d'entité narrative focus pour le chat Campagne. */
export type NarrativeEntityType = 'arc' | 'chapter' | 'scene' | 'character';

@Injectable({ providedIn: 'root' })
export class AiChatService {
  private readonly loreEndpoint = '/api/ai/chat/stream';
  private readonly campaignEndpoint = '/api/ai/chat/stream-campaign';

  /**
   * Streame la réponse de l'IA pour un historique de messages donné (chat ancré Lore).
   * L'Observable :
   *  - émet `{type: 'token', value}` à chaque fragment reçu ;
   *  - se complete quand `event: done` arrive ;
   *  - erreur-complete (via `throwError`) quand `event: error` arrive ou qu'une erreur réseau survient.
   *
   * Annuler la subscription annule proprement le fetch (AbortController).
   */
  streamChat(
    loreId: string,
    messages: ChatMessage[],
    pageId?: string | null
  ): Observable<ChatStreamEvent> {
    const body: Record<string, unknown> = { loreId, messages };
    if (pageId) body['pageId'] = pageId;
    return this.streamSse(this.loreEndpoint, body);
  }

  /**
   * Streame la réponse de l'IA pour un chat ancré sur une Campagne.
   * Le backend charge automatiquement la carte narrative (arcs/chapitres/scènes)
   * et, si la campagne est liée à un Lore, sa carte structurelle également.
   *
   * `entityType` + `entityId` sont optionnels : si fournis, focalisent l'IA
   * sur l'arc / chapitre / scène en cours d'édition.
   */
  streamChatForCampaign(
    campaignId: string,
    messages: ChatMessage[],
    entityType?: NarrativeEntityType | null,
    entityId?: string | null
  ): Observable<ChatStreamEvent> {
    const body: Record<string, unknown> = { campaignId, messages };
    if (entityType && entityId) {
      body['entityType'] = entityType;
      body['entityId'] = entityId;
    }
    return this.streamSse(this.campaignEndpoint, body);
  }

  /** Plumbing SSE mutualisé entre les 2 endpoints (Lore et Campaign). */
  private streamSse(endpoint: string, body: Record<string, unknown>): Observable<ChatStreamEvent> {
    return new Observable<ChatStreamEvent>((subscriber) => {
      const controller = new AbortController();

      fetch(endpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'text/event-stream'
        },
        body: JSON.stringify(body),
        signal: controller.signal
      })
        .then(async (response) => {
          if (!response.ok || !response.body) {
            subscriber.error(new Error(`HTTP ${response.status}`));
            return;
          }
          await this.consumeSseStream(response.body, subscriber);
        })
        .catch((err) => {
          if (controller.signal.aborted) return; // annulation volontaire, silencieuse
          subscriber.error(err);
        });

      return () => controller.abort();
    });
  }

  /**
   * Consomme un ReadableStream SSE ligne par ligne.
   * Format attendu (un événement = un bloc séparé par `\n\n`) :
   *   event: done          (optionnel, défaut = 'message')
   *   data: {...}          (une ou plusieurs lignes, concaténées avec '\n')
   *   <ligne vide>         (séparateur d'événements)
   */
  private async consumeSseStream(
    body: ReadableStream<Uint8Array>,
    subscriber: { next: (e: ChatStreamEvent) => void; error: (e: unknown) => void; complete: () => void }
  ): Promise<void> {
    const reader = body.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';

    // Événement SSE en cours de construction (accumulé entre lignes vides).
    let currentEvent: string | null = null;
    let currentData = '';

    const dispatchCurrentEvent = () => {
      const eventName = currentEvent ?? 'message';
      // DEBUG jauge de contexte — à retirer une fois stabilisé.
      if (eventName !== 'message') {
        console.log('[AiChatService] SSE event:', eventName, 'data:', currentData);
      }
      if (eventName === 'error') {
        const message = this.safeParseMessage(currentData);
        subscriber.error(new Error(message));
      } else if (eventName === 'done') {
        subscriber.next({ type: 'done' });
        subscriber.complete();
      } else if (eventName === 'usage') {
        const usage = this.safeParseUsage(currentData);
        if (usage) subscriber.next({ type: 'usage', usage });
      } else {
        // Événement 'message' (défaut) : JSON {"token": "..."}
        const token = this.safeParseToken(currentData);
        if (token) subscriber.next({ type: 'token', value: token });
      }
      currentEvent = null;
      currentData = '';
    };

    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });

        // On découpe par lignes ; la dernière (potentiellement incomplète) reste dans buffer.
        let newlineIdx: number;
        while ((newlineIdx = buffer.indexOf('\n')) >= 0) {
          const line = buffer.slice(0, newlineIdx).replace(/\r$/, '');
          buffer = buffer.slice(newlineIdx + 1);

          if (line === '') {
            // Ligne vide = fin d'un événement SSE : on dispatch ce qu'on a accumulé.
            if (currentEvent !== null || currentData !== '') {
              dispatchCurrentEvent();
            }
            continue;
          }
          if (line.startsWith('event:')) {
            currentEvent = line.slice(6).trim();
          } else if (line.startsWith('data:')) {
            const chunk = line.slice(5).replace(/^ /, '');
            currentData = currentData ? `${currentData}\n${chunk}` : chunk;
          }
          // Autres champs SSE (id:, retry:) ignorés pour le MVP.
        }
      }
      // Fin de stream côté réseau sans event: done explicite → on complete quand même.
      if (currentEvent !== null || currentData !== '') dispatchCurrentEvent();
      subscriber.complete();
    } catch (err) {
      subscriber.error(err);
    }
  }

  private safeParseToken(json: string): string | null {
    try {
      const obj = JSON.parse(json) as { token?: string };
      return typeof obj.token === 'string' ? obj.token : null;
    } catch {
      return null;
    }
  }

  private safeParseUsage(json: string): ChatUsage | null {
    try {
      const obj = JSON.parse(json) as Partial<ChatUsage>;
      if (
        typeof obj.system === 'number' &&
        typeof obj.history === 'number' &&
        typeof obj.current === 'number' &&
        typeof obj.max === 'number'
      ) {
        return { system: obj.system, history: obj.history, current: obj.current, max: obj.max };
      }
      return null;
    } catch {
      return null;
    }
  }

  private safeParseMessage(json: string): string {
    try {
      const obj = JSON.parse(json) as { message?: string };
      return obj.message ?? 'Erreur inconnue côté serveur.';
    } catch {
      return json || 'Erreur inconnue côté serveur.';
    }
  }
}
