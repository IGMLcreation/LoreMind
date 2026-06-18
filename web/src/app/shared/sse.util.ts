import { Observable, Subscriber } from 'rxjs';

/**
 * Plumbing SSE mutualisé entre les services qui streament depuis le backend
 * (chat IA, import de campagne, import de règles, chat d'atelier).
 *
 * On n'utilise pas `EventSource` (API navigateur native) : elle ne supporte que
 * GET sans body, alors qu'on a besoin de POST (+ multipart). On fait donc un
 * `fetch()` dont on décode le `ReadableStream` ligne par ligne.
 */

/** Un événement SSE complet (bloc séparé par une ligne vide). */
export interface SseEvent {
  /** Nom de l'événement (`event:` du flux), ou `'message'` par défaut. */
  event: string;
  /** Charge utile (`data:`) ; les lignes multiples sont concaténées par `\n`. */
  data: string;
}

/**
 * Décode un `ReadableStream` SSE et invoque `onEvent` pour chaque bloc complet.
 *
 * Format attendu (un événement = un bloc terminé par une ligne vide) :
 *   event: done          (optionnel, défaut = 'message')
 *   data: {...}          (une ou plusieurs lignes, concaténées avec '\n')
 *   <ligne vide>         (séparateur d'événements)
 *
 * Gère le buffer partiel entre deux lectures, les fins de ligne `\r\n`, et
 * dispatche un éventuel bloc résiduel en fin de flux (réseau coupé sans ligne
 * vide finale). Ne complète/erreur PAS d'observable : l'appelant mappe chaque
 * `SseEvent` vers son type métier et décide de la terminaison.
 */
export async function parseSseStream(
  body: ReadableStream<Uint8Array>,
  onEvent: (ev: SseEvent) => void
): Promise<void> {
  const reader = body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';
  let currentEvent: string | null = null;
  let currentData = '';

  const flush = () => {
    if (currentEvent === null && currentData === '') return;
    onEvent({ event: currentEvent ?? 'message', data: currentData });
    currentEvent = null;
    currentData = '';
  };

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
        flush(); // ligne vide = fin d'un événement SSE
        continue;
      }
      if (line.startsWith('event:')) {
        currentEvent = line.slice(6).trim();
      } else if (line.startsWith('data:')) {
        const chunk = line.slice(5).replace(/^ /, '');
        currentData = currentData ? `${currentData}\n${chunk}` : chunk;
      }
      // Autres champs SSE (id:, retry:) ignorés.
    }
  }
  // Fin de stream sans ligne vide finale : on dispatche le dernier bloc accumulé.
  flush();
}

/**
 * Ouvre un flux SSE via `fetch()` et expose ses événements dans un `Observable`.
 *
 * Annuler la souscription annule proprement le fetch (AbortController) ; une
 * erreur réseau survenue APRÈS une annulation volontaire est ignorée. Le rappel
 * `consume` reçoit le corps de la réponse et le subscriber : il mappe les
 * événements bruts vers le type métier `T` et complète/erreur le subscriber
 * (typiquement via {@link parseSseStream}).
 */
export function sseFetch<T>(
  url: string,
  init: RequestInit,
  consume: (body: ReadableStream<Uint8Array>, subscriber: Subscriber<T>) => Promise<void>
): Observable<T> {
  return new Observable<T>((subscriber) => {
    const controller = new AbortController();

    fetch(url, { ...init, signal: controller.signal })
      .then(async (response) => {
        if (!response.ok || !response.body) {
          subscriber.error(new Error(`HTTP ${response.status}`));
          return;
        }
        await consume(response.body, subscriber);
      })
      .catch((err) => {
        if (controller.signal.aborted) return; // annulation volontaire, silencieuse
        subscriber.error(err);
      });

    return () => controller.abort();
  });
}
