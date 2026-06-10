import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ConfirmDialogComponent } from './confirm-dialog.component';
import { ConfirmDialogService } from './confirm-dialog.service';

@Component({
    selector: 'app-confirm-dialog-host',
    imports: [CommonModule, ConfirmDialogComponent],
    template: `
    <app-confirm-dialog
      *ngIf="(svc.state$ | async) as s"
      [open]="s.open"
      [title]="s.title"
      [message]="s.message"
      [details]="s.details"
      [confirmLabel]="s.confirmLabel"
      [cancelLabel]="s.cancelLabel"
      [variant]="s.variant"
      (confirmed)="svc.resolve(true)"
      (cancelled)="svc.resolve(false)">
    </app-confirm-dialog>
  `
})
export class ConfirmDialogHostComponent {
  constructor(public svc: ConfirmDialogService) {}
}
