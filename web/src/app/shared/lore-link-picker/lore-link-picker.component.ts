import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, X, Link2 } from 'lucide-angular';
import { Page } from '../../services/page.model';

/**
 * Composant pour lier une entité (Page, Arc, Chapter, Scene...) à un ensemble
 * de Pages du Lore par leurs IDs.
 *
 * Prévu pour être réutilisé en Phase cross-context Campagne↔Lore (sous-tâche 4) :
 * un Arc/Chapter/Scene pourra pointer vers des Pages du Lore via ce même picker.
 *
 * Usage :
 *   <app-lore-link-picker
 *     [value]="relatedPageIds"
 *     [availablePages]="allPages"
 *     [excludePageId]="currentPageId"
 *     [loreId]="loreId"
 *     (valueChange)="relatedPageIds = $event"></app-lore-link-picker>
 *
 * Design :
 *  - Liste des pages liées = chips cliquables (clic → navigation vers la page)
 *  - Input de recherche avec dropdown de suggestions filtrées (max 8 résultats)
 *  - Exclut la page courante et les pages déjà sélectionnées
 */
@Component({
  selector: 'app-lore-link-picker',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideAngularModule],
  templateUrl: './lore-link-picker.component.html',
  styleUrls: ['./lore-link-picker.component.scss']
})
export class LoreLinkPickerComponent {
  readonly X = X;
  readonly Link2 = Link2;

  /** IDs des pages actuellement liées (contrôlées par le parent). */
  @Input() value: string[] = [];
  /** Référentiel de pages dans lequel on peut piocher. */
  @Input() availablePages: Page[] = [];
  /** ID de la page courante (exclue des suggestions) — optionnel. */
  @Input() excludePageId: string | null = null;
  /** ID du lore courant, utilisé pour construire les URLs des chips cliquables. */
  @Input() loreId = '';

  @Output() valueChange = new EventEmitter<string[]>();

  /** Texte de recherche courant. */
  query = '';
  /** true tant que l'input a le focus (pour afficher le dropdown). */
  dropdownOpen = false;

  /** Pages actuellement liées (résolues en objets complets pour affichage). */
  get linkedPages(): Page[] {
    return this.value
      .map(id => this.availablePages.find(p => p.id === id))
      .filter((p): p is Page => !!p);
  }

  /** Pages proposables dans le dropdown — filtrées par query, exclut current + déjà liées. */
  get suggestions(): Page[] {
    const q = this.query.trim().toLowerCase();
    return this.availablePages
      .filter(p => p.id !== this.excludePageId)
      .filter(p => !this.value.includes(p.id!))
      .filter(p => q === '' || p.title.toLowerCase().includes(q))
      .slice(0, 8);
  }

  /** Ajoute une page aux liens. */
  add(page: Page): void {
    if (!page.id || this.value.includes(page.id)) return;
    this.valueChange.emit([...this.value, page.id]);
    this.query = '';
  }

  /** Retire une page des liens. */
  remove(pageId: string): void {
    this.valueChange.emit(this.value.filter(id => id !== pageId));
  }

  /**
   * URL vers la page liée — utilisée par un <a target="_blank"> dans le template.
   * Ouverture en nouvel onglet : on consulte la fiche d'un PNJ/lieu sans perdre
   * le contexte de la page/scène en cours d'édition.
   */
  pageUrl(pageId: string): string {
    return `/lore/${this.loreId}/pages/${pageId}`;
  }

  /** Retarde la fermeture du dropdown pour laisser le temps au clic de se propager. */
  onBlur(): void {
    setTimeout(() => { this.dropdownOpen = false; }, 150);
  }
}
