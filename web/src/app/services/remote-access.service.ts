import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, of, shareReplay } from 'rxjs';

/**
 * Accès distant à l'application de bureau (tablette / téléphone sur le réseau local).
 * Côté PC : réglage, code d'appairage, appareils (routes /api/settings/remote-access,
 * réservées à la machine). Côté tablette : appairage et état de la session.
 * En Docker ces routes n'existent pas (404) : l'écran Paramètres masque alors la section.
 */
export interface PairedDevice {
  id: string;
  name: string;
  userAgent?: string | null;
  createdAt?: string | null;
  lastSeenAt?: string | null;
}

export interface RemoteAccessStatus {
  enabled: boolean;
  port: number;
  running: boolean;
  urls: string[];
  lastError?: string | null;
  devices: PairedDevice[];
}

export interface PairingCode {
  code: string;
  expiresAt: string;
}

export interface RemoteSession {
  /** Requête arrivée par le réseau local (tablette) plutôt que depuis le PC. */
  remote: boolean;
  paired: boolean;
  deviceName?: string | null;
}

/** Réponse d'erreur du filtre / des contrôleurs. */
export const REMOTE_PAIRING_REQUIRED = 'remote_pairing_required';
export const REMOTE_FORBIDDEN = 'remote_forbidden';

@Injectable({ providedIn: 'root' })
export class RemoteAccessService {

  private readonly adminUrl = '/api/settings/remote-access';
  private session$?: Observable<RemoteSession>;

  constructor(private http: HttpClient) {}

  status(): Observable<RemoteAccessStatus> {
    return this.http.get<RemoteAccessStatus>(this.adminUrl);
  }

  configure(enabled: boolean, port: number): Observable<RemoteAccessStatus> {
    return this.http.put<RemoteAccessStatus>(this.adminUrl, { enabled, port });
  }

  newPairingCode(): Observable<PairingCode> {
    return this.http.post<PairingCode>(`${this.adminUrl}/pairing-code`, {});
  }

  revoke(deviceId: string): Observable<void> {
    return this.http.delete<void>(`${this.adminUrl}/devices/${deviceId}`);
  }

  pair(code: string, deviceName: string): Observable<PairedDevice> {
    return this.http.post<PairedDevice>('/api/remote-access/pair', { code, deviceName });
  }

  /**
   * Suis-je une tablette ? Mis en cache pour la durée de la page. Sans la route (Docker,
   * ancienne version), on se considère sur la machine elle-même.
   */
  session(): Observable<RemoteSession> {
    this.session$ ??= this.http.get<RemoteSession>('/api/remote-access/session').pipe(
      catchError(() => of({ remote: false, paired: true } as RemoteSession)),
      shareReplay(1)
    );
    return this.session$;
  }

  /** À appeler après un appairage : l'état de session a changé. */
  resetSession(): void {
    this.session$ = undefined;
  }
}
