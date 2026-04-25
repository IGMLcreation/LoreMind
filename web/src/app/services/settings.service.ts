import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * Reflet de SettingsDTO cote Brain / SettingsController cote Core.
 * `onemin_api_key_set` indique si une cle est configuree, sans la reveler.
 */
export interface AppSettings {
  llm_provider: 'ollama' | 'onemin';
  ollama_base_url: string;
  llm_model: string;
  onemin_model: string;
  onemin_api_key_set: boolean;
  llm_num_ctx: number;
}

/**
 * Patch partiel — seuls les champs a modifier sont presents.
 * `onemin_api_key: ''` efface la cle, `null`/absent ne touche a rien.
 */
export interface AppSettingsUpdate {
  llm_provider?: 'ollama' | 'onemin';
  ollama_base_url?: string;
  llm_model?: string;
  onemin_model?: string;
  onemin_api_key?: string;
  llm_num_ctx?: number;
}

/** Metadonnees d'un modele Ollama (issues de /api/show). */
export interface OllamaModelInfo {
  /** Fenetre de contexte max du modele (en tokens). 0 si inconnue. */
  context_length: number;
}

@Injectable({ providedIn: 'root' })
export class SettingsService {
  private readonly apiUrl = '/api/settings';

  // HTTP Basic : le browser gere le prompt natif de credentials au premier 401.
  // withCredentials=true pour que les creds soient renvoyees sur les appels
  // suivants en cross-origin (dev Angular sur :4200 -> core sur :8080).
  private readonly authOptions = { withCredentials: true };

  constructor(private http: HttpClient) {}

  getSettings(): Observable<AppSettings> {
    return this.http.get<AppSettings>(this.apiUrl, this.authOptions);
  }

  updateSettings(patch: AppSettingsUpdate): Observable<AppSettings> {
    return this.http.put<AppSettings>(this.apiUrl, patch, this.authOptions);
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
    return new Observable<OllamaPullEvent>((subscriber) => {
      const controller = new AbortController();
      (async () => {
        try {
          const response = await fetch(`${this.apiUrl}/models/ollama/pull`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ name }),
            signal: controller.signal,
          });
          if (!response.ok || !response.body) {
            subscriber.error(new Error(`HTTP ${response.status}`));
            return;
          }
          const reader = response.body.getReader();
          const decoder = new TextDecoder();
          let buffer = '';
          while (true) {
            const { value, done } = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, { stream: true });
            // Decoupage NDJSON : chaque ligne est un objet JSON complet.
            let nl: number;
            while ((nl = buffer.indexOf('\n')) >= 0) {
              const line = buffer.slice(0, nl).trim();
              buffer = buffer.slice(nl + 1);
              if (!line) continue;
              try {
                subscriber.next(JSON.parse(line) as OllamaPullEvent);
              } catch {
                // Ligne non JSON (rare) : on l'ignore.
              }
            }
          }
          subscriber.complete();
        } catch (err) {
          if ((err as Error).name !== 'AbortError') subscriber.error(err);
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
