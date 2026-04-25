import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, catchError, of, tap } from 'rxjs';

/**
 * Reflet de UpdateCheckService.UpdateStatus cote backend.
 */
export interface ImageStatus {
  image: string;
  localDigest: string | null;
  remoteDigest: string | null;
  updateAvailable: boolean;
}

export interface UpdateStatus {
  enabled: boolean;
  updateAvailable: boolean;
  images: ImageStatus[];
  checkedAt: string;
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
}
