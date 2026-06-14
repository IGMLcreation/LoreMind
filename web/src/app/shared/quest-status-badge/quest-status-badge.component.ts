import { Component, Input } from '@angular/core';

import { LucideAngularModule, Lock, Circle, Play, CheckCircle2, LucideIconData } from 'lucide-angular';
import { TranslateService } from '@ngx-translate/core';
import { QuestStatus } from '../../services/campaign.model';

/**
 * Badge visuel pour un QuestStatus (vue Hub).
 * Composant standalone, sans dépendance métier.
 */
@Component({
    selector: 'app-quest-status-badge',
    imports: [LucideAngularModule],
    templateUrl: './quest-status-badge.component.html',
    styleUrls: ['./quest-status-badge.component.scss']
})
export class QuestStatusBadgeComponent {
  @Input() status: QuestStatus | undefined | null = 'AVAILABLE';

  /** Variante visuelle compacte (sans label) — utile pour les listes denses. */
  @Input() compact = false;

  constructor(private translate: TranslateService) {}

  get icon(): LucideIconData {
    switch (this.status) {
      case 'LOCKED':      return Lock;
      case 'IN_PROGRESS': return Play;
      case 'COMPLETED':   return CheckCircle2;
      case 'AVAILABLE':
      default:            return Circle;
    }
  }

  get label(): string {
    switch (this.status) {
      case 'LOCKED':      return this.translate.instant('questStatusBadge.locked');
      case 'IN_PROGRESS': return this.translate.instant('questStatusBadge.inProgress');
      case 'COMPLETED':   return this.translate.instant('questStatusBadge.completed');
      case 'AVAILABLE':
      default:            return this.translate.instant('questStatusBadge.available');
    }
  }

  get cssClass(): string {
    return `status-badge status-${(this.status ?? 'AVAILABLE').toLowerCase()}`;
  }
}
