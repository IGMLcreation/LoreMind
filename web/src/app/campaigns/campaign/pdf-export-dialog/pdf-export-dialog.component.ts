import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { LucideAngularModule, FileText, X, BookOpen } from 'lucide-angular';
import { TranslatePipe } from '@ngx-translate/core';
import { PDF_SECTIONS, PdfExportOptions, PdfSection } from '../../../services/pdf-export.model';
import { editionPreset, toExportOptions, toggleSection } from './pdf-export-presets';

/**
 * Modale des options de l'export PDF : version MJ ou joueurs, format de page, mode
 * impression, parties incluses et périmètre du lore. Passer en version joueurs
 * pré-sélectionne ce qui convient à des joueurs (sans bestiaire, quêtes ni tables,
 * lore limité aux pages liées) ; tout reste modifiable. Le parent télécharge au confirm.
 */
@Component({
  selector: 'app-pdf-export-dialog',
  imports: [LucideAngularModule, TranslatePipe],
  templateUrl: './pdf-export-dialog.component.html',
  styleUrls: ['./pdf-export-dialog.component.scss']
})
export class PdfExportDialogComponent implements OnChanges {
  readonly FileText = FileText;
  readonly X = X;
  readonly BookOpen = BookOpen;
  readonly allSections = PDF_SECTIONS;

  @Input() open = false;
  @Input() exporting = false;
  /** Faux = fiche Publication vide : la modale propose de la compléter. */
  @Input() hasPublication = true;
  /** Masque le lien vers la fiche (on est déjà dessus). */
  @Input() showPublicationLink = true;
  @Output() cancelled = new EventEmitter<void>();
  @Output() confirmed = new EventEmitter<PdfExportOptions>();
  @Output() editPublication = new EventEmitter<void>();

  edition: 'gm' | 'player' = 'gm';
  format: 'A4' | 'A5' | 'LETTER' = 'A4';
  print = false;
  sections: ReadonlySet<PdfSection> = editionPreset('gm').sections;
  lore: 'ALL' | 'LINKED' = 'ALL';

  ngOnChanges(changes: SimpleChanges): void {
    // Réouverture : on repart du livret MJ complet (pas de parties fantômes d'un export précédent).
    if (changes['open']?.currentValue && !changes['open'].previousValue) this.setEdition('gm');
  }

  get nothingSelected(): boolean { return this.sections.size === 0; }

  setEdition(edition: 'gm' | 'player'): void {
    this.edition = edition;
    ({ sections: this.sections, lore: this.lore } = editionPreset(edition));
  }

  has(section: PdfSection): boolean { return this.sections.has(section); }

  toggle(section: PdfSection): void {
    this.sections = toggleSection(this.sections, section);
  }

  onCancel(): void {
    if (this.exporting) return;
    this.cancelled.emit();
  }

  onConfirm(): void {
    if (this.exporting) return;
    const options = toExportOptions({
      edition: this.edition, format: this.format, print: this.print, sections: this.sections, lore: this.lore,
    });
    if (options) this.confirmed.emit(options);
  }
}
