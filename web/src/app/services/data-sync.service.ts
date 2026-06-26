import { Injectable, DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, Subject } from 'rxjs';

/**
 * Bus d'événement minimal pour synchroniser les vues qui affichent les MÊMES données
 * (arbre de la sidebar ↔ vues en cartes). Après un réordonnancement/déplacement
 * (depuis n'importe quelle vue), on émet `changed$` ; chaque vue concernée se recharge
 * → tout reste cohérent SANS rafraîchir la page (F5).
 */
@Injectable({ providedIn: 'root' })
export class DataSyncService {
  private readonly _changed = new Subject<void>();
  /** Émis quand une donnée ordonnable a changé (reorder/move). */
  readonly changed$ = this._changed.asObservable();

  notify(): void {
    this._changed.next();
  }

  /**
   * Abonne `handler` aux changements de données, avec auto-désabonnement lié au
   * cycle de vie du composant (`destroyRef`). Évite de recopier le couple
   * `changed$.pipe(takeUntilDestroyed(...)).subscribe(...)` dans chaque vue.
   */
  onChange(destroyRef: DestroyRef, handler: () => void): void {
    this.changed$.pipe(takeUntilDestroyed(destroyRef)).subscribe(handler);
  }

  /**
   * Persiste un réordonnancement : au succès, notifie les autres vues
   * (`notify()`) ; en cas d'échec, exécute `rollback` (typiquement recharger pour
   * annuler le déplacement optimiste à l'écran).
   */
  persist<T>(reorder$: Observable<T>, rollback: () => void): void {
    reorder$.subscribe({ next: () => this.notify(), error: () => rollback() });
  }
}
