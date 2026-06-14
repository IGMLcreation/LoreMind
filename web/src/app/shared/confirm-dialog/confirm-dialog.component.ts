import { Component, EventEmitter, Input, Output } from '@angular/core';

import { LucideAngularModule, TriangleAlert, X } from 'lucide-angular';
import { TranslatePipe } from '@ngx-translate/core';

export type ConfirmDialogVariant = 'warning' | 'danger' | 'info';

@Component({
    selector: 'app-confirm-dialog',
    imports: [LucideAngularModule, TranslatePipe],
    templateUrl: './confirm-dialog.component.html',
    styleUrls: ['./confirm-dialog.component.scss']
})
export class ConfirmDialogComponent {
  readonly TriangleAlert = TriangleAlert;
  readonly X = X;

  @Input() open = false;
  @Input() title = 'Confirmation';
  @Input() message = '';
  @Input() details: string[] = [];
  @Input() confirmLabel = 'Confirmer';
  @Input() cancelLabel = 'Annuler';
  @Input() variant: ConfirmDialogVariant = 'warning';

  @Output() confirmed = new EventEmitter<void>();
  @Output() cancelled = new EventEmitter<void>();

  onConfirm(): void { this.confirmed.emit(); }
  onCancel(): void { this.cancelled.emit(); }
}
