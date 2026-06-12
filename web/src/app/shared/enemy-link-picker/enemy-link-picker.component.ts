import { Component, Input, Output, EventEmitter } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { LucideAngularModule, X, Skull } from 'lucide-angular';
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
    imports: [FormsModule, LucideAngularModule],
    templateUrl: './enemy-link-picker.component.html',
    styleUrls: ['./enemy-link-picker.component.scss']
})
export class EnemyLinkPickerComponent {
  readonly X = X;
  readonly Skull = Skull;

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

  /** Ennemis actuellement liés (résolus en objets complets pour affichage). */
  get linkedEnemies(): Enemy[] {
    return this.value
      .map(id => this.availableEnemies.find(e => e.id === id))
      .filter((e): e is Enemy => !!e);
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

  /** Ajoute un ennemi aux liens. */
  add(enemy: Enemy): void {
    if (!enemy.id || this.value.includes(enemy.id)) return;
    this.valueChange.emit([...this.value, enemy.id]);
    this.query = '';
  }

  /** Retire un ennemi des liens. */
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
