import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, catchError, of, tap } from 'rxjs';

/**
 * Reflet de UpdateCheckService.UpdateStatus cote backend (post-refactor v0.8.x).
 *
 * Etat tri-state par image : UP_TO_DATE / UPDATE_AVAILABLE / UNKNOWN.
 * Comparaison faite par version semver (BuildProperties cote backend), plus
 * fiable que les digests qui ne survivaient pas a un restart sans pull.
 */
export type ImageStatusKind = 'UP_TO_DATE' | 'UPDATE_AVAILABLE' | 'UNKNOWN';

export interface ImageStatus {
  image: string;
  localVersion: string | null;
  remoteVersion: string | null;
  status: ImageStatusKind;
  /** Conserve pour back-compat ; equivalent a (status === 'UPDATE_AVAILABLE'). */
  updateAvailable: boolean;
}

export interface UpdateStatus {
  enabled: boolean;
  /** True si au moins une image a status === 'UPDATE_AVAILABLE'. */
  updateAvailable: boolean;
  /** True si au moins une image a status === 'UNKNOWN'. */
  anyUnknown: boolean;
  /** Version courante du binaire (BuildProperties). null si build-info absent. */
  currentVersion: string | null;
  images: ImageStatus[];
  checkedAt: string;
}

/** Canal courant + dernier resultat de bascule (cf. ChannelStatusDTO cote backend). */
export type ChannelName = 'stable' | 'beta';
export type SwitchStatus = 'IN_PROGRESS' | 'SUCCESS' | 'ERROR';

export interface ChannelStatusDTO {
  currentChannel: ChannelName;
  switcherAvailable: boolean;
  lastSwitch: {
    id: string;
    status: SwitchStatus;
    channel: ChannelName;
    message: string;
    completedAt: string;
  } | null;
}

/** Reflet de UpdateCheckService.BetaStatus (images beta publiques, verifiees en beta). */
export interface BetaStatusDTO {
  enabled: boolean;
  updateAvailable: boolean;
  anyUnknown: boolean;
  images: ImageStatus[];
  checkedAt: string;
  disabledReason: string | null;
}

/**
 * Service de detection / declenchement des mises a jour des conteneurs
 * LoreMind. Endpoints proteges par HTTP Basic (admin) — withCredentials
 * comme pour SettingsService.
 *
 * `updateAvailable$` est un signal global consomme par la sidebar pour
 * afficher un badge. Il est rafraichi via {@link checkNow}.
 */
@Injectable({ providedIn: 'root' })
export class UpdatesService {
  private readonly apiUrl = '/api/admin/updates';
  private readonly authOptions = { withCredentials: true };

  private readonly _updateAvailable$ = new BehaviorSubject<boolean>(false);
  readonly updateAvailable$ = this._updateAvailable$.asObservable();

  constructor(private http: HttpClient) {}

  /**
   * Interroge le backend. Met a jour `updateAvailable$` au passage.
   * Renvoie `null` en cas d'erreur (pas authentifie, feature off, etc.)
   * pour ne pas faire crasher l'UI au boot.
   */
  checkNow(): Observable<UpdateStatus | null> {
    return this.http.get<UpdateStatus>(`${this.apiUrl}/check`, this.authOptions).pipe(
      tap(s => this._updateAvailable$.next(!!s?.updateAvailable)),
      catchError(() => {
        this._updateAvailable$.next(false);
        return of(null);
      })
    );
  }

  apply(): Observable<{ status: string; message: string } | null> {
    return this.http.post<{ status: string; message: string }>(
      `${this.apiUrl}/apply`, null, this.authOptions
    ).pipe(
      catchError(() => of(null))
    );
  }

  /** Nouvelle beta disponible ? (seulement quand l'instance tourne sur le canal beta). */
  checkBeta(): Observable<BetaStatusDTO | null> {
    return this.http.get<BetaStatusDTO>(`${this.apiUrl}/check-beta`, this.authOptions).pipe(
      catchError(() => of(null))
    );
  }

  /** Canal courant et dernier resultat de bascule (pour le polling de l'UI). */
  getChannelStatus(): Observable<ChannelStatusDTO | null> {
    return this.http.get<ChannelStatusDTO>(`${this.apiUrl}/channel`, this.authOptions).pipe(
      catchError(() => of(null))
    );
  }

  /** Demande une bascule de canal : 202 + { id, channel }, sinon { error } (503 = sidecar absent…). */
  switchChannel(channel: ChannelName): Observable<{ id: string; channel: ChannelName } | { error: string }> {
    return this.http.post<{ id: string; channel: ChannelName }>(
      `${this.apiUrl}/channel/switch`, { channel }, this.authOptions
    ).pipe(
      catchError((err) => of({ error: err?.error?.error ?? 'Echec du switch de canal' }))
    );
  }
}
