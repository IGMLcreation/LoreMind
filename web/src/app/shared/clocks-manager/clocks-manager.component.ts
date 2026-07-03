import { Component, Input, OnInit, OnChanges, SimpleChanges } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { LucideAngularModule, Plus, Minus, Trash2, Zap, Pencil, Check, X } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ClockService } from '../../services/clock.service';
import { Clock, ClockTrigger } from '../../services/clock.model';
import { FrontService } from '../../services/front.service';
import { Front } from '../../services/front.model';
import { QuestService } from '../../services/quest.service';
import { Quest } from '../../services/campaign.model';
import { CampaignFlagService } from '../../services/campaign-flag.service';

/** Un groupe affiché : un Front (ou null = horloges libres) et ses horloges. */
interface ClockGroup { front: Front | null; clocks: Clock[]; }

/**
 * Gestionnaire des Horloges de progression (Clocks) d'une Partie, regroupées par
 * **Front** (menace) : visuel à segments, +/− (avancer/reculer), déclencheur auto
 * (co-MJ), création/suppression d'horloges ET de fronts. Auto-chargé via
 * {@code playthroughId} ; {@code campaignId} (optionnel) alimente les déclencheurs.
 */
@Component({
    selector: 'app-clocks-manager',
    imports: [FormsModule, LucideAngularModule, TranslatePipe],
    templateUrl: './clocks-manager.component.html',
    styleUrls: ['./clocks-manager.component.scss']
})
export class ClocksManagerComponent implements OnInit, OnChanges {

  @Input() playthroughId!: string;
  @Input() campaignId = '';

  readonly Plus = Plus;
  readonly Minus = Minus;
  readonly Trash2 = Trash2;
  readonly Zap = Zap;
  readonly Pencil = Pencil;
  readonly Check = Check;
  readonly X = X;
  readonly segmentOptions = [2, 4, 6, 8, 10, 12];
  readonly triggerTypeOptions: ClockTrigger[] = ['NONE', 'FLAG_SET', 'QUEST_COMPLETED', 'SESSION_ENDED'];

  clocks: Clock[] = [];
  fronts: Front[] = [];
  quests: Quest[] = [];
  referencedFlags: string[] = [];
  loading = false;

  // Formulaire de création d'horloge.
  newName = '';
  newSegments = 4;
  newTriggerType: ClockTrigger = 'NONE';
  newTriggerRef = '';
  newClockFront = '';

  // Formulaire de création de front.
  newFrontName = '';

  // Édition in-place d'une horloge.
  editingId: string | null = null;
  editName = '';
  editSegments = 4;
  editTriggerType: ClockTrigger = 'NONE';
  editTriggerRef = '';
  editFront = '';

  constructor(
    private clockService: ClockService,
    private frontService: FrontService,
    private questService: QuestService,
    private campaignFlagService: CampaignFlagService,
    private translate: TranslateService
  ) {}

  ngOnInit(): void { this.reload(); }
  ngOnChanges(changes: SimpleChanges): void {
    if (changes['playthroughId'] || changes['campaignId']) this.reload();
  }

  reload(): void {
    if (!this.playthroughId) { this.clocks = []; this.fronts = []; return; }
    this.loading = true;
    forkJoin({
      clocks: this.clockService.list(this.playthroughId),
      fronts: this.frontService.list(this.playthroughId).pipe(catchError(() => of([] as Front[]))),
      quests: this.campaignId
        ? this.questService.getByCampaign(this.campaignId).pipe(catchError(() => of([] as Quest[])))
        : of([] as Quest[]),
      flags: this.campaignId
        ? this.campaignFlagService.listReferenced(this.campaignId).pipe(catchError(() => of([] as string[])))
        : of([] as string[])
    }).subscribe({
      next: ({ clocks, fronts, quests, flags }) => {
        this.clocks = clocks;
        this.fronts = fronts;
        this.quests = quests;
        this.referencedFlags = flags;
        this.loading = false;
      },
      error: () => { this.clocks = []; this.fronts = []; this.loading = false; }
    });
  }

  /** Groupes affichés : un par Front (avec ses horloges) + un groupe « libres » si besoin. */
  get groups(): ClockGroup[] {
    const groups: ClockGroup[] = this.fronts.map(f => ({
      front: f,
      clocks: this.clocks.filter(c => c.frontId === f.id)
    }));
    const ungrouped = this.clocks.filter(c => !c.frontId);
    if (ungrouped.length > 0) groups.push({ front: null, clocks: ungrouped });
    return groups;
  }

  frontProgressLabel(clocks: Clock[]): string {
    const filled = clocks.reduce((s, c) => s + c.filled, 0);
    const total = clocks.reduce((s, c) => s + c.segments, 0);
    return `${filled}/${total}`;
  }

  // ── Fronts ──────────────────────────────────────────────────────────────
  createFront(): void {
    const name = this.newFrontName.trim();
    if (!name) return;
    this.frontService.create(this.playthroughId, { name }).subscribe({
      next: f => { this.fronts = [...this.fronts, f]; this.newFrontName = ''; }
    });
  }

  removeFront(front: Front): void {
    this.frontService.delete(this.playthroughId, front.id).subscribe({
      next: () => {
        this.fronts = this.fronts.filter(f => f.id !== front.id);
        // Le backend orpheline les horloges (frontId -> null) : on reflète localement.
        this.clocks = this.clocks.map(c => c.frontId === front.id ? { ...c, frontId: undefined } : c);
      }
    });
  }

  // ── Horloges ────────────────────────────────────────────────────────────
  create(): void {
    const name = this.newName.trim();
    if (!name) return;
    const needsRef = this.newTriggerType === 'FLAG_SET' || this.newTriggerType === 'QUEST_COMPLETED';
    const ref = needsRef ? (this.newTriggerRef.trim() || undefined) : undefined;
    this.clockService.create(this.playthroughId, {
      name, segments: this.newSegments, triggerType: this.newTriggerType,
      triggerRef: ref, frontId: this.newClockFront || undefined
    }).subscribe({
      next: c => {
        this.clocks = [...this.clocks, c];
        this.newName = '';
        this.newSegments = 4;
        this.newTriggerType = 'NONE';
        this.newTriggerRef = '';
        this.newClockFront = '';
      }
    });
  }

  advance(clock: Clock): void {
    this.clockService.advance(this.playthroughId, clock.id).subscribe({ next: u => this.replace(u) });
  }

  regress(clock: Clock): void {
    this.clockService.regress(this.playthroughId, clock.id).subscribe({ next: u => this.replace(u) });
  }

  remove(clock: Clock): void {
    this.clockService.delete(this.playthroughId, clock.id).subscribe({
      next: () => this.clocks = this.clocks.filter(c => c.id !== clock.id)
    });
  }

  startEdit(clock: Clock): void {
    this.editingId = clock.id;
    this.editName = clock.name;
    this.editSegments = clock.segments;
    this.editTriggerType = clock.triggerType ?? 'NONE';
    this.editTriggerRef = clock.triggerRef ?? '';
    this.editFront = clock.frontId ?? '';
  }

  cancelEdit(): void { this.editingId = null; }

  saveEdit(clock: Clock): void {
    const name = this.editName.trim();
    if (!name) return;
    const needsRef = this.editTriggerType === 'FLAG_SET' || this.editTriggerType === 'QUEST_COMPLETED';
    const ref = needsRef ? (this.editTriggerRef.trim() || undefined) : undefined;
    this.clockService.update(this.playthroughId, clock.id, {
      name, segments: this.editSegments, triggerType: this.editTriggerType,
      triggerRef: ref, frontId: this.editFront || undefined
    }).subscribe({
      next: u => { this.replace(u); this.editingId = null; }
    });
  }

  private replace(updated: Clock): void {
    this.clocks = this.clocks.map(c => c.id === updated.id ? updated : c);
  }

  /** Libellé lisible du déclencheur auto (badge), ou null si aucun. */
  triggerLabel(clock: Clock): string | null {
    switch (clock.triggerType) {
      case 'FLAG_SET':
        return this.translate.instant('clocksManager.triggerFlag', { name: clock.triggerRef });
      case 'QUEST_COMPLETED':
        return this.translate.instant('clocksManager.triggerQuest', { name: this.questName(clock.triggerRef) });
      case 'SESSION_ENDED':
        return this.translate.instant('clocksManager.triggerSession');
      default:
        return null;
    }
  }

  private questName(id?: string): string {
    return this.quests.find(q => q.id === id)?.name ?? this.translate.instant('clocksManager.deletedQuest');
  }

  /**
   * Arcs SVG des segments (anneau) d'une horloge : un arc par segment, rempli ou non.
   * Évite le wedge plein (point central) → pas de cas dégénéré pour 1 segment.
   */
  segments(clock: Clock): { d: string; filled: boolean }[] {
    const n = Math.max(1, clock.segments);
    const cx = 32, cy = 32, r = 26;
    const gap = n > 1 ? 0.12 : 0.0001; // léger espace entre segments (radians)
    const out: { d: string; filled: boolean }[] = [];
    for (let i = 0; i < n; i++) {
      const a0 = (i / n) * 2 * Math.PI - Math.PI / 2 + gap / 2;
      const a1 = ((i + 1) / n) * 2 * Math.PI - Math.PI / 2 - gap / 2;
      const x0 = cx + r * Math.cos(a0), y0 = cy + r * Math.sin(a0);
      const x1 = cx + r * Math.cos(a1), y1 = cy + r * Math.sin(a1);
      const large = (a1 - a0) > Math.PI ? 1 : 0;
      out.push({
        d: `M ${x0.toFixed(2)} ${y0.toFixed(2)} A ${r} ${r} 0 ${large} 1 ${x1.toFixed(2)} ${y1.toFixed(2)}`,
        filled: i < clock.filled
      });
    }
    return out;
  }
}
