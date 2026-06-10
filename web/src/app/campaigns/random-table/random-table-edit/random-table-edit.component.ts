import { Component, OnInit } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { LucideAngularModule, ArrowLeft, Save, Plus, Trash2, Wand2, Sparkles } from 'lucide-angular';
import { RandomTableService } from '../../../services/random-table.service';
import { CampaignSidebarService } from '../../../services/campaign-sidebar.service';
import { RandomTable, RandomTableEntry, RandomTableCreate } from '../../../services/random-table.model';
import { DiceUtils } from '../../../shared/dice.utils';

/**
 * Création/édition d'une table aléatoire (formule de dé + entrées par plage).
 * Routes : /campaigns/:campaignId/random-tables/create
 *          /campaigns/:campaignId/random-tables/:tableId/edit
 */
@Component({
    selector: 'app-random-table-edit',
    imports: [FormsModule, LucideAngularModule],
    templateUrl: './random-table-edit.component.html',
    styleUrls: ['./random-table-edit.component.scss']
})
export class RandomTableEditComponent implements OnInit {
  readonly ArrowLeft = ArrowLeft;
  readonly Save = Save;
  readonly Plus = Plus;
  readonly Trash2 = Trash2;
  readonly Wand2 = Wand2;
  readonly Sparkles = Sparkles;

  campaignId: string | null = null;
  tableId: string | null = null;

  name = '';
  description = '';
  diceFormula = '1d20';
  entries: RandomTableEntry[] = [];

  saving = false;
  errorMessage = '';

  // --- Génération IA ---
  aiPrompt = '';
  generating = false;
  aiError = '';

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
    if (this.campaignId) this.campaignSidebar.show(this.campaignId);

    if (this.tableId) {
      this.service.getById(this.tableId).subscribe({
        next: (t: RandomTable) => {
          this.name = t.name;
          this.description = t.description ?? '';
          this.diceFormula = t.diceFormula;
          this.entries = t.entries.map(e => ({ ...e }));
        },
        error: () => this.back()
      });
    } else {
      // Démarre avec une entrée vide pour guider.
      this.addEntry();
    }
  }

  get formulaValid(): boolean {
    return DiceUtils.parse(this.diceFormula) !== null;
  }

  addEntry(): void {
    const range = DiceUtils.totalRange(this.diceFormula);
    const next = this.entries.length ? this.entries[this.entries.length - 1].maxRoll + 1 : (range?.min ?? 1);
    this.entries.push({ minRoll: next, maxRoll: next, label: '', detail: '' });
  }

  removeEntry(i: number): void {
    this.entries.splice(i, 1);
  }

  /** Répartit équitablement la plage du dé sur toutes les entrées. */
  autoRanges(): void {
    const range = DiceUtils.totalRange(this.diceFormula);
    if (!range || this.entries.length === 0) return;
    const span = range.max - range.min + 1;
    const n = this.entries.length;
    const base = Math.floor(span / n);
    let cursor = range.min;
    this.entries.forEach((e, idx) => {
      const size = idx === n - 1 ? (range.max - cursor + 1) : Math.max(1, base);
      e.minRoll = cursor;
      e.maxRoll = Math.min(range.max, cursor + size - 1);
      cursor = e.maxRoll + 1;
    });
  }

  /** Génère la table via l'IA et préremplit le formulaire (l'utilisateur révise avant d'enregistrer). */
  generateWithAI(): void {
    if (!this.campaignId) return;
    if (!this.aiPrompt.trim()) { this.aiError = 'Décris la table à générer.'; return; }
    if (!this.formulaValid) { this.aiError = 'Choisis d\'abord une formule de dé valide.'; return; }
    this.generating = true;
    this.aiError = '';
    this.service.generate(this.campaignId, this.aiPrompt.trim(), this.diceFormula.trim()).subscribe({
      next: (t) => {
        this.generating = false;
        if (t.name && !this.name.trim()) this.name = t.name;
        if (t.description) this.description = t.description;
        this.entries = (t.entries ?? []).map(e => ({ ...e }));
      },
      error: (err) => {
        this.generating = false;
        this.aiError = err?.error?.message || 'Échec de la génération IA. Réessaie ou reformule.';
      }
    });
  }

  save(): void {
    if (!this.campaignId) return;
    if (!this.name.trim()) { this.errorMessage = 'Le nom est requis.'; return; }
    if (!this.formulaValid) { this.errorMessage = 'Formule de dé invalide (ex. 1d20, 2d6, d100).'; return; }
    this.saving = true;
    this.errorMessage = '';

    const cleanEntries = this.entries
      .filter(e => e.label.trim())
      .map(e => ({
        minRoll: e.minRoll,
        maxRoll: Math.max(e.minRoll, e.maxRoll),
        label: e.label.trim(),
        detail: e.detail?.trim() || undefined
      }));

    if (this.tableId) {
      const payload: RandomTable = {
        id: this.tableId,
        name: this.name.trim(),
        description: this.description.trim() || undefined,
        diceFormula: this.diceFormula.trim(),
        campaignId: this.campaignId,
        entries: cleanEntries
      };
      this.service.update(this.tableId, payload).subscribe({
        next: () => this.goToView(this.tableId!),
        error: (e) => this.fail(e)
      });
    } else {
      const payload: RandomTableCreate = {
        name: this.name.trim(),
        description: this.description.trim() || undefined,
        diceFormula: this.diceFormula.trim(),
        campaignId: this.campaignId,
        entries: cleanEntries
      };
      this.service.create(payload).subscribe({
        next: (t) => this.goToView(t.id!),
        error: (e) => this.fail(e)
      });
    }
  }

  private goToView(id: string): void {
    this.saving = false;
    this.router.navigate(['/campaigns', this.campaignId, 'random-tables', id]);
  }

  private fail(err: unknown): void {
    this.saving = false;
    this.errorMessage = 'Échec de l\'enregistrement.';
    console.error('RandomTable save failed', err);
  }

  back(): void {
    this.router.navigate(this.campaignId ? ['/campaigns', this.campaignId] : ['/campaigns']);
  }
}
