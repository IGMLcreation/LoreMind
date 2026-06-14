import { Component, OnInit } from '@angular/core';

import { ActivatedRoute, Router } from '@angular/router';
import { LucideAngularModule, ArrowLeft, Edit3, Dices } from 'lucide-angular';
import { TranslatePipe } from '@ngx-translate/core';
import { RandomTableService } from '../../../services/random-table.service';
import { CampaignSidebarService } from '../../../services/campaign-sidebar.service';
import { RandomTable, RandomTableEntry } from '../../../services/random-table.model';
import { DiceUtils, DiceRoll } from '../../../shared/dice.utils';

/**
 * Vue d'une table aléatoire : affiche les entrées et permet de LANCER le dé
 * (jet côté client) → surligne l'entrée tombée + montre son détail.
 * Route : /campaigns/:campaignId/random-tables/:tableId
 */
@Component({
    selector: 'app-random-table-view',
    imports: [LucideAngularModule, TranslatePipe],
    templateUrl: './random-table-view.component.html',
    styleUrls: ['./random-table-view.component.scss']
})
export class RandomTableViewComponent implements OnInit {
  readonly ArrowLeft = ArrowLeft;
  readonly Edit3 = Edit3;
  readonly Dices = Dices;

  campaignId: string | null = null;
  tableId: string | null = null;
  table: RandomTable | null = null;

  lastRoll: DiceRoll | null = null;
  matched: RandomTableEntry | null = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private service: RandomTableService,
    private campaignSidebar: CampaignSidebarService
  ) {}

  ngOnInit(): void {
    const params = this.route.snapshot.paramMap;
    this.campaignId = params.get('campaignId');
    this.tableId = params.get('tableId');
    if (this.tableId) {
      this.service.getById(this.tableId).subscribe({
        next: t => this.table = t,
        error: () => this.back()
      });
    }
    if (this.campaignId) {
      this.campaignSidebar.show(this.campaignId);
    }
  }

  roll(): void {
    if (!this.table) return;
    const result = DiceUtils.roll(this.table.diceFormula);
    if (!result) { this.lastRoll = null; this.matched = null; return; }
    this.lastRoll = result;
    this.matched = this.table.entries.find(
      e => result.total >= e.minRoll && result.total <= e.maxRoll
    ) ?? null;
  }

  isMatched(entry: RandomTableEntry): boolean {
    return this.matched === entry;
  }

  rangeLabel(entry: RandomTableEntry): string {
    return entry.minRoll === entry.maxRoll
      ? String(entry.minRoll)
      : `${entry.minRoll}–${entry.maxRoll}`;
  }

  edit(): void {
    if (this.campaignId && this.tableId) {
      this.router.navigate(['/campaigns', this.campaignId, 'random-tables', this.tableId, 'edit']);
    }
  }

  back(): void {
    this.router.navigate(this.campaignId ? ['/campaigns', this.campaignId] : ['/campaigns']);
  }
}
