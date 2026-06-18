import { Injectable, inject } from '@angular/core';
import { Observable, Subscriber } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { LanguageService } from './language.service';
import { parseSseStream, sseFetch } from '../shared/sse.util';

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
export type NarrativeEntityType = 'arc' | 'chapter' | 'scene' | 'character' | 'npc';

@Injectable({ providedIn: 'root' })
export class AiChatService {
  private readonly translate = inject(TranslateService);
  private readonly language = inject(LanguageService);
  private readonly loreEndpoint = '/api/ai/chat/stream';
  private readonly campaignEndpoint = '/api/ai/chat/stream-campaign';
  private readonly sessionEndpoint = '/api/ai/chat/stream-session';

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

  /**
   * Streame la réponse de l'IA pour un chat pendant une Session de jeu.
   * Le backend reconstitue automatiquement le contexte complet (lore +
   * campagne + système de JDR + journal de session).
   */
  streamChatForSession(sessionId: string, messages: ChatMessage[]): Observable<ChatStreamEvent> {
    return this.streamSse(this.sessionEndpoint, { sessionId, messages });
  }

  /** Plumbing SSE mutualisé entre les endpoints (Lore / Campaign / Session). */
  private streamSse(endpoint: string, body: Record<string, unknown>): Observable<ChatStreamEvent> {
    return sseFetch<ChatStreamEvent>(
      endpoint,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'text/event-stream',
          'X-User-Language': this.language.current
        },
        body: JSON.stringify(body)
      },
      (responseBody, subscriber) => this.consumeSseStream(responseBody, subscriber)
    );
  }

  /** Mappe les événements SSE bruts vers les `ChatStreamEvent` typés. */
  private async consumeSseStream(
    body: ReadableStream<Uint8Array>,
    subscriber: Subscriber<ChatStreamEvent>
  ): Promise<void> {
    try {
      await parseSseStream(body, ({ event, data }) => {
        if (event === 'error') {
          subscriber.error(new Error(this.safeParseMessage(data)));
        } else if (event === 'done') {
          subscriber.next({ type: 'done' });
          subscriber.complete();
        } else if (event === 'usage') {
          const usage = this.safeParseUsage(data);
          if (usage) subscriber.next({ type: 'usage', usage });
        } else {
          // Événement 'message' (défaut) : JSON {"token": "..."}
          const token = this.safeParseToken(data);
          if (token) subscriber.next({ type: 'token', value: token });
        }
      });
      // Fin de stream côté réseau sans event: done explicite → on complete quand même.
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
      return obj.message ?? this.translate.instant('services.unknownServerError');
    } catch {
      return json || this.translate.instant('services.unknownServerError');
    }
  }
}
