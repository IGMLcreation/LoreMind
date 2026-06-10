import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideAngularModule, Lock, Circle, Play, CheckCircle2, LucideIconData } from 'lucide-angular';
import { QuestStatus } from '../../services/campaign.model';

/**
 * Badge visuel pour un QuestStatus (vue Hub).
 * Composant standalone, sans dépendance métier.
 */
@Component({
    selector: 'app-quest-status-badge',
    imports: [CommonModule, LucideAngularModule],
    templateUrl: './quest-status-badge.component.html',
    styleUrls: ['./quest-status-badge.component.scss']
})
export class QuestStatusBadgeComponent {
  @Input() status: QuestStatus | undefined | null = 'AVAILABLE';

  /** Variante visuelle compacte (sans label) — utile pour les listes denses. */
  @Input() compact = false;

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
      case 'LOCKED':      return 'Verrouillée';
      case 'IN_PROGRESS': return 'En cours';
      case 'COMPLETED':   return 'Terminée';
      case 'AVAILABLE':
      default:            return 'Disponible';
    }
  }

  get cssClass(): string {
    return `status-badge status-${(this.status ?? 'AVAILABLE').toLowerCase()}`;
  }
}
