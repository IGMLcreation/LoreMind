import { Component, Input, OnChanges, SimpleChanges, inject } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule, ChevronDown, ChevronRight, Lightbulb } from 'lucide-angular';

import { WritingReviewService } from '../../services/writing-review.service';
import {
  WRITING_REMARK_CATEGORIES, WritingRemark, WritingRemarkCategory, WritingReviewReport
} from '../../services/writing-review.model';
import { WritingRemarkCardComponent } from './writing-remark-card.component';

interface RemarkGroup {
  category: WritingRemarkCategory;
  remarks: WritingRemark[];
}

/**
 * Panneau « Relecture d'éditeur » du hub campagne (Mentor d'écriture, phase M2) :
 * le pendant « qualité » de « Prochaines étapes ». Conseils regroupés par famille,
 * chacun avec son lien vers l'éditeur concerné. Purement indicatif.
 */
@Component({
  selector: 'app-writing-review-panel',
  imports: [TranslatePipe, LucideAngularModule, WritingRemarkCardComponent],
  templateUrl: './writing-review-panel.component.html',
  styleUrls: ['./writing-review-panel.component.scss']
})
export class WritingReviewPanelComponent implements OnChanges {
  readonly ChevronDown = ChevronDown;
  readonly ChevronRight = ChevronRight;
  readonly Lightbulb = Lightbulb;

  /** Remarques visibles par famille avant « voir les autres ». */
  readonly perGroup = 3;

  private static readonly STORAGE_KEY = 'loremind.writingReview.collapsed';
  private readonly reviewService = inject(WritingReviewService);

  @Input({ required: true }) campaignId = '';

  report: WritingReviewReport | null = null;
  groups: RemarkGroup[] = [];
  expanded = new Set<WritingRemarkCategory>();
  collapsed = readCollapsed();

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['campaignId'] && this.campaignId) {
      this.reviewService.getReview(this.campaignId).subscribe({
        next: report => {
          this.report = report;
          this.groups = WRITING_REMARK_CATEGORIES
            .map(category => ({ category, remarks: report.remarks.filter(r => r.category === category) }))
            .filter(g => g.remarks.length > 0);
        },
        // Conseil facultatif : en cas d'erreur, le panneau ne s'affiche simplement pas.
        error: () => { this.report = null; }
      });
    }
  }

  get total(): number {
    return this.report?.remarks.length ?? 0;
  }

  toggle(): void {
    this.collapsed = !this.collapsed;
    try {
      localStorage.setItem(WritingReviewPanelComponent.STORAGE_KEY, this.collapsed ? '1' : '0');
    } catch { /* stockage indisponible : préférence non mémorisée */ }
  }

  visible(group: RemarkGroup): WritingRemark[] {
    return this.expanded.has(group.category) ? group.remarks : group.remarks.slice(0, this.perGroup);
  }

  showAll(category: WritingRemarkCategory): void {
    this.expanded.add(category);
  }
}

function readCollapsed(): boolean {
  try {
    return localStorage.getItem('loremind.writingReview.collapsed') === '1';
  } catch {
    return false;
  }
}
