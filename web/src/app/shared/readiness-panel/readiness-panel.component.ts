import { Component, Input, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule, ChevronDown, ChevronRight, CheckCircle2, AlertTriangle, ArrowRight } from 'lucide-angular';
import { ReadinessService } from '../../services/readiness.service';
import { CampaignReadinessAssessment, ReadinessGap } from '../../services/readiness.model';
import { gapAction } from '../../campaigns/gap-action.helper';

/**
 * Panneau « Prochaines étapes » (Pilier B — guidage co-MJ). Affiche le statut de
 * préparation agrégé d'une campagne et la liste des manques à combler, chacun
 * cliquable vers l'éditeur concerné. Purement indicatif (ne bloque rien).
 *
 * <p>Peut recevoir un {@link CampaignReadinessAssessment} déjà chargé via
 * {@code [assessment]} (évite un second appel quand le parent l'a déjà) ; sinon
 * il le charge lui-même à partir de {@code [campaignId]}.</p>
 */
@Component({
  selector: 'app-readiness-panel',
  standalone: true,
  imports: [RouterLink, TranslatePipe, LucideAngularModule],
  templateUrl: './readiness-panel.component.html',
  styleUrls: ['./readiness-panel.component.scss']
})
export class ReadinessPanelComponent implements OnInit {

  @Input() campaignId = '';
  @Input() assessment: CampaignReadinessAssessment | null = null;

  loading = false;
  collapsed = false;

  readonly ChevronDown = ChevronDown;
  readonly ChevronRight = ChevronRight;
  readonly CheckCircle2 = CheckCircle2;
  readonly AlertTriangle = AlertTriangle;
  readonly ArrowRight = ArrowRight;

  private static readonly STORAGE_KEY = 'loremind.readiness.collapsed';

  constructor(private readinessService: ReadinessService) {}

  ngOnInit(): void {
    this.collapsed = localStorage.getItem(ReadinessPanelComponent.STORAGE_KEY) === '1';
    if (!this.assessment && this.campaignId) {
      this.fetch();
    }
  }

  private fetch(): void {
    this.loading = true;
    this.readinessService.getReadiness(this.campaignId).subscribe({
      next: a => { this.assessment = a; this.loading = false; },
      error: () => { this.loading = false; }
    });
  }

  toggle(): void {
    this.collapsed = !this.collapsed;
    localStorage.setItem(ReadinessPanelComponent.STORAGE_KEY, this.collapsed ? '1' : '0');
  }

  get blockingCount(): number {
    return this.assessment?.counts?.['BLOCKING'] ?? 0;
  }

  get recommendedCount(): number {
    return this.assessment?.counts?.['RECOMMENDED'] ?? 0;
  }

  get isReady(): boolean {
    return !!this.assessment && this.assessment.gaps.length === 0;
  }

  /** Lien profond « réparateur » : route + query params (outil auto-ouvert sur la cible). */
  gapLink(gap: ReadinessGap): string[] {
    return gapAction(gap, this.campaignId).link;
  }

  gapQuery(gap: ReadinessGap): Record<string, string> | null {
    return gapAction(gap, this.campaignId).queryParams ?? null;
  }
}
