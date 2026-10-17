import { Injectable } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { NavigationHistoryService } from './navigation-history.service';

/**
 * Service centralisé pour le titre de l'onglet navigateur.
 * Uniformise le format "LoreMind - <sujet>" partout dans l'app.
 *
 * Pourquoi un wrapper et pas Title directement ? Évite de dupliquer le préfixe
 * "LoreMind - " dans chaque écran — si on veut changer le format un jour, un
 * seul endroit à toucher.
 */
@Injectable({ providedIn: 'root' })
export class PageTitleService {
  constructor(private title: Title, private router: Router, private history: NavigationHistoryService) {}

  /**
   * Définit le titre de l'onglet au format "LoreMind - <subject>".
   * Passer `null` (ou vide) remet juste "LoreMind" — utile pour les écrans
   * listing qui n'ont pas de sujet spécifique.
   */
  set(subject: string | null | undefined): void {
    const s = subject?.trim();
    this.title.setTitle(s ? `LoreMind - ${s}` : 'LoreMind');
    if (s) this.history.visit(this.router.url, s);
  }
}
