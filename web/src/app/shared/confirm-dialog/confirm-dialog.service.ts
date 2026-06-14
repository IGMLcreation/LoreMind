import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { ConfirmDialogVariant } from './confirm-dialog.component';

export interface ConfirmDialogOptions {
  title?: string;
  message: string;
  details?: string[];
  confirmLabel?: string;
  cancelLabel?: string;
  variant?: ConfirmDialogVariant;
}

export interface ConfirmDialogState extends Required<Omit<ConfirmDialogOptions, 'details'>> {
  details: string[];
  open: boolean;
}

const CLOSED_STATE: ConfirmDialogState = {
  open: false,
  title: 'Confirmation',
  message: '',
  details: [],
  confirmLabel: 'Confirmer',
  cancelLabel: 'Annuler',
  variant: 'warning'
};

@Injectable({ providedIn: 'root' })
export class ConfirmDialogService {
  readonly state$ = new BehaviorSubject<ConfirmDialogState>(CLOSED_STATE);
  private resolver: ((value: boolean) => void) | null = null;

  constructor(private translate: TranslateService) {}

  confirm(opts: ConfirmDialogOptions): Promise<boolean> {
    // Si un dialog precedent est encore ouvert, on le resout en "false"
    // avant d'en ouvrir un nouveau pour eviter une fuite de Promise.
    if (this.resolver) {
      this.resolver(false);
      this.resolver = null;
    }
    this.state$.next({
      open: true,
      title: opts.title ?? this.translate.instant('confirmDialog.defaultTitle'),
      message: opts.message,
      details: opts.details ?? [],
      confirmLabel: opts.confirmLabel ?? this.translate.instant('common.confirm'),
      cancelLabel: opts.cancelLabel ?? this.translate.instant('common.cancel'),
      variant: opts.variant ?? 'warning'
    });
    return new Promise<boolean>((resolve) => { this.resolver = resolve; });
  }

  resolve(value: boolean): void {
    const r = this.resolver;
    this.resolver = null;
    this.state$.next(CLOSED_STATE);
    if (r) r(value);
  }
}
