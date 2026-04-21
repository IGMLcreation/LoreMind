import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

/**
 * État global de la command palette (modale de recherche).
 * Ouverte via bouton sidebar, raccourci Ctrl+K / Cmd+K, ou API programmatique.
 */
@Injectable({ providedIn: 'root' })
export class GlobalSearchService {
  private readonly _open$ = new BehaviorSubject<boolean>(false);
  readonly open$ = this._open$.asObservable();

  open(): void { this._open$.next(true); }
  close(): void { this._open$.next(false); }
  toggle(): void { this._open$.next(!this._open$.value); }
}
