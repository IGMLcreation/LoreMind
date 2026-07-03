import { Component, EventEmitter, Input, Output } from '@angular/core';
import { LucideAngularModule, Download, X } from 'lucide-angular';
import { TranslatePipe } from '@ngx-translate/core';
import { FoundryExportOptions } from '../../../services/campaign.service';

/**
 * Modale de choix du PÉRIMÈTRE de l'export Foundry : tout, ou seulement les
 * cartes + ennemis (usage table de jeu), ou seulement les journaux… Trois cases
 * indépendantes + raccourcis. Le parent déclenche le téléchargement au confirm.
 */
@Component({
    selector: 'app-foundry-export-dialog',
    imports: [LucideAngularModule, TranslatePipe],
    templateUrl: './foundry-export-dialog.component.html',
    styleUrls: ['./foundry-export-dialog.component.scss']
})
export class FoundryExportDialogComponent {
  readonly Download = Download;
  readonly X = X;

  @Input() open = false;
  @Input() exporting = false;
  @Output() cancelled = new EventEmitter<void>();
  @Output() confirmed = new EventEmitter<FoundryExportOptions>();

  maps = true;
  journals = true;
  tables = true;

  get nothingSelected(): boolean { return !this.maps && !this.journals && !this.tables; }

  /** Raccourci « usage table de jeu » : uniquement les cartes + ennemis. */
  presetMapsOnly(): void {
    this.maps = true;
    this.journals = false;
    this.tables = false;
  }

  presetAll(): void {
    this.maps = true;
    this.journals = true;
    this.tables = true;
  }

  onCancel(): void {
    if (this.exporting) return;
    this.cancelled.emit();
  }

  onConfirm(): void {
    if (this.nothingSelected || this.exporting) return;
    this.confirmed.emit({ maps: this.maps, journals: this.journals, tables: this.tables });
  }
}
