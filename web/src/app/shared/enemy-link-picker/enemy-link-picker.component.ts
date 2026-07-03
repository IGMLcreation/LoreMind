import { Component, Input, Output, EventEmitter } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { LucideAngularModule, X, Skull, AlertTriangle } from 'lucide-angular';
import { TranslatePipe } from '@ngx-translate/core';
import { Enemy } from '../../services/enemy.model';

/**
 * Composant pour lier une scène (ou toute entité) à des fiches du bestiaire
 * par leurs IDs. Pendant côté ennemis du LoreLinkPickerComponent.
 *
 * Usage :
 *   <app-enemy-link-picker
 *     [value]="enemyIds"
 *     [availableEnemies]="enemies"
 *     [campaignId]="campaignId"
 *     (valueChange)="enemyIds = $event"></app-enemy-link-picker>
 *
 * Design :
 *  - Fiches liées = chips cliquables (clic → fiche ennemi en nouvel onglet)
 *  - Input de recherche avec dropdown de suggestions filtrées (max 8 résultats)
 */
@Component({
    selector: 'app-enemy-link-picker',
    imports: [FormsModule, LucideAngularModule, TranslatePipe],
    templateUrl: './enemy-link-picker.component.html',
    styleUrls: ['./enemy-link-picker.component.scss']
})
export class EnemyLinkPickerComponent {
  readonly X = X;
  readonly Skull = Skull;
  readonly AlertTriangle = AlertTriangle;

  /** IDs des ennemis actuellement liés (contrôlés par le parent). */
  @Input() value: string[] = [];
  /** Bestiaire de la campagne dans lequel on peut piocher. */
  @Input() availableEnemies: Enemy[] = [];
  /** ID de la campagne, pour construire les URLs des chips cliquables. */
  @Input() campaignId = '';

  @Output() valueChange = new EventEmitter<string[]>();

  /** Texte de recherche courant. */
  query = '';
  /** true tant que l'input a le focus (pour afficher le dropdown). */
  dropdownOpen = false;

  /**
   * Ennemis liés groupés AVEC leur quantité (le même id peut apparaître N fois
   * dans `value` → N tokens à l'export). Ordre = première apparition.
   */
  get linkedGroups(): { enemy: Enemy; count: number }[] {
    const counts = new Map<string, number>();
    const order: string[] = [];
    for (const id of this.value) {
      if (!counts.has(id)) order.push(id);
      counts.set(id, (counts.get(id) ?? 0) + 1);
    }
    return order
      .map(id => ({ enemy: this.availableEnemies.find(e => e.id === id), count: counts.get(id)! }))
      .filter((g): g is { enemy: Enemy; count: number } => !!g.enemy);
  }

  /**
   * IDs liés dont la fiche est INTROUVABLE dans le bestiaire de la campagne (fiche
   * supprimée — typiquement un ennemi supprimé puis recréé sous un nouvel id). Le picker
   * les cachait silencieusement ; on les EXPOSE (chip « introuvable ») pour que
   * l'utilisateur puisse les retirer — sinon la référence morte reste invisible mais
   * persistée, et le guidage (readiness) la signale sans que rien ne soit actionnable.
   */
  get brokenGroups(): { id: string; count: number }[] {
    const counts = new Map<string, number>();
    const order: string[] = [];
    for (const id of this.value) {
      if (!id) continue;
      if (this.availableEnemies.some(e => e.id === id)) continue; // résolu → pas cassé
      if (!counts.has(id)) order.push(id);
      counts.set(id, (counts.get(id) ?? 0) + 1);
    }
    return order.map(id => ({ id, count: counts.get(id)! }));
  }

  /** Ennemis proposables dans le dropdown — filtrés par query, exclut les déjà liés. */
  get suggestions(): Enemy[] {
    const q = this.query.trim().toLowerCase();
    return this.availableEnemies
      .filter(e => !this.value.includes(e.id!))
      .filter(e => q === '' || e.name.toLowerCase().includes(q))
      .slice(0, 8);
  }

  /** Libellé d'une suggestion / chip : nom + niveau s'il est renseigné. */
  label(enemy: Enemy): string {
    const level = enemy.level?.trim();
    return level ? `${enemy.name} (${level})` : enemy.name;
  }

  /** Ajoute un ennemi aux liens (première occurrence). */
  add(enemy: Enemy): void {
    if (!enemy.id) return;
    this.valueChange.emit([...this.value, enemy.id]);
    this.query = '';
  }

  /** +1 du même ennemi (un token de plus à l'export). */
  increment(enemyId: string): void {
    this.valueChange.emit([...this.value, enemyId]);
  }

  /** −1 du même ennemi (retire UNE occurrence). */
  decrement(enemyId: string): void {
    const i = this.value.indexOf(enemyId);
    if (i < 0) return;
    const next = [...this.value];
    next.splice(i, 1);
    this.valueChange.emit(next);
  }

  /** Retire TOUTES les occurrences de cet ennemi. */
  remove(enemyId: string): void {
    this.valueChange.emit(this.value.filter(id => id !== enemyId));
  }

  /**
   * URL vers la fiche ennemi — utilisée par un <a target="_blank"> dans le
   * template : on consulte la fiche sans perdre la scène en cours d'édition.
   */
  enemyUrl(enemyId: string): string {
    return `/campaigns/${this.campaignId}/enemies/${enemyId}`;
  }

  /** Retarde la fermeture du dropdown pour laisser le temps au clic de se propager. */
  onBlur(): void {
    setTimeout(() => { this.dropdownOpen = false; }, 150);
  }
}
