import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

/**
 * Configuration publique chargee une seule fois au demarrage via APP_INITIALIZER.
 * Le flag demoMode bascule l'UI en mode vitrine (Settings/Export masques).
 */
export interface PublicConfig {
  demoMode: boolean;
}

@Injectable({ providedIn: 'root' })
export class ConfigService {
  private config: PublicConfig = { demoMode: false };

  constructor(private http: HttpClient) {}

  async load(): Promise<void> {
    try {
      this.config = await firstValueFrom(this.http.get<PublicConfig>('/api/config'));
    } catch {
      // Si l'endpoint n'est pas joignable au boot, on reste sur le default
      // (demoMode=false) pour ne pas bloquer l'app en dev.
    }
  }

  get demoMode(): boolean {
    return this.config.demoMode;
  }
}
