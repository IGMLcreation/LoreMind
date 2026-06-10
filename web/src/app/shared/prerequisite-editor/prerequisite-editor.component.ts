import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, Plus, Trash2, ChevronDown } from 'lucide-angular';
import { Chapter, Prerequisite } from '../../services/campaign.model';

/**
 * Éditeur des prérequis (conditions de déblocage) d'une quête.
 * Composant standalone & contrôlé : reçoit la liste, émet la nouvelle liste à chaque changement.
 *
 * Combinaison ET (MVP). UI : liste de lignes + bouton "Ajouter une condition" qui ouvre
 * un menu à 3 entrées (quête, session, fait).
 */
@Component({
    selector: 'app-prerequisite-editor',
    imports: [CommonModule, FormsModule, LucideAngularModule],
    templateUrl: './prerequisite-editor.component.html',
    styleUrls: ['./prerequisite-editor.component.scss']
})
export class PrerequisiteEditorComponent {
  /** Liste courante. */
  @Input() prerequisites: Prerequisite[] = [];

  /** Quêtes candidates pour QUEST_COMPLETED (typiquement les chapitres frères du Hub). */
  @Input() availableQuests: Chapter[] = [];

  /** Flags déjà connus de la campagne (autocomplete pour FLAG_SET). */
  @Input() availableFlags: string[] = [];

  /** Émis chaque fois que la liste change (toute édition / ajout / suppression). */
  @Output() prerequisitesChange = new EventEmitter<Prerequisite[]>();

  readonly Plus = Plus;
  readonly Trash2 = Trash2;
  readonly ChevronDown = ChevronDown;

  /** Ouvre/ferme le menu d'ajout. */
  addMenuOpen = false;

  toggleAddMenu(): void { this.addMenuOpen = !this.addMenuOpen; }

  addPrerequisite(kind: Prerequisite['kind']): void {
    let next: Prerequisite;
    switch (kind) {
      case 'QUEST_COMPLETED':
        next = { kind: 'QUEST_COMPLETED', questId: this.availableQuests[0]?.id ?? '' };
        break;
      case 'SESSION_REACHED':
        next = { kind: 'SESSION_REACHED', minSessionNumber: 1 };
        break;
      case 'FLAG_SET':
        next = { kind: 'FLAG_SET', flagName: this.availableFlags[0] ?? '' };
        break;
    }
    this.prerequisites = [...this.prerequisites, next];
    this.prerequisitesChange.emit(this.prerequisites);
    this.addMenuOpen = false;
  }

  removeAt(index: number): void {
    this.prerequisites = this.prerequisites.filter((_, i) => i !== index);
    this.prerequisitesChange.emit(this.prerequisites);
  }

  /** Le champ binding change : on émet une nouvelle liste (immutable update). */
  updateAt(index: number, patched: Prerequisite): void {
    this.prerequisites = this.prerequisites.map((p, i) => (i === index ? patched : p));
    this.prerequisitesChange.emit(this.prerequisites);
  }

  // === Handlers spécifiques par type (pour garder les templates simples) ===

  onQuestIdChange(index: number, questId: string): void {
    this.updateAt(index, { kind: 'QUEST_COMPLETED', questId });
  }
  onMinSessionChange(index: number, n: number): void {
    this.updateAt(index, { kind: 'SESSION_REACHED', minSessionNumber: Number(n) || 1 });
  }
  onFlagNameChange(index: number, flagName: string): void {
    this.updateAt(index, { kind: 'FLAG_SET', flagName });
  }

  /** trackBy stable pour *ngFor (sinon les inputs reset à chaque édition). */
  trackByIndex(i: number): number { return i; }

  // === Casts typés pour le template ===
  // Angular strict templates ne fait pas le narrowing d'une union discriminée à travers
  // *ngSwitchCase. Ces helpers castent vers la bonne variante pour rendre les inputs lisibles.
  asQuestCompleted(p: Prerequisite): { kind: 'QUEST_COMPLETED'; questId: string } {
    return p as { kind: 'QUEST_COMPLETED'; questId: string };
  }
  asSessionReached(p: Prerequisite): { kind: 'SESSION_REACHED'; minSessionNumber: number } {
    return p as { kind: 'SESSION_REACHED'; minSessionNumber: number };
  }
  asFlagSet(p: Prerequisite): { kind: 'FLAG_SET'; flagName: string } {
    return p as { kind: 'FLAG_SET'; flagName: string };
  }
}
