import { Injectable, NgZone } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * Reflet de SettingsDTO cote Brain / SettingsController cote Core.
 * `onemin_api_key_set` indique si une cle est configuree, sans la reveler.
 */
export type LlmProvider = 'ollama' | 'onemin' | 'openrouter' | 'mistral' | 'gemini';

export interface AppSettings {
  llm_provider: LlmProvider;
  ollama_base_url: string;
  llm_model: string;
  onemin_model: string;
  onemin_api_key_set: boolean;
  openrouter_model: string;
  openrouter_api_key_set: boolean;
  mistral_model: string;
  mistral_api_key_set: boolean;
  gemini_model: string;
  gemini_api_key_set: boolean;
  /** Embeddings (RAG des ateliers). */
  embedding_provider: 'ollama' | 'mistral';
  ollama_embedding_model: string;
  mistral_embedding_model: string;
  auto_pull_embedding_model: boolean;
  /** Nombre d'extraits récupérés par question (RAG). */
  rag_top_k: number;
  llm_num_ctx: number;
  /** Taille cible d'un morceau (tokens) pour l'import de PDF (règles/campagne). */
  import_chunk_tokens: number;
  /** Timeout HTTP des appels LLM (secondes). */
  llm_timeout_seconds: number;
}

/**
 * Patch partiel — seuls les champs a modifier sont presents.
 * `onemin_api_key: ''` efface la cle, `null`/absent ne touche a rien.
 */
export interface AppSettingsUpdate {
  llm_provider?: LlmProvider;
  ollama_base_url?: string;
  llm_model?: string;
  onemin_model?: string;
  onemin_api_key?: string;
  openrouter_model?: string;
  openrouter_api_key?: string;
  mistral_model?: string;
  mistral_api_key?: string;
  gemini_model?: string;
  gemini_api_key?: string;
  embedding_provider?: 'ollama' | 'mistral';
  ollama_embedding_model?: string;
  mistral_embedding_model?: string;
  auto_pull_embedding_model?: boolean;
  rag_top_k?: number;
  llm_num_ctx?: number;
  import_chunk_tokens?: number;
  llm_timeout_seconds?: number;
}

/** Metadonnees d'un modele Ollama (issues de /api/show). */
export interface OllamaModelInfo {
  /** Fenetre de contexte max du modele (en tokens). 0 si inconnue. */
  context_length: number;
}

/** Resultat d'un import de donnees (compteurs par type + images). */
export interface ImportResult {
  created: Record<string, number>;
  imagesUploaded: number;
  imagesReused: number;
}

@Injectable({ providedIn: 'root' })
export class SettingsService {
  private readonly apiUrl = '/api/settings';

  // HTTP Basic : le browser gere le prompt natif de credentials au premier 401.
  // withCredentials=true pour que les creds soient renvoyees sur les appels
  // suivants en cross-origin (dev Angular sur :4200 -> core sur :8080).
  private readonly authOptions = { withCredentials: true };

  constructor(private http: HttpClient, private zone: NgZone) {}

  getSettings(): Observable<AppSettings> {
    return this.http.get<AppSettings>(this.apiUrl, this.authOptions);
  }

  updateSettings(patch: AppSettingsUpdate): Observable<AppSettings> {
    return this.http.put<AppSettings>(this.apiUrl, patch, this.authOptions);
  }

  // --- Export / Import des donnees (sauvegarde & transfert d'instance) -------

  /** Telecharge l'export complet du contenu (zip : data.json + images). */
  exportData(): Observable<Blob> {
    return this.http.get('/api/admin/data/export',
      { withCredentials: true, responseType: 'blob' });
  }

  /** Importe un zip d'export en mode FUSION (ajoute, ne remplace pas). */
  importData(file: File): Observable<ImportResult> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ImportResult>('/api/admin/data/import', form, this.authOptions);
  }

  listOllamaModels(): Observable<{ models: string[] }> {
    return this.http.get<{ models: string[] }>(`${this.apiUrl}/models/ollama`, this.authOptions);
  }

  getOllamaModelInfo(name: string): Observable<OllamaModelInfo> {
    return this.http.post<OllamaModelInfo>(
      `${this.apiUrl}/models/ollama/info`, { name }, this.authOptions);
  }

  listOneMinModels(): Observable<{ groups: OneMinModelGroup[] }> {
    return this.http.get<{ groups: OneMinModelGroup[] }>(`${this.apiUrl}/models/onemin`, this.authOptions);
  }

  /** Catalogue dynamique des modeles OpenRouter (depuis leur API publique). */
  listOpenRouterModels(): Observable<{ models: OpenRouterModel[] }> {
    return this.http.get<{ models: OpenRouterModel[] }>(`${this.apiUrl}/models/openrouter`, this.authOptions);
  }

  /** Catalogue des modeles Mistral (dynamique si cle configuree, repli statique sinon). */
  listMistralModels(): Observable<{ models: MistralModel[] }> {
    return this.http.get<{ models: MistralModel[] }>(`${this.apiUrl}/models/mistral`, this.authOptions);
  }

  /** Catalogue des modeles Gemini (dynamique si cle configuree, repli statique sinon). */
  listGeminiModels(): Observable<{ models: GeminiModel[] }> {
    return this.http.get<{ models: GeminiModel[] }>(`${this.apiUrl}/models/gemini`, this.authOptions);
  }

  /**
   * Telecharge un modele Ollama et streame la progression au client.
   *
   * Le backend renvoie du NDJSON (un objet JSON par ligne) avec le format
   * Ollama natif : `{status, digest?, total?, completed?}`. On parse chaque
   * ligne au fur et a mesure et on emet via un Observable que le composant
   * peut consommer pour mettre a jour une barre de progression.
   *
   * On utilise `fetch` directement plutot que `HttpClient` car Angular
   * bufferise les reponses XHR, ce qui empeche le streaming en temps reel.
   */
  pullOllamaModel(name: string): Observable<OllamaPullEvent> {
    // fetch() s'execute hors zone Angular -> les emissions du subscriber
    // doivent etre forcees dans la zone via NgZone.run() pour que le change
    // detection se declenche et que la barre de progression se mette a jour
    // en temps reel.
    return new Observable<OllamaPullEvent>((subscriber) => {
      const controller = new AbortController();
      const emit = (ev: OllamaPullEvent) => this.zone.run(() => subscriber.next(ev));
      const fail = (e: unknown) => this.zone.run(() => subscriber.error(e));
      const done = () => this.zone.run(() => subscriber.complete());

      (async () => {
        try {
          const response = await fetch(`${this.apiUrl}/models/ollama/pull`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ name }),
            signal: controller.signal,
          });
          if (!response.ok) {
            const text = await response.text();
            fail(new Error(`HTTP ${response.status} : ${text || 'reponse vide'}`));
            return;
          }
          if (!response.body) {
            fail(new Error('Reponse sans corps : streaming non supporte par le navigateur'));
            return;
          }
          const reader = response.body.getReader();
          const decoder = new TextDecoder();
          let buffer = '';
          let eventsReceived = 0;
          while (true) {
            const { value, done: streamDone } = await reader.read();
            if (streamDone) break;
            buffer += decoder.decode(value, { stream: true });
            // Decoupage NDJSON : chaque ligne est un objet JSON complet.
            let nl: number;
            while ((nl = buffer.indexOf('\n')) >= 0) {
              const line = buffer.slice(0, nl).trim();
              buffer = buffer.slice(nl + 1);
              if (!line) continue;
              try {
                emit(JSON.parse(line) as OllamaPullEvent);
                eventsReceived++;
              } catch {
                // Ligne non JSON (rare) : on l'ignore.
              }
            }
          }
          // Reste eventuel dans le buffer (derniere ligne sans '\n').
          if (buffer.trim()) {
            try {
              emit(JSON.parse(buffer.trim()) as OllamaPullEvent);
              eventsReceived++;
            } catch { /* ignore */ }
          }
          if (eventsReceived === 0) {
            fail(new Error('Aucun evenement recu du serveur. Verifiez les logs du Brain (docker logs loremind-brain).'));
            return;
          }
          done();
        } catch (err) {
          if ((err as Error).name !== 'AbortError') fail(err);
        }
      })();
      return () => controller.abort();
    });
  }

  deleteOllamaModel(name: string): Observable<{ status: string; name: string }> {
    return this.http.delete<{ status: string; name: string }>(
      `${this.apiUrl}/models/ollama/${encodeURIComponent(name)}`, this.authOptions);
  }
}

/**
 * Format des evenements emis par Ollama pendant un pull. Les champs sont
 * optionnels car le serveur emet differents types de messages selon l'etape :
 *  - `{status: "pulling manifest"}`
 *  - `{status: "downloading", digest, total, completed}`
 *  - `{status: "verifying sha256 digest"}`
 *  - `{status: "writing manifest"}`
 *  - `{status: "removing any unused layers"}`
 *  - `{status: "success"}`
 *  - `{error: "..."}` en cas d'erreur (modele inexistant, reseau, etc.)
 */
export interface OllamaPullEvent {
  status?: string;
  digest?: string;
  total?: number;
  completed?: number;
  error?: string;
}

/** Un groupe de modeles 1min.ai regroupes par fournisseur (Anthropic, OpenAI, ...). */
export interface OneMinModelGroup {
  provider: string;
  models: string[];
}

/** Un modele OpenRouter (catalogue dynamique). */
export interface OpenRouterModel {
  id: string;
  name: string;
  /** Fenetre de contexte du modele (tokens). 0 si inconnue. */
  context_length: number;
  /** True si le modele est gratuit (id `:free` ou prix nul). */
  free: boolean;
}

/** Un modele Mistral (catalogue dynamique ou repli statique). */
export interface MistralModel {
  id: string;
}

/** Un modele Gemini (catalogue dynamique ou repli statique). */
export interface GeminiModel {
  id: string;
}
