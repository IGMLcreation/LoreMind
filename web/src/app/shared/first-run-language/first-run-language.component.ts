import { Component, inject } from '@angular/core';
import { LanguageService } from '../../services/language.service';

/**
 * Écran de choix de langue au TOUT PREMIER lancement (overlay one-shot).
 *
 * Ne s'affiche que si aucune langue n'a encore été choisie explicitement
 * (cf. LanguageService.hasExplicitChoice). Le choix est mémorisé en localStorage
 * via LanguageService.use() → aux lancements suivants l'overlay ne réapparaît pas,
 * et le sélecteur de langue habituel prend le relais.
 *
 * Textes volontairement BILINGUES / neutres (l'utilisateur n'a pas encore choisi).
 */
@Component({
  selector: 'app-first-run-language',
  standalone: true,
  templateUrl: './first-run-language.component.html',
  styleUrls: ['./first-run-language.component.scss'],
})
export class FirstRunLanguageComponent {
  private readonly language = inject(LanguageService);

  visible = !this.language.hasExplicitChoice();

  /** Libellés NATIFs (indépendants de la langue courante). */
  readonly options: ReadonlyArray<{ code: string; native: string; flag: string }> = [
    { code: 'fr', native: 'Français', flag: '🇫🇷' },
    { code: 'en', native: 'English', flag: '🇬🇧' },
  ];

  choose(code: string): void {
    this.language.use(code); // applique + mémorise
    this.visible = false;
  }
}
