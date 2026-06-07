import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideAngularModule, Dices, BookmarkPlus, Sparkles, ChevronLeft } from 'lucide-angular';
import { catchError, of } from 'rxjs';
import { RandomTableService } from '../../services/random-table.service';
import { RandomTable, RandomTableEntry } from '../../services/random-table.model';
import { DiceUtils, DiceRoll } from '../../shared/dice.utils';
import { DiceRollResult } from '../session-dice-panel/session-dice-panel.component';

/**
 * Panneau "Tables aléatoires" du mode jeu : choisir une table de la campagne,
 * la JETER, consigner le résultat au journal, et (option) demander à l'IA
 * d'improviser un court récit sur le tirage.
 *
 * Réutilise les sorties existantes du panneau de référence :
 *  - `rolled` (DiceRollResult) → entrée DICE_ROLL dans le journal ;
 *  - `aiReplyToJournal` (string) → entrée NOTE (récit IA).
 */
@Component({
  selector: 'app-session-random-tables-panel',
  standalone: true,
  imports: [CommonModule, LucideAngularModule],
  templateUrl: './session-random-tables-panel.component.html',
  styleUrls: ['./session-random-tables-panel.component.scss']
})
export class SessionRandomTablesPanelComponent implements OnInit {
  readonly Dices = Dices;
  readonly BookmarkPlus = BookmarkPlus;
  readonly Sparkles = Sparkles;
  readonly ChevronLeft = ChevronLeft;

  @Input() campaignId!: string;
  @Input() canAddToJournal = true;
  @Output() rolled = new EventEmitter<DiceRollResult>();
  @Output() aiReplyToJournal = new EventEmitter<string>();

  tables: RandomTable[] = [];
  loading = false;

  selected: RandomTable | null = null;
  lastRoll: DiceRoll | null = null;
  matched: RandomTableEntry | null = null;
  improvising = false;

  constructor(private service: RandomTableService) {}

  ngOnInit(): void {
    if (!this.campaignId) return;
    this.loading = true;
    this.service.getByCampaign(this.campaignId).pipe(catchError(() => of([] as RandomTable[])))
      .subscribe(list => { this.tables = list; this.loading = false; });
  }

  select(table: RandomTable): void {
    this.selected = table;
    this.lastRoll = null;
    this.matched = null;
  }

  backToList(): void {
    this.selected = null;
    this.lastRoll = null;
    this.matched = null;
  }

  roll(): void {
    if (!this.selected) return;
    const result = DiceUtils.roll(this.selected.diceFormula);
    if (!result) { this.lastRoll = null; this.matched = null; return; }
    this.lastRoll = result;
    this.matched = this.selected.entries.find(
      e => result.total >= e.minRoll && result.total <= e.maxRoll
    ) ?? null;
  }

  rangeLabel(entry: RandomTableEntry): string {
    return entry.minRoll === entry.maxRoll ? String(entry.minRoll) : `${entry.minRoll}–${entry.maxRoll}`;
  }

  /** Consigne le tirage au journal (entrée DICE_ROLL via la sortie `rolled`). */
  addToJournal(): void {
    if (!this.canAddToJournal || !this.selected || !this.lastRoll) return;
    const label = this.matched?.label ?? 'aucun résultat';
    const result: DiceRollResult = {
      notation: this.selected.diceFormula,
      rolls: this.lastRoll.rolls,
      modifier: 0,
      total: this.lastRoll.total,
      summary: `🎲 ${this.selected.name} : ${this.lastRoll.total} → ${label}`
    };
    this.rolled.emit(result);
  }

  /** Demande à l'IA un court récit sur le résultat, puis l'envoie au journal (NOTE). */
  improvise(): void {
    if (!this.selected || !this.matched || this.improvising) return;
    this.improvising = true;
    this.service.improvise(
      this.campaignId, this.selected.name, this.matched.label, this.matched.detail ?? ''
    ).subscribe({
      next: (r) => {
        this.improvising = false;
        if (r.narration?.trim() && this.canAddToJournal) {
          this.aiReplyToJournal.emit(r.narration.trim());
        }
      },
      error: () => { this.improvising = false; }
    });
  }
}
