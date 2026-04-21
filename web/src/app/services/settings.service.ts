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
}

@Injectable({ providedIn: 'root' })
export class SettingsService {
  private readonly apiUrl = 'http://localhost:8080/api/settings';

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

  listOneMinModels(): Observable<{ groups: OneMinModelGroup[] }> {
    return this.http.get<{ groups: OneMinModelGroup[] }>(`${this.apiUrl}/models/onemin`, this.authOptions);
  }
}

/** Un groupe de modeles 1min.ai regroupes par fournisseur (Anthropic, OpenAI, ...). */
export interface OneMinModelGroup {
  provider: string;
  models: string[];
}
