import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideAngularModule, LucideIconData } from 'lucide-angular';

export interface IconPickerOption {
  key: string;
  icon: LucideIconData;
}

/**
 * Petit composant reutilisable : grille d'icones cliquables avec selection unique.
 * Utilise par les formulaires de creation / edition d'arcs, chapitres et scenes
 * (ainsi que potentiellement les dossiers du Lore plus tard).
 *
 * Usage :
 *   <app-icon-picker [options]="iconOptions" [(selected)]="selectedIcon"></app-icon-picker>
 *
 * Le composant est purement presentationnel : il n'interroge pas le registre
 * d'icones lui-meme — l'appelant lui passe la banque a afficher.
 */
@Component({
  selector: 'app-icon-picker',
  standalone: true,
  imports: [CommonModule, LucideAngularModule],
  template: `
    <div class="icon-grid">
      <button
        type="button"
        class="icon-btn"
        *ngFor="let option of options"
        [class.selected]="selected === option.key"
        [attr.aria-pressed]="selected === option.key"
        [title]="option.key"
        (click)="pick(option.key)">
        <lucide-icon [img]="option.icon" [size]="18"></lucide-icon>
      </button>
    </div>
  `,
  styleUrls: ['./icon-picker.component.scss']
})
export class IconPickerComponent {
  @Input() options: IconPickerOption[] = [];
  @Input() selected: string | null = null;
  /** Permet le binding two-way `[(selected)]`. */
  @Output() selectedChange = new EventEmitter<string | null>();
  /** Si true, recliquer sur l'icone actuellement selectionnee la deselectionne. */
  @Input() allowDeselect = true;

  pick(key: string): void {
    const next = this.allowDeselect && this.selected === key ? null : key;
    this.selected = next;
    this.selectedChange.emit(next);
  }
}
