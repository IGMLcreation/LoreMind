import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule, Sparkles, RefreshCw, Check, ChevronDown, ChevronRight } from 'lucide-angular';
import { SceneDraftService } from '../../services/scene-draft.service';
import { SceneDraft } from '../../services/scene-draft.model';

interface ReviewDraft extends SceneDraft {
  accepted: boolean;
}

/**
 * Panneau « Générer des scènes » (Pilier A — capacité « create »). Le co-MJ ébauche
 * plusieurs scènes pour un chapitre ; l'utilisateur révise/coche, puis les scènes retenues
 * sont CRÉÉES dans le chapitre (contrairement à l'étoffage de champs, l'application persiste).
 *
 * <p>Émet {@code created} avec le nombre de scènes créées pour que le parent puisse
 * naviguer (ex. vers le graphe du chapitre).</p>
 */
@Component({
  selector: 'app-scene-draft-panel',
  standalone: true,
  imports: [FormsModule, TranslatePipe, LucideAngularModule],
  templateUrl: './scene-draft-panel.component.html',
  styleUrls: ['./scene-draft-panel.component.scss']
})
export class SceneDraftPanelComponent implements OnChanges {

  @Input() chapterId = '';
  @Input() campaignId = '';
  /** Déplie le panneau à l'arrivée (bouton « Corriger » du guidage sur un chapitre vide). */
  @Input() startOpen = false;
  /** Nombre de scènes créées, pour que le parent réagisse (navigation/refresh). */
  @Output() created = new EventEmitter<number>();

  open = false;
  instruction = '';
  count = 4;
  loading = false;
  applying = false;
  generated = false;
  error = '';
  drafts: ReviewDraft[] = [];

  readonly countOptions = [3, 4, 5, 6];

  readonly Sparkles = Sparkles;
  readonly RefreshCw = RefreshCw;
  readonly Check = Check;
  readonly ChevronDown = ChevronDown;
  readonly ChevronRight = ChevronRight;

  constructor(private draftService: SceneDraftService, private translate: TranslateService) {}

  /** Repart d'un état vierge si le chapitre ciblé change (composant réutilisé). */
  ngOnChanges(changes: SimpleChanges): void {
    if (changes['startOpen'] && this.startOpen) {
      this.open = true;
    }
    if (changes['chapterId'] && !changes['chapterId'].firstChange) {
      this.open = false;
      this.instruction = '';
      this.loading = false;
      this.applying = false;
      this.generated = false;
      this.error = '';
      this.drafts = [];
    }
  }

  toggle(): void {
    this.open = !this.open;
  }

  generate(): void {
    if (!this.chapterId || this.loading) return;
    this.loading = true;
    this.error = '';
    this.generated = false;
    this.drafts = [];
    this.draftService.generate(this.chapterId, this.campaignId, this.instruction.trim(), this.count).subscribe({
      next: proposal => {
        this.drafts = (proposal.scenes ?? []).map(s => ({ ...s, accepted: true }));
        this.generated = true;
        this.loading = false;
      },
      error: () => {
        this.error = this.translate.instant('sceneDraft.error');
        this.loading = false;
      }
    });
  }

  get acceptedCount(): number {
    return this.drafts.filter(d => d.accepted).length;
  }

  apply(): void {
    const accepted: SceneDraft[] = this.drafts
      .filter(d => d.accepted)
      .map(d => ({ name: d.name, description: d.description, playerNarration: d.playerNarration }));
    if (accepted.length === 0 || this.applying) return;
    this.applying = true;
    this.error = '';
    this.draftService.apply(this.chapterId, { chapterId: this.chapterId, scenes: accepted }).subscribe({
      next: createdScenes => {
        this.applying = false;
        this.drafts = [];
        this.generated = false;
        this.open = false;
        this.created.emit(createdScenes.length);
      },
      error: () => {
        this.applying = false;
        this.error = this.translate.instant('sceneDraft.applyError');
      }
    });
  }
}
