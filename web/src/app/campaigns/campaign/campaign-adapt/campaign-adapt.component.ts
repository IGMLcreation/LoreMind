import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, ArrowLeft, Upload, Copy, Check, Send } from 'lucide-angular';
import { CampaignAdaptService, AdaptMessage } from '../../../services/campaign-adapt.service';
import { CampaignSidebarService } from '../../../services/campaign-sidebar.service';
import { PageTitleService } from '../../../services/page-title.service';
import { MarkdownPipe } from '../../../shared/markdown.pipe';

const FIRST_PROMPT = 'Propose-moi comment intégrer et adapter ce PDF à ma campagne.';

/**
 * Page « Adapter un PDF » — CONVERSATIONNELLE. L'IA connaît la campagne (structure,
 * PNJ, univers) + lit le PDF, propose une 1re adaptation, puis l'utilisateur peut
 * répondre (corriger, demander des alternatives…) et l'IA rebondit.
 * Route : /campaigns/:campaignId/adapt — rien n'est créé, conseils à appliquer à la main.
 */
@Component({
  selector: 'app-campaign-adapt',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideAngularModule, MarkdownPipe],
  templateUrl: './campaign-adapt.component.html',
  styleUrls: ['./campaign-adapt.component.scss']
})
export class CampaignAdaptComponent implements OnInit {
  readonly ArrowLeft = ArrowLeft;
  readonly Upload = Upload;
  readonly Copy = Copy;
  readonly Check = Check;
  readonly Send = Send;

  campaignId = '';

  /** PDF choisi, conservé pour les tours de conversation suivants. */
  private file: File | null = null;
  fileName = '';

  /** Conversation affichée (user + assistant). */
  messages: AdaptMessage[] = [];
  streaming = false;
  error: string | null = null;

  /** Saisie du message en cours. */
  input = '';
  copiedIndex: number | null = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private service: CampaignAdaptService,
    private campaignSidebar: CampaignSidebarService,
    private pageTitle: PageTitleService
  ) {}

  ngOnInit(): void {
    this.campaignId = this.route.snapshot.paramMap.get('campaignId')!;
    this.pageTitle.set('Adapter un PDF');
    this.campaignSidebar.show(this.campaignId);
  }

  get hasConversation(): boolean { return this.messages.length > 0; }

  // --- Choix du PDF (démarre / réinitialise la conversation) ---------------

  onPdfSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    this.file = file;
    this.fileName = file.name;
    this.messages = [];
    this.error = null;
    this.send(FIRST_PROMPT);
  }

  // --- Envoi d'un message (1er tour ou feedback) ---------------------------

  sendCurrent(): void {
    const text = this.input.trim();
    if (!text || this.streaming) return;
    this.input = '';
    this.send(text);
  }

  private send(text: string): void {
    if (!this.file || this.streaming) return;
    this.error = null;

    this.messages.push({ role: 'user', content: text });
    // Historique envoyé = tout jusqu'au message user inclus (sans la bulle vide).
    const payload: AdaptMessage[] = this.messages.map(m => ({ role: m.role, content: m.content }));

    const assistant: AdaptMessage = { role: 'assistant', content: '' };
    this.messages.push(assistant);
    this.streaming = true;

    this.service.adviseStream(this.campaignId, this.file, payload).subscribe({
      next: (ev) => {
        if (ev.type === 'token') {
          assistant.content += ev.value;
        } else if (ev.type === 'done') {
          this.streaming = false;
        }
      },
      error: (err: Error) => {
        this.streaming = false;
        // Bulle assistant restée vide → on la retire pour ne pas afficher de vide.
        if (!assistant.content) {
          this.messages = this.messages.filter(m => m !== assistant);
        }
        this.error = err?.message ? `Échec : ${err.message}` : "Échec de l'adaptation.";
      }
    });
  }

  copy(index: number): void {
    const msg = this.messages[index];
    if (!msg) return;
    navigator.clipboard?.writeText(msg.content).then(() => {
      this.copiedIndex = index;
      setTimeout(() => (this.copiedIndex = null), 2000);
    });
  }

  back(): void {
    this.router.navigate(['/campaigns', this.campaignId]);
  }
}
