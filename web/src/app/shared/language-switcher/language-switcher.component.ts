import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { LanguageService } from '../../services/language.service';

/**
 * Sélecteur de langue réutilisable (liste déroulante).
 *
 * Réutilisable partout : page Paramètres, en-tête, etc. Délègue tout au
 * LanguageService — le changement est appliqué à chaud, sans rechargement.
 *
 * Usage : <app-language-switcher></app-language-switcher>
 */
@Component({
  selector: 'app-language-switcher',
  standalone: true,
  imports: [TranslatePipe, FormsModule],
  template: `
    <label class="lang-switcher">
      <span class="lang-switcher__label">{{ 'language.label' | translate }}</span>
      <select
        class="lang-switcher__select"
        [ngModel]="lang.current"
        (ngModelChange)="lang.use($event)">
        @for (l of lang.languages; track l.code) {
          <option [value]="l.code">{{ l.flag }} {{ l.labelKey | translate }}</option>
        }
      </select>
    </label>
  `,
  styles: [`
    .lang-switcher {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
    }
    .lang-switcher__label {
      font-weight: 500;
    }
    .lang-switcher__select {
      padding: 0.35rem 0.5rem;
      border-radius: 6px;
    }
  `],
})
export class LanguageSwitcherComponent {
  readonly lang = inject(LanguageService);
}
