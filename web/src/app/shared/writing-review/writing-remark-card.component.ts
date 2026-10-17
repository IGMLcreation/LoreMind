import { Component, Input, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule, ArrowRight } from 'lucide-angular';

import { WritingRemark } from '../../services/writing-review.model';
import { remarkAction } from '../../campaigns/writing-remark-action.helper';
import { GapAction } from '../../campaigns/gap-action.helper';

/**
 * Une remarque de relecture, en posture « mentor » : ce que j'observe, pourquoi
 * ça compte, puis une question pour faire réfléchir (jamais de réécriture).
 * Le texte vient de l'i18n `writingReview.rules.<ruleId>` ; le back fournit les paramètres.
 */
@Component({
  selector: 'app-writing-remark-card',
  imports: [RouterLink, TranslatePipe, LucideAngularModule],
  template: `
    <article class="remark">
      <p class="remark-observation">{{ key('observation') | translate: textParams }}</p>
      <p class="remark-why">{{ key('why') | translate: textParams }}</p>
      <p class="remark-question">{{ key('question') | translate: textParams }}</p>
      @if (showLink) {
        <a class="remark-link" [routerLink]="action.link" [queryParams]="action.queryParams ?? null">
          {{ 'writingReview.see' | translate }}
          <lucide-icon [img]="ArrowRight" [size]="13"></lucide-icon>
        </a>
      }
    </article>
  `,
  styles: [`
    .remark {
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
      padding: 0.8rem 0.95rem;
      border-radius: 8px;
      background: #111827;
      border: 1px solid #1f2937;
      border-left: 3px solid #fbbf24;
    }
    p { margin: 0; line-height: 1.5; }
    .remark-observation { font-size: 0.88rem; font-weight: 600; color: #f3f4f6; }
    .remark-why { font-size: 0.84rem; color: #9ca3af; }
    .remark-question { font-size: 0.86rem; font-style: italic; color: #fcd34d; }
    .remark-link {
      align-self: flex-start;
      display: inline-flex;
      align-items: center;
      gap: 0.3rem;
      margin-top: 0.2rem;
      font-size: 0.8rem;
      font-weight: 600;
      color: #a5b4fc;
      text-decoration: none;
    }
    .remark-link:hover { color: #c7d2fe; text-decoration: underline; }
    .remark-link:focus-visible { outline: 2px solid #6c63ff; outline-offset: 2px; border-radius: 4px; }
  `]
})
export class WritingRemarkCardComponent {
  readonly ArrowRight = ArrowRight;
  private readonly translate = inject(TranslateService);

  @Input({ required: true }) remark!: WritingRemark;
  @Input() campaignId = '';
  /** Lien vers l'éditeur concerné (inutile quand on est déjà dans cet éditeur). */
  @Input() showLink = true;

  key(part: 'observation' | 'why' | 'question'): string {
    return `writingReview.rules.${this.remark.ruleId}.${part}`;
  }

  get action(): GapAction {
    return remarkAction(this.remark, this.campaignId);
  }

  /** Paramètres du texte : ceux du back + le nom de l'entité + le genre de scène traduit. */
  get textParams(): Record<string, string> {
    const params: Record<string, string> = { ...this.remark.params, entity: this.remark.entityName ?? '' };
    const kind = this.remark.params['kind'];
    if (kind) {
      params['kind'] = this.translate.instant(kind === 'COMBAT' ? 'writingReview.kindCombat' : `sceneType.${kind}`);
    }
    return params;
  }
}
