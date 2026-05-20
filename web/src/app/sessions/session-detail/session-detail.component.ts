import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import {
  LucideAngularModule, LucideIconData,
  Dices, ArrowLeft, Square, Trash2, Pencil, Check,
  StickyNote, Sparkles, UserCheck, Plus, X
} from 'lucide-angular';
import { catchError, switchMap, filter, map } from 'rxjs/operators';
import { of } from 'rxjs';
import { SessionService } from '../../services/session.service';
import { Session } from '../../services/session.model';
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
  standalone: true,
  imports: [CommonModule, FormsModule, LucideAngularModule, RouterLink, SessionReferencePanelComponent],
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

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private sessionService: SessionService,
    private entryService: SessionEntryService,
    private layoutService: LayoutService,
    private pageTitleService: PageTitleService,
    private confirmDialog: ConfirmDialogService
  ) {}

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
      title: 'Terminer la session ?',
      message: `Marquer la session "${session.name}" comme terminée ?`,
      details: ['Tu pourras toujours consulter son contenu après.'],
      confirmLabel: 'Terminer',
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
    const details = [
      entryCount > 0
        ? `${entryCount} entrée${entryCount > 1 ? 's' : ''} de journal sera également supprimée.`
        : 'Aucune entrée de journal pour cette session.',
      'Cette action est irréversible.'
    ];
    this.confirmDialog.confirm({
      title: 'Supprimer la session ?',
      message: `Supprimer définitivement la session "${session.name}" ?`,
      details,
      confirmLabel: 'Supprimer',
      variant: 'danger'
    }).then(ok => {
      if (!ok) return;
      const campaignId = session.campaignId;
      this.sessionService.deleteSession(session.id).subscribe({
        next: () => this.router.navigate(['/campaigns', campaignId]),
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

  deleteEntry(entry: SessionEntry): void {
    if (!this.session) return;
    const session = this.session;
    this.confirmDialog.confirm({
      title: 'Supprimer cette entrée ?',
      message: 'Cette entrée du journal sera définitivement supprimée.',
      confirmLabel: 'Supprimer',
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
