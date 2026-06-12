import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { LucideAngularModule, Plus, Check, Drama, Clapperboard, BookText, GitBranch, Dices, ExternalLink } from 'lucide-angular';
import { CampaignService } from '../../../services/campaign.service';
import { NpcService } from '../../../services/npc.service';
import { RandomTableService } from '../../../services/random-table.service';
import { Arc, Chapter } from '../../../services/campaign.model';
import { NotebookAction } from '../../../services/notebook-action.model';

/**
 * Carte « Créer dans la campagne » issue d'une proposition de l'IA. Gère la cible
 * (chapitre pour une scène, arc pour un chapitre) et appelle les services existants.
 */
@Component({
    selector: 'app-notebook-action-card',
    imports: [FormsModule, LucideAngularModule],
    templateUrl: './notebook-action-card.component.html',
    styleUrls: ['./notebook-action-card.component.scss']
})
export class NotebookActionCardComponent implements OnInit {
  readonly Plus = Plus;
  readonly Check = Check;
  readonly ExternalLink = ExternalLink;

  @Input() action!: NotebookAction;
  @Input() campaignId!: string;
  @Input() arcs: Arc[] = [];
  @Input() chaptersByArc: Record<string, Chapter[]> = {};
  /** Émis après une création réussie → l'atelier rafraîchit la sidebar. */
  @Output() created = new EventEmitter<void>();

  selectedArcId = '';
  selectedChapterId = '';

  status: 'idle' | 'creating' | 'created' | 'error' = 'idle';
  errorMsg = '';
  createdRoute: string[] | null = null;

  constructor(
    private campaignService: CampaignService,
    private npcService: NpcService,
    private tableService: RandomTableService,
    private router: Router
  ) {}

  ngOnInit(): void {
    if (this.arcs.length) this.selectedArcId = this.arcs[0].id!;
    this.syncChapter();
  }

  get typeLabel(): string {
    switch (this.action.type) {
      case 'npc': return 'PNJ';
      case 'scene': return 'Scène';
      case 'chapter': return 'Chapitre';
      case 'arc': return 'Arc';
      case 'table': return 'Table aléatoire';
      default: return this.action.type;
    }
  }

  get typeIcon() {
    switch (this.action.type) {
      case 'npc': return Drama;
      case 'scene': return Clapperboard;
      case 'chapter': return BookText;
      case 'arc': return GitBranch;
      case 'table': return Dices;
      default: return Plus;
    }
  }

  get needsArc(): boolean { return this.action.type === 'chapter' || this.action.type === 'scene'; }
  get needsChapter(): boolean { return this.action.type === 'scene'; }

  get targetChapters(): Chapter[] { return this.chaptersByArc[this.selectedArcId] ?? []; }

  syncChapter(): void {
    const chs = this.targetChapters;
    this.selectedChapterId = chs.length ? chs[0].id! : '';
  }

  get canCreate(): boolean {
    if (this.status === 'creating' || this.status === 'created') return false;
    if (this.needsArc && !this.selectedArcId) return false;
    if (this.needsChapter && !this.selectedChapterId) return false;
    return true;
  }

  create(): void {
    if (!this.canCreate) return;
    this.status = 'creating';
    this.errorMsg = '';
    switch (this.action.type) {
      case 'npc':     return this.createNpc();
      case 'arc':     return this.createArc();
      case 'chapter': return this.createChapter();
      case 'scene':   return this.createScene();
      case 'table':   return this.createTable();
    }
  }

  private createNpc(): void {
    // Valeurs de fiche proposées par l'IA (clés = champs du template PNJ) ;
    // la description sert de repli si le modèle n'a pas détaillé par champ.
    const values: Record<string, string> = {};
    for (const [key, val] of Object.entries(this.action.values ?? {})) {
      if (typeof val === 'string' && val.trim()) values[key] = val;
    }
    if (Object.keys(values).length === 0 && this.action.description) {
      values['Description'] = this.action.description;
    }
    this.npcService.create({
      name: this.action.name,
      campaignId: this.campaignId,
      values
    }).subscribe({
      next: (n) => this.done(['/campaigns', this.campaignId, 'npcs', n.id!]),
      error: (e) => this.fail(e)
    });
  }

  private createArc(): void {
    this.campaignService.createArc({
      name: this.action.name,
      description: this.action.description,
      campaignId: this.campaignId,
      order: this.arcs.length,
      type: this.action.arcType === 'HUB' ? 'HUB' : 'LINEAR',
      themes: this.action.themes,
      stakes: this.action.stakes,
      rewards: this.action.rewards,
      resolution: this.action.resolution,
      gmNotes: this.action.gmNotes
    }).subscribe({
      next: (a) => this.done(['/campaigns', this.campaignId, 'arcs', a.id!]),
      error: (e) => this.fail(e)
    });
  }

  private createChapter(): void {
    const order = (this.chaptersByArc[this.selectedArcId] ?? []).length;
    this.campaignService.createChapter({
      name: this.action.name,
      description: this.action.description,
      arcId: this.selectedArcId,
      order,
      playerObjectives: this.action.playerObjectives,
      narrativeStakes: this.action.narrativeStakes,
      gmNotes: this.action.gmNotes
    }).subscribe({
      next: (c) => this.done(['/campaigns', this.campaignId, 'arcs', this.selectedArcId, 'chapters', c.id!]),
      error: (e) => this.fail(e)
    });
  }

  private createScene(): void {
    this.campaignService.createScene({
      name: this.action.name,
      description: this.action.description,
      // `content` = ancien protocole (messages archivés d'avant l'enrichissement).
      playerNarration: this.action.playerNarration ?? this.action.content,
      chapterId: this.selectedChapterId,
      order: 0,
      location: this.action.location,
      timing: this.action.timing,
      atmosphere: this.action.atmosphere,
      gmSecretNotes: this.action.gmSecretNotes,
      choicesConsequences: this.action.choicesConsequences,
      combatDifficulty: this.action.combatDifficulty,
      enemies: this.action.enemies
    }).subscribe({
      next: (s) => this.done(
        ['/campaigns', this.campaignId, 'arcs', this.selectedArcId, 'chapters', this.selectedChapterId, 'scenes', s.id!]),
      error: (e) => this.fail(e)
    });
  }

  private createTable(): void {
    this.tableService.create({
      name: this.action.name,
      diceFormula: this.action.diceFormula || '1d20',
      campaignId: this.campaignId,
      entries: (this.action.entries ?? []).map(e => ({
        minRoll: e.minRoll, maxRoll: e.maxRoll, label: e.label, detail: e.detail
      }))
    }).subscribe({
      next: (t) => this.done(['/campaigns', this.campaignId, 'random-tables', t.id!]),
      error: (e) => this.fail(e)
    });
  }

  private done(route: string[]): void {
    this.status = 'created';
    this.createdRoute = route;
    this.created.emit();
  }

  private fail(err: unknown): void {
    this.status = 'error';
    this.errorMsg = (err as { error?: { message?: string } })?.error?.message || 'Échec de la création.';
  }

  openCreated(): void {
    if (this.createdRoute) this.router.navigate(this.createdRoute);
  }
}
