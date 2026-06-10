import { Component, EventEmitter, Input, Output } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { LucideAngularModule, Dices, BookmarkPlus, Trash2 } from 'lucide-angular';

/** Faces de dés supportées par le roller. */
const DICE_FACES = [4, 6, 8, 10, 12, 20, 100] as const;
type DiceFace = typeof DICE_FACES[number];

/** Résultat d'un jet, exposé au parent pour ajout au journal. */
export interface DiceRollResult {
  /** Notation lisible, ex: "2d6+3". */
  notation: string;
  /** Détail des dés individuels. */
  rolls: number[];
  modifier: number;
  total: number;
  /** Formatage textuel prêt à être écrit dans le journal. */
  summary: string;
}

/**
 * Panneau de jet de dés pour une session.
 * Composant isolé : choix face/quantité/modificateur, jet, historique local court,
 * et émission d'un événement vers le parent pour ajout au journal.
 */
@Component({
    selector: 'app-session-dice-panel',
    imports: [FormsModule, LucideAngularModule],
    templateUrl: './session-dice-panel.component.html',
    styleUrls: ['./session-dice-panel.component.scss']
})
export class SessionDicePanelComponent {
  readonly Dices = Dices;
  readonly BookmarkPlus = BookmarkPlus;
  readonly Trash2 = Trash2;

  readonly faces: readonly DiceFace[] = DICE_FACES;

  /** Désactive le bouton "Ajouter au journal" si la session est terminée. */
  @Input() canAddToJournal = true;

  @Output() rolled = new EventEmitter<DiceRollResult>();

  selectedFace: DiceFace = 20;
  count = 1;
  modifier = 0;

  /** Historique local (max 8 entrées) pour permettre de retrouver un jet récent. */
  history: DiceRollResult[] = [];

  roll(): void {
    const safeCount = Math.max(1, Math.min(20, Math.floor(this.count)));
    const rolls: number[] = [];
    for (let i = 0; i < safeCount; i++) {
      rolls.push(this.randomFace(this.selectedFace));
    }
    const sumRolls = rolls.reduce((s, n) => s + n, 0);
    const total = sumRolls + this.modifier;
    const modPart = this.modifier === 0 ? '' : (this.modifier > 0 ? `+${this.modifier}` : `${this.modifier}`);
    const notation = `${safeCount}d${this.selectedFace}${modPart}`;
    const detailsPart = rolls.length > 1 ? ` [${rolls.join(', ')}]` : '';
    const summary = `🎲 ${notation}${detailsPart} = ${total}`;

    const result: DiceRollResult = { notation, rolls, modifier: this.modifier, total, summary };
    this.history = [result, ...this.history].slice(0, 8);
  }

  /** Émet vers le parent pour qu'il insère le jet comme entrée DICE_ROLL. */
  addToJournal(result: DiceRollResult): void {
    if (!this.canAddToJournal) return;
    this.rolled.emit(result);
  }

  clearHistory(): void {
    this.history = [];
  }

  /**
   * crypto.getRandomValues si dispo, fallback Math.random sinon.
   * Pas critique pour du JDR mais évite le biais Math.random sur les très petites distributions.
   */
  private randomFace(face: DiceFace): number {
    if (typeof crypto !== 'undefined' && crypto.getRandomValues) {
      const buf = new Uint32Array(1);
      crypto.getRandomValues(buf);
      return (buf[0] % face) + 1;
    }
    return Math.floor(Math.random() * face) + 1;
  }
}
