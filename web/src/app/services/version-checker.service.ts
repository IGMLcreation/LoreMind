import { Injectable, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

/**
 * Detecte qu'une nouvelle version de LoreMind a ete deployee pendant qu'un
 * onglet utilisateur est deja ouvert.
 *
 * Strategie : polling toutes les {@link POLL_INTERVAL_MS} sur /api/version.
 * La version observee au boot est figee comme reference. Si la version polled
 * differe, {@link hasUpdate} passe a true et l'UI peut afficher un bandeau
 * proposant un reload.
 *
 * Pourquoi pas un Service Worker ? Trop lourd pour le besoin (offline pas
 * pertinent, lifecycle complexe). Polling simple = ~15 lignes, fait le job.
 */
@Injectable({ providedIn: 'root' })
export class VersionCheckerService {
  private static readonly POLL_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes
  private static readonly INITIAL_DELAY_MS = 30 * 1000;     // 30s avant premier poll

  private readonly _bootVersion = signal<string | null>(null);
  private readonly _remoteVersion = signal<string | null>(null);
  private readonly _hasUpdate = computed(
    () => {
      const boot = this._bootVersion();
      const remote = this._remoteVersion();
      return boot !== null && remote !== null && boot !== remote;
    }
  );

  /** True quand la version remote differe de celle observee au boot. */
  readonly hasUpdate = this._hasUpdate;
  /** Version observee a l'ouverture du tab (figee). */
  readonly bootVersion = this._bootVersion.asReadonly();
  /** Derniere version observee sur le backend. */
  readonly remoteVersion = this._remoteVersion.asReadonly();

  private timer: ReturnType<typeof setTimeout> | null = null;

  constructor(private http: HttpClient) {}

  /**
   * Demarre le polling. A appeler une fois au boot de l'app.
   * Ignore si deja demarre (idempotent).
   */
  start(): void {
    if (this.timer !== null) return;
    void this.fetchInitial();
    this.timer = setInterval(
      () => void this.poll(),
      VersionCheckerService.POLL_INTERVAL_MS,
    );
  }

  stop(): void {
    if (this.timer !== null) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }

  /** Recharge l'app (force re-fetch index.html + assets). */
  reload(): void {
    window.location.reload();
  }

  private async fetchInitial(): Promise<void> {
    // Petit delai pour laisser le bootstrap se finir avant le premier appel.
    await new Promise(r => setTimeout(r, VersionCheckerService.INITIAL_DELAY_MS));
    const v = await this.fetchVersion();
    if (v && this._bootVersion() === null) {
      this._bootVersion.set(v);
      this._remoteVersion.set(v);
    }
  }

  private async poll(): Promise<void> {
    const v = await this.fetchVersion();
    if (v) this._remoteVersion.set(v);
  }

  private async fetchVersion(): Promise<string | null> {
    try {
      const resp = await firstValueFrom(
        this.http.get<{ version: string }>('/api/version'),
      );
      return resp?.version ?? null;
    } catch {
      // Backend down / restart en cours — on ignore, on retentera au prochain tick
      return null;
    }
  }
}
