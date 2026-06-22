import { Injectable, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

/**
 * Langue affichée par l'interface.
 *
 * Source de vérité = ngx-translate (TranslateService). Ce service ajoute :
 *  - la liste des langues proposées dans le sélecteur (LANGUAGES) ;
 *  - la persistance du choix dans localStorage (par appareil) ;
 *  - la résolution de la langue initiale au démarrage (localStorage → langue
 *    du navigateur → repli français).
 *
 * Le changement est appliqué à chaud : translate.use() recharge le bon JSON et
 * rafraîchit tous les pipes `translate` — aucun rechargement de page.
 */
export interface AppLanguage {
  /** Code ISO court utilisé par les fichiers i18n (fr.json, en.json). */
  code: string;
  /** Clé i18n du libellé natif affiché dans le sélecteur. */
  labelKey: string;
  /** Drapeau emoji pour un repère visuel léger. */
  flag: string;
}

/**
 * Clé localStorage du choix de langue (par appareil). Exportée pour que le
 * languageInterceptor lise la langue SANS injecter LanguageService/TranslateService
 * (sinon dépendance circulaire au démarrage : le chargement initial de la langue
 * de repli passe par l'intercepteur pendant la construction de TranslateService).
 */
export const STORAGE_KEY = 'loremind.lang';

@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly translate = inject(TranslateService);

  /** Langues proposées dans l'application. Ajouter une langue = une ligne ici + un JSON. */
  readonly languages: readonly AppLanguage[] = [
    { code: 'fr', labelKey: 'language.fr', flag: '🇫🇷' },
    { code: 'en', labelKey: 'language.en', flag: '🇬🇧' },
  ];

  readonly defaultLang = 'fr';

  /**
   * Appelé une fois au démarrage (APP_INITIALIZER) : enregistre les langues
   * connues, fixe le repli et applique la langue résolue avant le premier rendu.
   */
  init(): Promise<unknown> {
    const codes = this.languages.map((l) => l.code);
    this.translate.addLangs(codes);
    this.translate.setFallbackLang(this.defaultLang);

    const lang = this.resolveInitialLang();
    // use() renvoie un Observable qui émet quand le JSON est chargé : on
    // l'attend pour éviter un flash de clés non traduites au boot.
    return new Promise((resolve) => {
      this.translate.use(lang).subscribe({
        next: () => resolve(true),
        error: () => resolve(false),
      });
    });
  }

  /** Code de la langue active. */
  get current(): string {
    return this.translate.getCurrentLang() || this.defaultLang;
  }

  /** Change la langue à chaud et mémorise le choix. */
  use(code: string): void {
    if (!this.languages.some((l) => l.code === code)) {
      return;
    }
    this.translate.use(code);
    this.persist(code);
  }

  private resolveInitialLang(): string {
    const stored = this.read();
    if (stored && this.languages.some((l) => l.code === stored)) {
      return stored;
    }
    const browser = this.translate.getBrowserLang();
    if (browser && this.languages.some((l) => l.code === browser)) {
      return browser;
    }
    return this.defaultLang;
  }

  /**
   * Vrai si une langue a déjà été choisie explicitement (mémorisée en localStorage).
   * Faux au tout premier lancement → on propose alors l'écran de choix de langue.
   */
  hasExplicitChoice(): boolean {
    return this.read() !== null;
  }

  private read(): string | null {
    try {
      return localStorage.getItem(STORAGE_KEY);
    } catch {
      return null;
    }
  }

  private persist(code: string): void {
    try {
      localStorage.setItem(STORAGE_KEY, code);
    } catch {
      // localStorage indisponible (mode privé strict) : la langue reste valable
      // pour la session courante, simplement non mémorisée.
    }
  }
}
