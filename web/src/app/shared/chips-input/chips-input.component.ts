import { Component, Input, Output, EventEmitter } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { LucideAngularModule, X } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

/**
 * Composant réutilisable de saisie de chips (étiquettes textuelles).
 *
 * Usage :
 *   <app-chips-input
 *     [value]="tags"
 *     (valueChange)="tags = $event"
 *     placeholder="Ajouter un tag..."></app-chips-input>
 *
 * Règles UX :
 *  - Entrée valide la saisie en cours
 *  - Virgule valide aussi (pratique pour enchaîner plusieurs tags)
 *  - Doublons silencieusement ignorés
 *  - Espaces en début/fin trimmés
 *  - Clic sur X retire le chip
 */
@Component({
    selector: 'app-chips-input',
    imports: [FormsModule, LucideAngularModule, TranslatePipe],
    templateUrl: './chips-input.component.html',
    styleUrls: ['./chips-input.component.scss']
})
export class ChipsInputComponent {
  readonly X = X;

  @Input() value: string[] = [];
  @Input() placeholder = '';
  @Output() valueChange = new EventEmitter<string[]>();

  /** Texte de l'input en cours de saisie. */
  current = '';

  constructor(private translate: TranslateService) {
    this.placeholder = this.translate.instant('chipsInput.placeholder');
  }

  /** Ajoute le texte courant s'il est non vide et non déjà présent. */
  add(): void {
    const raw = this.current.trim().replace(/,$/, '').trim();
    if (!raw) return;
    if (this.value.includes(raw)) {
      this.current = '';
      return;
    }
    this.valueChange.emit([...this.value, raw]);
    this.current = '';
  }

  /** Retire le chip à l'index donné. */
  remove(index: number): void {
    const next = [...this.value];
    next.splice(index, 1);
    this.valueChange.emit(next);
  }

  /** Gère Enter et virgule comme déclencheurs d'ajout. */
  onKeyDown(event: KeyboardEvent): void {
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault();
      this.add();
    } else if (event.key === 'Backspace' && this.current === '' && this.value.length > 0) {
      // Backspace sur input vide retire le dernier chip (UX courante).
      this.remove(this.value.length - 1);
    }
  }
}
