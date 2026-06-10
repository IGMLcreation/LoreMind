import { Component, OnInit } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { LucideAngularModule, ArrowLeft, Upload, Trash2, Send, FileText, Loader, CheckCircle2, AlertCircle, Sparkles, Layers } from 'lucide-angular';
import { NotebookService } from '../../../services/notebook.service';
import { CampaignSidebarService } from '../../../services/campaign-sidebar.service';
import { CampaignService } from '../../../services/campaign.service';
import { CharacterService } from '../../../services/character.service';
import { NpcService } from '../../../services/npc.service';
import { NotebookDetail, NotebookSource, NotebookMessage } from '../../../services/notebook.model';
import { parseNotebookActions, NotebookAction } from '../../../services/notebook-action.model';
import { Arc, Chapter } from '../../../services/campaign.model';
import { loadCampaignTreeData } from '../../campaign-tree.helper';
import { NotebookActionCardComponent } from '../notebook-action-card/notebook-action-card.component';

/**
 * Atelier (workspace) : sources indexées (gauche) + chat ancré RAG (droite).
 * Route : /campaigns/:campaignId/notebooks/:notebookId
 */
@Component({
    selector: 'app-notebook-detail',
    imports: [FormsModule, LucideAngularModule, NotebookActionCardComponent],
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
    private npcService: NpcService
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
    loadCampaignTreeData(this.campaignService, this.campaignId, this.characterService, this.npcService)
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
      },
      error: () => this.back()
    });
  }

  reloadSources(): void {
    this.service.get(this.notebookId).subscribe({ next: (d) => this.sources = d.sources ?? [] });
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

    this.service.streamChat(this.notebookId, text, deep).subscribe({
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
