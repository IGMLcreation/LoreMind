import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import {
  LucideAngularModule, LucideIconData,
  Dices, ArrowLeft, Square, Trash2, Pencil, Check,
  StickyNote, Sparkles, UserCheck, Plus, X,
  Pin, PinOff, ExternalLink, ScrollText, RefreshCw
} from 'lucide-angular';
import { catchError, switchMap, filter, map } from 'rxjs/operators';
import { of } from 'rxjs';
import { SessionService } from '../../services/session.service';
import { Session } from '../../services/session.model';
import { PlaythroughService } from '../../services/playthrough.service';
import { CampaignService } from '../../services/campaign.service';
import { Scene } from '../../services/campaign.model';
import {
  SessionEntry, SessionEntryInput, EntryType, ENTRY_TYPE_META
} from '../../services/session-entry.model';
import { SessionEntryService } from '../../services/session-entry.service';
import { LayoutService } from '../../services/layout.service';
import { PageTitleService } from '../../services/page-title.service';
import { ConfirmDialogService } from '../../shared/confirm-dialog/confirm-dialog.service';
import { SessionReferencePanelComponent } from '../session-reference-panel/session-reference-panel.component';
import { DiceRollResult } from '../session-dice-panel/session-dice-panel.component';

/**
 * Vue détail d'une Session avec journal horodaté.
 * Form de saisie en haut, timeline en dessous (plus récent en premier).
 * Le layout dédié "mode jeu" sera ajouté en Phase 4.
 */
@Component({
    selector: 'app-session-detail',
    imports: [CommonModule, FormsModule, LucideAngularModule, TranslatePipe, RouterLink, SessionReferencePanelComponent],
    templateUrl: './session-detail.component.html',
    styleUrls: ['./session-detail.component.scss']
})
export class SessionDetailComponent implements OnInit, OnDestroy {
  readonly Dices = Dices;
  readonly ArrowLeft = ArrowLeft;
  readonly Square = Square;
  readonly Trash2 = Trash2;
  readonly Pencil = Pencil;
  readonly Check = Check;
  readonly Plus = Plus;
  readonly X = X;
  readonly Pin = Pin;
  readonly PinOff = PinOff;
  readonly ExternalLink = ExternalLink;
  readonly ScrollText = ScrollText;
  readonly RefreshCw = RefreshCw;

  /** Mapping enum → composant Lucide pour le rendu des icônes par type. */
  readonly typeIcons: Record<EntryType, LucideIconData> = {
    NOTE: StickyNote,
    EVENT: Sparkles,
    DICE_ROLL: Dices,
    PLAYER_ACTION: UserCheck,
  };
  readonly entryTypes: EntryType[] = ['NOTE', 'EVENT', 'DICE_ROLL', 'PLAYER_ACTION'];
  readonly entryTypeMeta = ENTRY_TYPE_META;

  session: Session | null = null;
  /** Résolu via Playthrough.campaignId (Session → Playthrough → Campaign). */
  campaignId: string | null = null;
  /** Timeline triée du plus récent au plus ancien (DESC) pour l'UX en partie. */
  entries: SessionEntry[] = [];

  editingName = false;
  editName = '';

  /** State de la zone "Ajouter une entrée". */
  newEntryType: EntryType = 'NOTE';
  newEntryContent = '';
  submittingEntry = false;

  /** Id de l'entrée en cours d'édition (null si aucune). */
  editingEntryId: string | null = null;
  editEntryType: EntryType = 'NOTE';
  editEntryContent = '';

  // ─────────────── Mode cockpit : scène courante épinglée ───────────────
  pinnedScene: Scene | null = null;
  /** Arc parent de la scène épinglée (résolu via son chapitre) — pour le lien d'ouverture. */
  pinnedArcId: string | null = null;
  /** Narration joueur dépliée dans le bandeau. */
  narrationOpen = false;

  // ─────────────── Récap « précédemment… » ───────────────
  recapLoading = false;
  recapText: string | null = null;
  recapFrom: string | null = null;
  recapError: string | null = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private sessionService: SessionService,
    private playthroughService: PlaythroughService,
    private campaignService: CampaignService,
    private entryService: SessionEntryService,
    private layoutService: LayoutService,
    private pageTitleService: PageTitleService,
    private confirmDialog: ConfirmDialogService,
    private translate: TranslateService
  ) {}

  /** Libellé traduit d'un type d'entrée (le modèle partagé reste en FR pour la donnée). */
  typeLabel(type: EntryType): string {
    return this.translate.instant('sessionDetail.entryType.' + type);
  }

  /** Placeholder de la zone de saisie, dépendant du type sélectionné. */
  newEntryPlaceholder(): string {
    return this.translate.instant('sessionDetail.addEntryPlaceholder', {
      type: this.typeLabel(this.newEntryType).toLowerCase()
    });
  }

  ngOnInit(): void {
    this.layoutService.hide();
    this.route.paramMap.pipe(
      map(pm => pm.get('id')),
      filter((id): id is string => !!id),
      switchMap(id => this.sessionService.getSessionById(id).pipe(
        catchError(() => of(null))
      ))
    ).subscribe(session => {
      this.session = session;
      if (session) {
        this.pageTitleService.set(session.name);
        this.loadEntries(session.id);
        this.loadPinnedScene(session.currentSceneId ?? null);
        if (session.playthroughId) {
          this.playthroughService.getById(session.playthroughId).pipe(
            catchError(() => of(null))
          ).subscribe(pt => { this.campaignId = pt ? pt.campaignId : null; });
        }
      }
    });
  }

  private loadEntries(sessionId: string): void {
    this.entryService.getEntries(sessionId).pipe(
      catchError(() => of([] as SessionEntry[]))
    ).subscribe(list => {
      this.entries = list.slice().sort((a, b) => b.occurredAt.localeCompare(a.occurredAt));
    });
  }

  // ─────────────── Mode cockpit : scène courante ───────────────

  /** Charge (ou vide) le bandeau de scène épinglée. Épingle caduque (scène supprimée) → ignorée. */
  private loadPinnedScene(sceneId: string | null): void {
    if (!sceneId) { this.pinnedScene = null; this.pinnedArcId = null; return; }
    this.campaignService.getSceneById(sceneId).pipe(catchError(() => of(null))).subscribe(scene => {
      this.pinnedScene = scene;
      this.narrationOpen = false;
      this.pinnedArcId = null;
      if (scene?.chapterId) {
        this.campaignService.getChapterById(scene.chapterId).pipe(catchError(() => of(null)))
          .subscribe(ch => this.pinnedArcId = ch?.arcId ?? null);
      }
    });
  }

  /** Épingle une scène depuis le panneau de référence (onglet Scènes). */
  onPinScene(sceneId: string): void {
    if (!this.session) return;
    this.sessionService.setCurrentScene(this.session.id, sceneId).subscribe({
      next: updated => { this.session = updated; this.loadPinnedScene(updated.currentSceneId ?? null); },
      error: () => console.error('Erreur lors de l\'épinglage de la scène')
    });
  }

  unpinScene(): void {
    if (!this.session) return;
    this.sessionService.setCurrentScene(this.session.id, null).subscribe({
      next: updated => { this.session = updated; this.pinnedScene = null; this.pinnedArcId = null; },
      error: () => console.error('Erreur lors du retrait de l\'épingle')
    });
  }

  /** Ouvre la scène épinglée dans un nouvel onglet (préserve l'écran de session). */
  openPinnedScene(): void {
    if (!this.pinnedScene || !this.campaignId || !this.pinnedArcId) return;
    const url = ['/campaigns', this.campaignId, 'arcs', this.pinnedArcId,
      'chapters', this.pinnedScene.chapterId, 'scenes', this.pinnedScene.id].join('/');
    window.open(url, '_blank', 'noopener');
  }

  // ─────────────── Récap « précédemment… » ───────────────

  generateRecap(): void {
    if (!this.session || this.recapLoading) return;
    this.recapLoading = true;
    this.recapError = null;
    this.sessionService.recap(this.session.id).subscribe({
      next: r => {
        this.recapLoading = false;
        this.recapText = r.recap;
        this.recapFrom = r.previousSessionName;
      },
      error: err => {
        this.recapLoading = false;
        // Message backend (pas de séance précédente, journal vide, Brain KO…) si disponible.
        this.recapError = err?.error?.error
          ?? this.translate.instant('sessionDetail.recap.error');
      }
    });
  }

  /** Consigne le récap au journal (entrée NOTE 📜) puis referme l'encart. */
  addRecapToJournal(): void {
    if (!this.session || !this.recapText) return;
    const input: SessionEntryInput = { type: 'NOTE', content: '📜 ' + this.recapText };
    this.entryService.createEntry(this.session.id, input).subscribe({
      next: created => {
        this.entries = [created, ...this.entries];
        this.recapText = null;
        this.recapFrom = null;
      },
      error: () => console.error('Erreur lors de l\'ajout du récap au journal')
    });
  }

  closeRecap(): void {
    this.recapText = null;
    this.recapFrom = null;
    this.recapError = null;
  }

  // ─────────────── Renommage de la Session ───────────────

  startRename(): void {
    if (!this.session) return;
    this.editName = this.session.name;
    this.editingName = true;
  }

  cancelRename(): void {
    this.editingName = false;
    this.editName = '';
  }

  saveRename(): void {
    if (!this.session || !this.editName.trim()) return;
    this.sessionService.renameSession(this.session.id, this.editName.trim()).subscribe({
      next: updated => {
        this.session = updated;
        this.editingName = false;
        this.pageTitleService.set(updated.name);
      },
      error: () => console.error('Erreur lors du renommage de la session')
    });
  }

  // ─────────────── Fin / suppression de Session ───────────────

  endSession(): void {
    if (!this.session || !this.session.active) return;
    const session = this.session;
    this.confirmDialog.confirm({
      title: this.translate.instant('sessionDetail.endConfirm.title'),
      message: this.translate.instant('sessionDetail.endConfirm.message', { name: session.name }),
      details: [this.translate.instant('sessionDetail.endConfirm.detail')],
      confirmLabel: this.translate.instant('sessionDetail.endConfirm.confirmLabel'),
      variant: 'warning'
    }).then(ok => {
      if (!ok) return;
      this.sessionService.endSession(session.id).subscribe({
        next: updated => this.session = updated,
        error: () => console.error('Erreur lors de la fin de session')
      });
    });
  }

  deleteSession(): void {
    if (!this.session) return;
    const session = this.session;
    const entryCount = this.entries.length;
    const entriesDetail = entryCount === 0
      ? this.translate.instant('sessionDetail.deleteConfirm.noEntries')
      : entryCount === 1
        ? this.translate.instant('sessionDetail.deleteConfirm.entriesOne')
        : this.translate.instant('sessionDetail.deleteConfirm.entriesMany', { n: entryCount });
    const details = [
      entriesDetail,
      this.translate.instant('sessionDetail.deleteConfirm.irreversible')
    ];
    this.confirmDialog.confirm({
      title: this.translate.instant('sessionDetail.deleteConfirm.title'),
      message: this.translate.instant('sessionDetail.deleteConfirm.message', { name: session.name }),
      details,
      confirmLabel: this.translate.instant('common.delete'),
      variant: 'danger'
    }).then(ok => {
      if (!ok) return;
      const cid = this.campaignId;
      this.sessionService.deleteSession(session.id).subscribe({
        next: () => {
          if (cid) this.router.navigate(['/campaigns', cid]);
          else this.router.navigate(['/campaigns']);
        },
        error: () => console.error('Erreur lors de la suppression de la session')
      });
    });
  }

  // ─────────────── Ajout d'entrée ───────────────

  submitNewEntry(): void {
    if (!this.session || this.submittingEntry) return;
    const content = this.newEntryContent.trim();
    if (!content) return;
    this.submittingEntry = true;
    const input: SessionEntryInput = { type: this.newEntryType, content };
    this.entryService.createEntry(this.session.id, input).subscribe({
      next: created => {
        this.submittingEntry = false;
        this.entries = [created, ...this.entries];
        this.newEntryContent = '';
      },
      error: () => {
        this.submittingEntry = false;
        console.error('Erreur lors de l\'ajout de l\'entrée');
      }
    });
  }

  // ─────────────── Édition d'entrée ───────────────

  startEditEntry(entry: SessionEntry): void {
    this.editingEntryId = entry.id;
    this.editEntryType = entry.type;
    this.editEntryContent = entry.content;
  }

  cancelEditEntry(): void {
    this.editingEntryId = null;
    this.editEntryContent = '';
  }

  saveEditEntry(entry: SessionEntry): void {
    if (!this.session) return;
    const content = this.editEntryContent.trim();
    if (!content) return;
    const input: SessionEntryInput = { type: this.editEntryType, content };
    this.entryService.updateEntry(this.session.id, entry.id, input).subscribe({
      next: updated => {
        this.entries = this.entries.map(e => e.id === updated.id ? updated : e);
        this.editingEntryId = null;
      },
      error: () => console.error('Erreur lors de la mise à jour de l\'entrée')
    });
  }

  /**
   * Réception d'un jet de dés depuis le panneau latéral.
   * On crée une entrée DICE_ROLL dans le journal avec le résumé formaté.
   */
  onDiceRolled(result: DiceRollResult): void {
    if (!this.session || !this.session.active) return;
    const input: SessionEntryInput = { type: 'DICE_ROLL', content: result.summary };
    this.entryService.createEntry(this.session.id, input).subscribe({
      next: created => this.entries = [created, ...this.entries],
      error: () => console.error('Erreur lors de l\'ajout du jet au journal')
    });
  }

  /**
   * Réception d'une réponse IA à sauvegarder dans le journal.
   * Type NOTE par défaut car c'est le MJ qui choisit de capter une suggestion
   * comme repère — pas un évènement de partie en lui-même.
   */
  onAiReplyToJournal(content: string): void {
    if (!this.session || !this.session.active) return;
    const trimmed = content.trim();
    if (!trimmed) return;
    const input: SessionEntryInput = { type: 'NOTE', content: '💡 ' + trimmed };
    this.entryService.createEntry(this.session.id, input).subscribe({
      next: created => this.entries = [created, ...this.entries],
      error: () => console.error('Erreur lors de l\'ajout de la suggestion IA au journal')
    });
  }

  /**
   * Réception d'un objet de catalogue à consigner dans le journal.
   * Le panneau fournit déjà une chaîne formatée (🛒 …) : on la sauvegarde
   * telle quelle comme entrée NOTE.
   */
  onItemNoteToJournal(content: string): void {
    if (!this.session || !this.session.active) return;
    const trimmed = content.trim();
    if (!trimmed) return;
    const input: SessionEntryInput = { type: 'NOTE', content: trimmed };
    this.entryService.createEntry(this.session.id, input).subscribe({
      next: created => this.entries = [created, ...this.entries],
      error: () => console.error('Erreur lors de l\'ajout de l\'objet au journal')
    });
  }

  deleteEntry(entry: SessionEntry): void {
    if (!this.session) return;
    const session = this.session;
    this.confirmDialog.confirm({
      title: this.translate.instant('sessionDetail.deleteEntryConfirm.title'),
      message: this.translate.instant('sessionDetail.deleteEntryConfirm.message'),
      confirmLabel: this.translate.instant('common.delete'),
      variant: 'danger'
    }).then(ok => {
      if (!ok) return;
      this.entryService.deleteEntry(session.id, entry.id).subscribe({
        next: () => this.entries = this.entries.filter(e => e.id !== entry.id),
        error: () => console.error('Erreur lors de la suppression de l\'entrée')
      });
    });
  }

  ngOnDestroy(): void {
    this.layoutService.hide();
  }
}
