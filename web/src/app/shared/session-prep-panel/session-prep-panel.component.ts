import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule, CalendarCheck, Compass, MapPin, AlertTriangle, Timer, CheckCircle2, ArrowRight, Play, Check, RotateCcw } from 'lucide-angular';
import { SessionPrepService } from '../../services/session-prep.service';
import { QuestService } from '../../services/quest.service';
import { SessionPrepReport, PrepClock, PrepNode, PrepQuest } from '../../services/session-prep.model';
import { ReadinessGap } from '../../services/readiness.model';
import { gapAction } from '../../campaigns/gap-action.helper';

/**
 * Panneau « Préparer la prochaine séance » (Phase 3 co-MJ) sur la page d'une Partie.
 * Croise la position des joueurs (quêtes actives, dernière séance), le contenu probable,
 * les manques du guidage CIBLÉS sur ce contenu, et les horloges en mouvement.
 * Read-only : chaque élément deep-linke vers l'éditeur / la fiche concernée.
 */
@Component({
  selector: 'app-session-prep-panel',
  standalone: true,
  imports: [RouterLink, TranslatePipe, LucideAngularModule],
  templateUrl: './session-prep-panel.component.html',
  styleUrls: ['./session-prep-panel.component.scss']
})
export class SessionPrepPanelComponent implements OnChanges {

  @Input() playthroughId = '';
  @Input() campaignId = '';

  report: SessionPrepReport | null = null;
  loading = false;

  readonly CalendarCheck = CalendarCheck;
  readonly Compass = Compass;
  readonly MapPin = MapPin;
  readonly AlertTriangle = AlertTriangle;
  readonly Timer = Timer;
  readonly CheckCircle2 = CheckCircle2;
  readonly ArrowRight = ArrowRight;
  readonly Play = Play;
  readonly Check = Check;
  readonly RotateCcw = RotateCcw;

  constructor(private prepService: SessionPrepService,
              private questService: QuestService) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['playthroughId'] && this.playthroughId) {
      this.load();
    }
  }

  private load(): void {
    this.loading = true;
    this.report = null;
    this.prepService.getPrep(this.playthroughId).subscribe({
      next: r => { this.report = r; this.loading = false; },
      error: () => { this.loading = false; }
    });
  }

  /** Rien à signaler : ni quête active, ni manque ciblé, ni horloge en mouvement. */
  get allQuiet(): boolean {
    const r = this.report;
    return !!r && r.questsInProgress.length === 0 && r.questsAvailable.length === 0
      && r.gaps.length === 0 && r.clocks.length === 0;
  }

  /** Lien profond vers un nœud probable (chapitre ou scène). */
  nodeLink(node: PrepNode): string[] {
    const base = ['/campaigns', this.campaignId, 'arcs', node.arcId ?? ''];
    return node.nodeType === 'SCENE'
      ? [...base, 'chapters', node.chapterId ?? '', 'scenes', node.id]
      : [...base, 'chapters', node.id];
  }

  /** Lien profond « réparateur » — mapping partagé avec le panneau readiness du hub. */
  gapLink(gap: ReadinessGap): string[] {
    return gapAction(gap, this.campaignId).link;
  }

  gapQuery(gap: ReadinessGap): Record<string, string> | null {
    return gapAction(gap, this.campaignId).queryParams ?? null;
  }

  // ─────────────── Progression des quêtes (pilotée depuis les chips) ───────────────
  // C'est ICI que le MJ tient à jour « où en sont les joueurs » : Disponible → ▶ En
  // cours → ✓ Terminée (elle disparaît des pistes), ↺ pour rouvrir en cas d'erreur.

  private setProgression(quest: PrepQuest, status: 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED'): void {
    this.questService.setProgression(this.playthroughId, quest.id, status).subscribe({
      next: () => this.load(),   // recharge le rapport : listes + manques ciblés à jour
      error: () => console.error('Erreur lors de la mise à jour de la progression')
    });
  }

  startQuest(quest: PrepQuest): void { this.setProgression(quest, 'IN_PROGRESS'); }
  completeQuest(quest: PrepQuest): void { this.setProgression(quest, 'COMPLETED'); }
  reopenQuest(quest: PrepQuest): void { this.setProgression(quest, 'NOT_STARTED'); }

  isNearlyFull(clock: PrepClock): boolean {
    return clock.segments - clock.filled === 1;
  }

  isFull(clock: PrepClock): boolean {
    return clock.filled >= clock.segments;
  }
}
