import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideAngularModule, TriangleAlert, X } from 'lucide-angular';

export type ConfirmDialogVariant = 'warning' | 'danger' | 'info';

@Component({
    selector: 'app-confirm-dialog',
    imports: [CommonModule, LucideAngularModule],
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
