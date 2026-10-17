import { Component, Input, OnChanges, SimpleChanges, inject } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule, ChevronDown, ChevronRight, Lightbulb } from 'lucide-angular';

import { ReadinessEntityType } from '../../services/readiness.model';
import { WritingRemark } from '../../services/writing-review.model';
import { WritingReviewService } from '../../services/writing-review.service';
import { WritingRemarkCardComponent } from './writing-remark-card.component';

/**
 * Encart « Conseils de relecture » d'un éditeur (scène / chapitre / arc) : les remarques
 * de la relecture d'éditeur qui concernent l'entité ouverte. Invisible s'il n'y en a pas.
 *
 * La relecture porte sur la version ENREGISTRÉE : après une sauvegarde, l'encart se met
 * à jour au prochain chargement de l'éditeur.
 */
@Component({
  selector: 'app-writing-review-hints',
  imports: [TranslatePipe, LucideAngularModule, WritingRemarkCardComponent],
  template: `
    @if (remarks.length > 0) {
      <section class="hints">
        <button type="button" class="hints-header" (click)="open = !open" [attr.aria-expanded]="open">
          <lucide-icon [img]="open ? ChevronDown : ChevronRight" [size]="15"></lucide-icon>
          <lucide-icon [img]="Lightbulb" [size]="15" class="hints-icon"></lucide-icon>
          <span>{{ 'writingReview.hintsTitle' | translate: { n: remarks.length } }}</span>
        </button>
        @if (open) {
          <div class="hints-list">
            @for (remark of remarks; track remark.ruleId) {
              <app-writing-remark-card [remark]="remark" [campaignId]="campaignId" [showLink]="false">
              </app-writing-remark-card>
            }
          </div>
        }
      </section>
    }
  `,
  styles: [`
    .hints {
      background: rgba(251, 191, 36, 0.04);
      border: 1px solid rgba(251, 191, 36, 0.25);
      border-radius: 8px;
    }
    .hints-header {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      width: 100%;
      padding: 0.7rem 0.95rem;
      background: transparent;
      border: none;
      border-radius: 8px;
      color: #fde68a;
      font-size: 0.88rem;
      font-weight: 600;
      cursor: pointer;
      text-align: left;
    }
    .hints-header:hover { background: rgba(251, 191, 36, 0.06); }
    .hints-header:focus-visible { outline: 2px solid #6c63ff; outline-offset: -2px; }
    .hints-icon { color: #fbbf24; }
    .hints-list {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      padding: 0 0.95rem 0.95rem;
    }
  `]
})
export class WritingReviewHintsComponent implements OnChanges {
  readonly ChevronDown = ChevronDown;
  readonly ChevronRight = ChevronRight;
  readonly Lightbulb = Lightbulb;

  private readonly reviewService = inject(WritingReviewService);

  @Input({ required: true }) campaignId = '';
  @Input({ required: true }) entityType: ReadinessEntityType = 'SCENE';
  @Input({ required: true }) entityId = '';

  remarks: WritingRemark[] = [];
  open = true;

  ngOnChanges(changes: SimpleChanges): void {
    if (!this.campaignId || !this.entityId) return;
    if (changes['campaignId'] || changes['entityId'] || changes['entityType']) {
      this.remarks = [];
      const { entityType, entityId } = this;
      this.reviewService.getReview(this.campaignId).subscribe({
        next: report => {
          this.remarks = report.remarks.filter(r => r.entityType === entityType && r.entityId === entityId);
        },
        error: () => { this.remarks = []; }
      });
    }
  }
}
