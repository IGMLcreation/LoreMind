import { Component, OnInit } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { LucideAngularModule, ArrowLeft, Upload, Trash2, Send, FileText, Loader, CheckCircle2, AlertCircle, Sparkles, Layers, Eraser, History, X } from 'lucide-angular';
import { NotebookService } from '../../../services/notebook.service';
import { CampaignSidebarService } from '../../../services/campaign-sidebar.service';
import { CampaignService } from '../../../services/campaign.service';
import { CharacterService } from '../../../services/character.service';
import { NpcService } from '../../../services/npc.service';
import { EnemyService } from '../../../services/enemy.service';
import { NotebookArchive, NotebookDetail, NotebookSource, NotebookMessage } from '../../../services/notebook.model';
import { parseNotebookActions, NotebookAction } from '../../../services/notebook-action.model';
import { Arc, Chapter } from '../../../services/campaign.model';
import { loadCampaignTreeData } from '../../campaign-tree.helper';
import { NotebookActionCardComponent } from '../notebook-action-card/notebook-action-card.component';
import { MarkdownPipe } from '../../../shared/markdown.pipe';
import { ConfirmDialogService } from '../../../shared/confirm-dialog/confirm-dialog.service';

/**
 * Atelier (workspace) : sources indexées (gauche) + chat ancré RAG (droite).
 * Route : /campaigns/:campaignId/notebooks/:notebookId
 */
@Component({
    selector: 'app-notebook-detail',
    imports: [FormsModule, LucideAngularModule, NotebookActionCardComponent, MarkdownPipe],
    templateUrl: './notebook-detail.component.html',
    styleUrls: ['./notebook-detail.component.scss']
})
export class NotebookDetailComponent implements OnInit {
  readonly ArrowLeft = ArrowLeft;
  readonly Upload = Upload;
  readonly Trash2 = Trash2;
  readonly Send = Send;
  readonly FileText = FileText;
  readonly Loader = Loader;
  readonly CheckCircle2 = CheckCircle2;
  readonly AlertCircle = AlertCircle;
  readonly Sparkles = Sparkles;
  readonly Layers = Layers;
  readonly Eraser = Eraser;
  readonly History = History;
  readonly X = X;

  campaignId = '';
  notebookId = '';
  detail: NotebookDetail | null = null;
  sources: NotebookSource[] = [];
  messages: NotebookMessage[] = [];

  uploading = false;
  uploadError = '';
  sending = false;
  draft = '';
  /** Avancement de l'analyse approfondie (lecture du doc par lots). */
  deepProgress: { current: number; total: number } | null = null;

  // Arbre de la campagne — sert aux cartes d'action (cibles arc/chapitre).
  arcs: Arc[] = [];
  chaptersByArc: Record<string, Chapter[]> = {};

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private service: NotebookService,
    private campaignSidebar: CampaignSidebarService,
    private campaignService: CampaignService,
    private characterService: CharacterService,
    private npcService: NpcService,
    private enemyService: EnemyService,
    private confirmDialog: ConfirmDialogService
  ) {}

  ngOnInit(): void {
    this.campaignId = this.route.snapshot.paramMap.get('campaignId') ?? '';
    this.notebookId = this.route.snapshot.paramMap.get('notebookId') ?? '';
    if (this.campaignId) {
      this.campaignSidebar.show(this.campaignId);
      this.loadTree();
    }
    this.load();
  }

  private loadTree(): void {
    loadCampaignTreeData(this.campaignService, this.campaignId, this.characterService, this.npcService, undefined, this.enemyService)
      .subscribe({
        next: (data) => { this.arcs = data.arcs; this.chaptersByArc = data.chaptersByArc; },
        error: () => { /* cibles indisponibles : les cartes le signaleront */ }
      });
  }

  /** Après création depuis une carte : rafraîchit la sidebar (l'élément apparaît)
   *  et l'arbre des cibles (pour les cartes suivantes). */
  onActionCreated(): void {
    if (this.campaignId) {
      this.campaignSidebar.show(this.campaignId);
      this.loadTree();
    }
  }

  /** Sépare le texte affiché des blocs d'action — MÉMORISÉ par message : renvoie
   *  une référence STABLE tant que le contenu n'a pas changé. Indispensable :
   *  appeler le parseur directement dans le template recréait le DOM à chaque
   *  détection de changement (au mousedown) → les clics sur les cartes étaient
   *  perdus. */
  parsedOf(m: NotebookMessage): { text: string; actions: NotebookAction[] } {
    const cache = m as unknown as {
      _parsedFor?: string;
      _parsed?: { text: string; actions: NotebookAction[] };
    };
    if (cache._parsedFor !== m.content || !cache._parsed) {
      cache._parsed = parseNotebookActions(m.content);
      cache._parsedFor = m.content;
    }
    return cache._parsed;
  }

  /**
   * Libellé compact des pages utilisées par le RAG pour une réponse
   * (« p. 12, 47, 103 » — préfixé du nom du fichier si plusieurs sources).
   */
  sourcesLabel(m: NotebookMessage): string {
    const src = m.sources ?? [];
    if (!src.length) return '';
    const bySource = new Map<string, number[]>();
    for (const s of src) {
      if (s.page == null) continue;
      const pages = bySource.get(s.sourceId) ?? [];
      if (!pages.includes(s.page)) pages.push(s.page);
      bySource.set(s.sourceId, pages);
    }
    if (bySource.size === 0) return '';
    const nameOf = (id: string) => this.sources.find(x => x.id === id)?.filename ?? '';
    return [...bySource.entries()]
      .map(([id, pages]) => {
        const label = `p. ${pages.sort((a, b) => a - b).join(', ')}`;
        return bySource.size > 1 && nameOf(id) ? `${nameOf(id)} — ${label}` : label;
      })
      .join(' · ');
  }

  load(): void {
    this.service.get(this.notebookId).subscribe({
      next: (d) => {
        this.detail = d;
        this.sources = d.sources ?? [];
        this.messages = d.messages ?? [];
        this.syncSelection();
      },
      error: () => this.back()
    });
  }

  reloadSources(): void {
    this.service.get(this.notebookId).subscribe({ next: (d) => {
      this.sources = d.sources ?? [];
      this.syncSelection();
    } });
  }

  // --- Sélection des sources utilisées par le chat -------------------------
  // Par défaut tout est coché ; décocher permet de limiter une question (et
  // surtout une analyse approfondie, coûteuse en requêtes) à certains PDF.

  /** IDs des sources cochées (sous-ensemble des sources READY). */
  selectedSourceIds = new Set<string>();
  /** IDs déjà vus — pour ne cocher par défaut que les NOUVELLES sources. */
  private knownSourceIds = new Set<string>();

  /** Aligne la sélection sur la liste courante : nouvelles sources cochées par
   *  défaut, sources supprimées retirées, choix de l'utilisateur préservés. */
  private syncSelection(): void {
    const readyIds = new Set(this.sources.filter(s => s.status === 'READY').map(s => s.id));
    for (const id of readyIds) {
      if (!this.knownSourceIds.has(id)) {
        this.selectedSourceIds.add(id);
        this.knownSourceIds.add(id);
      }
    }
    for (const id of [...this.selectedSourceIds]) {
      if (!readyIds.has(id)) this.selectedSourceIds.delete(id);
    }
  }

  isSelected(s: NotebookSource): boolean {
    return this.selectedSourceIds.has(s.id);
  }

  toggleSource(s: NotebookSource): void {
    if (this.selectedSourceIds.has(s.id)) this.selectedSourceIds.delete(s.id);
    else this.selectedSourceIds.add(s.id);
  }

  get readySourceCount(): number {
    return this.sources.filter(s => s.status === 'READY').length;
  }

  // --- Sources ---

  onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    this.uploading = true;
    this.uploadError = '';
    this.service.addSource(this.notebookId, file).subscribe({
      next: () => { this.uploading = false; this.reloadSources(); },
      error: (err) => {
        this.uploading = false;
        this.uploadError = err?.error?.message || 'Échec de l\'indexation. Réessaie ou vérifie le modèle d\'embedding.';
        this.reloadSources();
      }
    });
  }

  removeSource(s: NotebookSource): void {
    this.service.deleteSource(s.id).subscribe(() => this.reloadSources());
  }

  // --- Vider la conversation / archives -------------------------------------
  // « Vider » ARCHIVE le fil (rien n'est supprimé) ; les archives restent
  // consultables en lecture seule via le sélecteur.

  /** Conversations archivées (chargées à l'ouverture du panneau). */
  archives: NotebookArchive[] = [];
  /** Archive affichée en lecture seule (null = chat actif). */
  viewingArchive: NotebookArchive | null = null;
  /** Panneau « archives » ouvert ? */
  archivesOpen = false;

  clearChat(): void {
    if (this.sending || this.messages.length === 0) return;
    this.confirmDialog.confirm({
      title: 'Vider la conversation',
      message: 'Repartir d\'une conversation vierge ?',
      details: ['Le fil actuel est archivé (pas supprimé) : il restera consultable via « Archives ».'],
      confirmLabel: 'Vider',
      variant: 'danger'
    }).then(ok => {
      if (!ok) return;
      this.service.clearChat(this.notebookId).subscribe({
        next: () => {
          this.messages = [];
          this.archives = [];      // re-chargées à la prochaine ouverture du panneau
          this.archivesOpen = false;
        },
        error: () => { /* le fil reste affiché : rien n'a été modifié côté serveur */ }
      });
    });
  }

  toggleArchives(): void {
    this.archivesOpen = !this.archivesOpen;
    if (this.archivesOpen && this.archives.length === 0) {
      this.service.getArchives(this.notebookId).subscribe({
        next: (a) => this.archives = a,
        error: () => { this.archives = []; }
      });
    }
  }

  /**
   * Archives cochées « en référence » : leur contenu est injecté dans le
   * contexte de chaque tour de chat (normal et approfondi). Clés = archivedAt.
   */
  referencedArchiveIds = new Set<string>();

  isReferenced(a: NotebookArchive): boolean {
    return this.referencedArchiveIds.has(a.archivedAt);
  }

  toggleReference(a: NotebookArchive): void {
    if (this.referencedArchiveIds.has(a.archivedAt)) this.referencedArchiveIds.delete(a.archivedAt);
    else this.referencedArchiveIds.add(a.archivedAt);
  }

  openArchive(a: NotebookArchive): void {
    this.viewingArchive = a;
    this.archivesOpen = false;
  }

  closeArchive(): void {
    this.viewingArchive = null;
  }

  /** Libellé d'une archive : date du clear + nb d'échanges. */
  archiveLabel(a: NotebookArchive): string {
    const date = new Date(a.archivedAt);
    const when = isNaN(date.getTime())
      ? a.archivedAt
      : date.toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' });
    return `${when} · ${a.messages.length} message(s)`;
  }

  // --- Chat ---

  send(deep = false): void {
    const text = this.draft.trim();
    if (!text || this.sending) return;
    this.draft = '';
    this.deepProgress = null;
    this.messages.push({ role: 'user', content: text });
    const assistant: NotebookMessage = { role: 'assistant', content: '' };
    this.messages.push(assistant);
    this.sending = true;

    this.service.streamChat(
      this.notebookId, text, deep,
      [...this.selectedSourceIds], [...this.referencedArchiveIds]
    ).subscribe({
      next: (ev) => {
        if (ev.type === 'token') { this.deepProgress = null; assistant.content += ev.value; }
        else if (ev.type === 'sources') assistant.sources = ev.sources;
        else if (ev.type === 'progress') this.deepProgress = { current: ev.current, total: ev.total };
        else if (ev.type === 'error') assistant.content += (assistant.content ? '\n\n' : '') + `⚠️ ${ev.message}`;
      },
      complete: () => { this.sending = false; this.deepProgress = null; },
      error: () => { this.sending = false; this.deepProgress = null; }
    });
  }

  rename(): void {
    if (!this.detail || !this.detail.name.trim()) return;
    this.service.rename(this.notebookId, this.detail.name.trim()).subscribe();
  }

  back(): void {
    this.router.navigate(['/campaigns', this.campaignId, 'notebooks']);
  }

  hasReadySource(): boolean {
    return this.sources.some(s => s.status === 'READY');
  }
}
