import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule, Sparkles, RefreshCw, Check, X, ChevronDown, ChevronRight } from 'lucide-angular';
import { EntityAssistService } from '../../services/entity-assist.service';
import { FieldProposal } from '../../services/entity-assist.model';

interface ReviewField extends FieldProposal {
  accepted: boolean;
}

/**
 * Panneau « Étoffer avec l'IA » (Pilier A — co-création), générique par type d'entité
 * narrative ({@code arc|chapter|scene}). Demande au co-MJ des propositions de valeurs pour
 * les champs de l'entité, les affiche en diff (actuel → proposé) avec accept/reject champ
 * par champ, et ÉMET les champs retenus.
 *
 * <p>Ne persiste rien : le parent (arc/chapter/scene-edit) applique les champs retenus au
 * FORMULAIRE ; l'utilisateur enregistre ensuite normalement (human-in-the-loop).</p>
 */
@Component({
  selector: 'app-entity-assist-panel',
  standalone: true,
  imports: [FormsModule, TranslatePipe, LucideAngularModule],
  templateUrl: './entity-assist-panel.component.html',
  styleUrls: ['./entity-assist-panel.component.scss']
})
export class EntityAssistPanelComponent implements OnChanges {

  /** Type d'entité : 'arc' | 'chapter' | 'scene'. */
  @Input() entityType = 'scene';
  @Input() entityId = '';
  @Input() campaignId = '';
  /** Émet les champs ACCEPTÉS ; le parent les applique où il veut (formulaire, /apply…). */
  @Output() applied = new EventEmitter<FieldProposal[]>();

  open = false;
  instruction = '';
  loading = false;
  generated = false;
  error = '';
  fields: ReviewField[] = [];

  readonly Sparkles = Sparkles;
  readonly RefreshCw = RefreshCw;
  readonly Check = Check;
  readonly X = X;
  readonly ChevronDown = ChevronDown;
  readonly ChevronRight = ChevronRight;

  constructor(private assist: EntityAssistService, private translate: TranslateService) {}

  /**
   * L'écran d'édition RÉUTILISE ce composant quand on navigue entre entités sœurs
   * (même route pattern → pas de recréation). On repart donc d'un état vierge dès que
   * l'entité ciblée change, sinon on afficherait les propositions de l'entité précédente.
   */
  ngOnChanges(changes: SimpleChanges): void {
    if (changes['entityId'] && !changes['entityId'].firstChange) {
      this.open = false;
      this.instruction = '';
      this.loading = false;
      this.generated = false;
      this.error = '';
      this.fields = [];
    }
  }

  toggle(): void {
    this.open = !this.open;
  }

  generate(): void {
    if (!this.entityId || this.loading) return;
    this.loading = true;
    this.error = '';
    this.generated = false;
    this.fields = [];
    this.assist.generateFields(this.entityType, this.entityId, this.campaignId, this.instruction.trim() || undefined).subscribe({
      next: proposal => {
        this.fields = (proposal.fields ?? []).map(f => ({ ...f, accepted: true }));
        this.generated = true;
        this.loading = false;
      },
      error: () => {
        this.error = this.translate.instant('entityAssist.error');
        this.loading = false;
      }
    });
  }

  get acceptedCount(): number {
    return this.fields.filter(f => f.accepted).length;
  }

  apply(): void {
    const accepted: FieldProposal[] = this.fields
      .filter(f => f.accepted)
      .map(f => ({ key: f.key, currentValue: f.currentValue, proposedValue: f.proposedValue }));
    if (accepted.length > 0) {
      this.applied.emit(accepted);
    }
    this.fields = [];
    this.generated = false;
    this.open = false;
  }

  /** Libellé lisible d'une clé de champ (fallback sur la clé brute si non traduite). */
  fieldLabel(key: string): string {
    const label = this.translate.instant('entityAssist.field.' + key);
    return label === 'entityAssist.field.' + key ? key : label;
  }
}
