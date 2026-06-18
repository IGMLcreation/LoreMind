import { describe, it, expect, vi, afterEach } from 'vitest';
import { parseSseStream, sseFetch, SseEvent } from './sse.util';

/** Construit un ReadableStream à partir d'une chaîne (un seul chunk). */
function streamFrom(text: string): ReadableStream<Uint8Array> {
  const bytes = new TextEncoder().encode(text);
  return new ReadableStream<Uint8Array>({
    start(controller) {
      controller.enqueue(bytes);
      controller.close();
    },
  });
}

async function collect(text: string): Promise<SseEvent[]> {
  const events: SseEvent[] = [];
  await parseSseStream(streamFrom(text), (e) => events.push(e));
  return events;
}

describe('parseSseStream', () => {
  it('parse un événement nommé avec sa data', async () => {
    expect(await collect('event: token\ndata: {"token":"hi"}\n\n'))
      .toEqual([{ event: 'token', data: '{"token":"hi"}' }]);
  });

  it("nomme l'événement par défaut 'message'", async () => {
    expect(await collect('data: {"token":"x"}\n\n'))
      .toEqual([{ event: 'message', data: '{"token":"x"}' }]);
  });

  it('sépare plusieurs événements', async () => {
    const events = await collect('event: a\ndata: 1\n\nevent: b\ndata: 2\n\n');
    expect(events).toEqual([
      { event: 'a', data: '1' },
      { event: 'b', data: '2' },
    ]);
  });

  it('concatène les lignes data multiples avec des \\n', async () => {
    const events = await collect('data: ligne1\ndata: ligne2\n\n');
    expect(events[0].data).toBe('ligne1\nligne2');
  });

  it('gère les fins de ligne CRLF', async () => {
    expect(await collect('event: x\r\ndata: y\r\n\r\n'))
      .toEqual([{ event: 'x', data: 'y' }]);
  });

  it('ignore les lignes de commentaire keep-alive (`: ...`)', async () => {
    expect(await collect(': keep-alive\n\ndata: ok\n\n'))
      .toEqual([{ event: 'message', data: 'ok' }]);
  });

  it('dispatche un bloc résiduel en fin de flux sans ligne vide finale', async () => {
    // Lignes terminées par \n mais pas de ligne vide séparatrice finale.
    expect(await collect('event: done\ndata: {}\n'))
      .toEqual([{ event: 'done', data: '{}' }]);
  });

  it('ne dispatche rien sur un flux vide', async () => {
    expect(await collect('')).toEqual([]);
  });
});

describe('sseFetch', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("consomme le corps et expose les évènements jusqu'à complétion", async () => {
    const body = streamFrom('event: done\ndata: {}\n\n');
    vi.stubGlobal('fetch', vi.fn(async () => new Response(body, { status: 200 })));

    const seen: string[] = [];
    await new Promise<void>((resolve, reject) => {
      sseFetch<string>('/x', { method: 'POST' }, async (b, sub) => {
        await parseSseStream(b, (e) => seen.push(e.event));
        sub.next('mapped');
        sub.complete();
      }).subscribe({ next: (v) => seen.push(v), complete: resolve, error: reject });
    });

    expect(seen).toContain('done');
    expect(seen).toContain('mapped');
  });

  it('émet une erreur HTTP si la réponse est non-ok', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('', { status: 500 })));

    await expect(
      new Promise<void>((resolve, reject) => {
        sseFetch('/x', {}, async () => undefined).subscribe({
          error: reject,
          complete: resolve,
        });
      }),
    ).rejects.toThrow('HTTP 500');
  });

  it('annule le fetch quand on se désabonne', async () => {
    let abortedSignal: AbortSignal | undefined;
    vi.stubGlobal('fetch', vi.fn((_url: string, init: RequestInit) => {
      abortedSignal = init.signal as AbortSignal;
      return new Promise(() => { /* ne résout jamais : on teste l'annulation */ });
    }));

    const sub = sseFetch('/x', {}, async () => undefined).subscribe();
    sub.unsubscribe();
    expect(abortedSignal?.aborted).toBe(true);
  });
});
