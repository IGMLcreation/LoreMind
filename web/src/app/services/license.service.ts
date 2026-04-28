import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, of } from 'rxjs';

/**
 * Reflet de LicenseStatus (enum cote backend).
 */
export type LicenseStatus = 'NONE' | 'VALID' | 'GRACE' | 'EXPIRED' | 'UNVERIFIABLE';

export interface LicenseStatusDTO {
  enabled: boolean;
  status: LicenseStatus;
  patreonUserId: string | null;
  tierId: string | null;
  instanceId: string | null;
  expiresAt: string | null;
  lastRefreshAttemptAt: string | null;
  lastRefreshSucceeded: boolean | null;
  betaChannelEnabled: boolean;
}

/**
 * Reflet de UpdateCheckService.BetaStatus.
 */
export interface BetaStatusDTO {
  enabled: boolean;
  updateAvailable: boolean;
  anyUnknown: boolean;
  images: Array<{
    image: string;
    localDigest: string | null;
    remoteDigest: string | null;
    status: 'UP_TO_DATE' | 'UPDATE_AVAILABLE' | 'UNKNOWN';
    updateAvailable: boolean;
  }>;
  checkedAt: string;
  disabledReason: string | null;
}

/**
 * Service Angular pour la gestion de la licence Patreon.
 * Tous les endpoints sont proteges par HTTP Basic (admin).
 */
@Injectable({ providedIn: 'root' })
export class LicenseService {
  private readonly apiUrl = '/api/license';
  private readonly authOptions = { withCredentials: true };

  constructor(private http: HttpClient) {}

  getStatus(): Observable<LicenseStatusDTO | null> {
    return this.http.get<LicenseStatusDTO>(this.apiUrl, this.authOptions).pipe(
      catchError(() => of(null))
    );
  }

  getConnectUrl(): Observable<{ url: string } | null> {
    return this.http.get<{ url: string }>(`${this.apiUrl}/connect-url`, this.authOptions).pipe(
      catchError(() => of(null))
    );
  }

  install(jwt: string): Observable<LicenseStatusDTO | { error: string }> {
    return this.http.post<LicenseStatusDTO>(`${this.apiUrl}/install`, { jwt }, this.authOptions).pipe(
      catchError((err) => of({ error: err?.error?.error ?? 'Echec de l\'installation' }))
    );
  }

  disconnect(): Observable<boolean> {
    return this.http.delete<void>(this.apiUrl, this.authOptions).pipe(
      // Convertit en boolean : true = succes, false = erreur
      // (catchError plus bas masque les detail HTTP)
      catchError(() => of(false as any))
    ) as unknown as Observable<boolean>;
  }

  refresh(): Observable<LicenseStatusDTO | null> {
    return this.http.post<LicenseStatusDTO>(`${this.apiUrl}/refresh`, null, this.authOptions).pipe(
      catchError(() => of(null))
    );
  }

  setBetaChannel(enabled: boolean): Observable<LicenseStatusDTO | null> {
    return this.http.put<LicenseStatusDTO>(`${this.apiUrl}/beta-channel`, { enabled }, this.authOptions).pipe(
      catchError(() => of(null))
    );
  }

  checkBeta(): Observable<BetaStatusDTO | null> {
    return this.http.get<BetaStatusDTO>('/api/admin/updates/check-beta', this.authOptions).pipe(
      catchError(() => of(null))
    );
  }
}
