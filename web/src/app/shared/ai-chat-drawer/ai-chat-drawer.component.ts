import { CommonModule } from '@angular/common';
import { Component, ElementRef, EventEmitter, HostListener, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, Lightbulb, Maximize2, MessageSquarePlus, Minimize2, PanelLeftClose, PanelLeftOpen, Pencil, Send, Sparkles, Trash2, Wand2, X } from 'lucide-angular';
import { Subscription } from 'rxjs';
import { AiChatService, ChatMessage, ChatUsage, NarrativeEntityType } from '../../services/ai-chat.service';
import { Conversation, ConversationContext } from '../../services/conversation.model';
import { ConversationService } from '../../services/conversation.service';
import { MarkdownPipe } from '../markdown.pipe';

/**
 * Action primaire optionnelle rendue en gros bouton au-dessus des suggestions.
 * Utilisee pour les actions "speciales" qui NE passent PAS par le chat
 * (ex: "Remplir automatiquement tous les champs" → declenche le one-shot b4).
 */
export interface ChatPrimaryAction {
  label: string;
}

/**
 * Drawer de chat IA reutilisable — panneau fixe a droite de l'ecran.
 *
 * Deux modes :
 *  - `persistent = true` (defaut) : sidebar + conversations persistees en base,
 *    filtrees par contexte (loreId/campaignId + optionnellement entityType+Id).
 *    Les messages sont persistes en base au fil du chat et un titre automatique
 *    est genere apres le 1er echange.
 *  - `persistent = false` : mode ephemere (pour le wizard de generation de page,
 *    ou la conversation n'a aucune valeur au-dela de l'usage immediat).
 */
@Component({
  selector: 'app-ai-chat-drawer',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideAngularModule, MarkdownPipe],
  templateUrl: './ai-chat-drawer.component.html',
  styleUrls: ['./ai-chat-drawer.component.scss'],
})
export class AiChatDrawerComponent implements OnInit, OnChanges, OnDestroy {
  readonly X = X;
  readonly Send = Send;
  readonly Sparkles = Sparkles;
  readonly Lightbulb = Lightbulb;
  readonly Wand2 = Wand2;
  readonly MessageSquarePlus = MessageSquarePlus;
  readonly PanelLeftClose = PanelLeftClose;
  readonly PanelLeftOpen = PanelLeftOpen;
  readonly Pencil = Pencil;
  readonly Trash2 = Trash2;
  readonly Maximize2 = Maximize2;
  readonly Minimize2 = Minimize2;

  // --- Redimensionnement --------------------------------------------------
  /** Largeur min du drawer en pixels (en dessous ca devient inutilisable). */
  private readonly MIN_WIDTH = 340;
  /** Largeur max : 95% de la fenetre, pour laisser un peu de page visible. */
  private readonly MAX_WIDTH_RATIO = 0.95;
  /** Largeurs par defaut selon la presence de la sidebar conversations. */
  private readonly DEFAULT_WIDTH = 380;
  private readonly DEFAULT_WIDTH_WITH_SIDEBAR = 600;
  /** Ratio utilise en mode "agrandi" (bouton Maximize). */
  private readonly WIDE_RATIO = 0.6;

  private readonly LS_WIDTH = 'ai-chat-drawer-width';
  private readonly LS_WIDE = 'ai-chat-drawer-wide';

  /** Largeur custom choisie par l'utilisateur via la poignee (null = defaut). */
  customWidth: number | null = null;
  /** Mode "grand ecran" : prend ~60% de la fenetre, ignore customWidth. */
  isWide = false;
  /** Drag en cours — utilise pour desactiver les transitions pendant le resize. */
  isResizing = false;
  private resizeStartX = 0;
  private resizeStartWidth = 0;
  private readonly onMouseMove = (e: MouseEvent) => this.handleResizeMove(e);
  private readonly onMouseUp = () => this.handleResizeEnd();

  @Input() loreId = '';
  @Input() pageId: string | null = null;
  @Input() campaignId: string | null = null;
  @Input() entityType: NarrativeEntityType | null = null;
  @Input() entityId: string | null = null;
  @Input() isOpen = false;
  @Input() welcomeMessage = 'Bonjour ! Je peux vous aider a developper cette page. Que souhaitez-vous creer ?';
  @Input() quickSuggestions: string[] = [];
  @Input() primaryAction: ChatPrimaryAction | null = null;
  @Input() systemPromptAddon: string | null = null;
  /** Persistance activee ? false = mode wizard ephemere. */
  @Input() persistent = true;

  @Output() close = new EventEmitter<void>();
  @Output() primaryActionClick = new EventEmitter<void>();
  @Output() assistantReply = new EventEmitter<string>();

  @ViewChild('messagesContainer') messagesContainer?: ElementRef<HTMLDivElement>;

  messages: ChatMessage[] = [];
  currentAssistantText = '';
  input = '';
  isStreaming = false;
  errorMessage: string | null = null;
  usage: ChatUsage | null = null;

  // --- Persistance --------------------------------------------------------

  /** Liste visible dans la sidebar pour le contexte courant. */
  conversations: Conversation[] = [];
  /** Conversation actuellement chargee (null = nouvelle conversation vierge). */
  currentConversationId: string | null = null;
  /** Titre de la conversation courante (affiche dans le header). */
  currentTitle = '';
  /** Mode edition inline du titre. */
  editingTitle = false;
  titleDraft = '';
  /** Etat repliable de la sidebar. */
  sidebarOpen = true;

  private streamSub: Subscription | null = null;

  constructor(
    private readonly chatService: AiChatService,
    private readonly conversationService: ConversationService,
  ) {}

  // --- Jauge de contexte --------------------------------------------------

  get usageTotal(): number {
    if (!this.usage) return 0;
    return this.usage.system + this.usage.history + this.usage.current;
  }
  get usageRatio(): number {
    if (!this.usage || this.usage.max <= 0) return 0;
    return Math.min(1, this.usageTotal / this.usage.max);
  }
  get usagePercent(): number {
    return Math.round(this.usageRatio * 100);
  }
  get usageLevel(): 'low' | 'mid' | 'high' {
    const r = this.usageRatio;
    if (r > 0.8) return 'high';
    if (r >= 0.5) return 'mid';
    return 'low';
  }

  // --- Cycle de vie -------------------------------------------------------

  ngOnInit(): void {
    this.loadSizePreferences();
  }

  @HostListener('window:resize')
  onWindowResize(): void {
    // Clamp si la fenetre a retreci en dessous du max actuel.
    if (this.customWidth !== null) {
      const max = Math.floor(window.innerWidth * this.MAX_WIDTH_RATIO);
      if (this.customWidth > max) this.customWidth = max;
    }
  }

  /** Largeur effective appliquee au drawer (px). */
  get effectiveWidth(): number {
    if (this.isWide) {
      return Math.max(this.MIN_WIDTH, Math.floor(window.innerWidth * this.WIDE_RATIO));
    }
    if (this.customWidth !== null) return this.customWidth;
    return this.persistent && this.sidebarOpen
      ? this.DEFAULT_WIDTH_WITH_SIDEBAR
      : this.DEFAULT_WIDTH;
  }

  toggleWide(): void {
    this.isWide = !this.isWide;
    try {
      localStorage.setItem(this.LS_WIDE, this.isWide ? '1' : '0');
    } catch {}
  }

  /** Debut du drag : enregistre la position de depart + abonne listeners globaux. */
  onResizeStart(event: MouseEvent): void {
    if (this.isWide) return; // en mode wide la poignee est desactivee
    event.preventDefault();
    this.isResizing = true;
    this.resizeStartX = event.clientX;
    this.resizeStartWidth = this.effectiveWidth;
    document.addEventListener('mousemove', this.onMouseMove);
    document.addEventListener('mouseup', this.onMouseUp);
    // Empeche la selection de texte pendant le drag.
    document.body.style.userSelect = 'none';
    document.body.style.cursor = 'ew-resize';
  }

  private handleResizeMove(event: MouseEvent): void {
    if (!this.isResizing) return;
    // Le drawer est ancre a droite : largeur augmente quand la souris va a gauche.
    const delta = this.resizeStartX - event.clientX;
    const max = Math.floor(window.innerWidth * this.MAX_WIDTH_RATIO);
    const next = Math.min(max, Math.max(this.MIN_WIDTH, this.resizeStartWidth + delta));
    this.customWidth = next;
  }

  private handleResizeEnd(): void {
    if (!this.isResizing) return;
    this.isResizing = false;
    document.removeEventListener('mousemove', this.onMouseMove);
    document.removeEventListener('mouseup', this.onMouseUp);
    document.body.style.userSelect = '';
    document.body.style.cursor = '';
    if (this.customWidth !== null) {
      try {
        localStorage.setItem(this.LS_WIDTH, String(this.customWidth));
      } catch {}
    }
  }

  private loadSizePreferences(): void {
    try {
      const w = localStorage.getItem(this.LS_WIDTH);
      if (w) {
        const n = parseInt(w, 10);
        if (Number.isFinite(n) && n >= this.MIN_WIDTH) {
          const max = Math.floor(window.innerWidth * this.MAX_WIDTH_RATIO);
          this.customWidth = Math.min(max, n);
        }
      }
      this.isWide = localStorage.getItem(this.LS_WIDE) === '1';
    } catch {}
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (!this.persistent) return;
    const contextChanged =
      changes['loreId'] || changes['pageId'] || changes['campaignId'] || changes['entityType'] || changes['entityId'];
    const openedNow = changes['isOpen'] && this.isOpen;
    if (contextChanged || openedNow) {
      this.resetConversationState();
      this.reloadConversations();
    }
  }

  ngOnDestroy(): void {
    this.abortStream();
    document.removeEventListener('mousemove', this.onMouseMove);
    document.removeEventListener('mouseup', this.onMouseUp);
  }

  // --- Sidebar : listing / nouveau / select / rename / delete ------------

  private buildContext(): ConversationContext {
    // Cote Lore : pageId joue le role de focus entite (entityType="page").
    // Cote Campagne : entityType + entityId sont deja fournis directement.
    if (this.loreId) {
      return {
        loreId: this.loreId,
        campaignId: null,
        entityType: this.pageId ? 'page' : null,
        entityId: this.pageId ?? null,
      };
    }
    return {
      loreId: null,
      campaignId: this.campaignId || null,
      entityType: this.entityType,
      entityId: this.entityId,
    };
  }

  reloadConversations(): void {
    if (!this.persistent) return;
    const ctx = this.buildContext();
    if (!ctx.loreId && !ctx.campaignId) {
      this.conversations = [];
      return;
    }
    this.conversationService.list(ctx).subscribe({
      next: (rows) => (this.conversations = rows),
      error: () => (this.conversations = []),
    });
  }

  startNewConversation(): void {
    if (this.isStreaming) return;
    this.resetConversationState();
  }

  private resetConversationState(): void {
    this.currentConversationId = null;
    this.currentTitle = '';
    this.messages = [];
    this.currentAssistantText = '';
    this.errorMessage = null;
    this.usage = null;
    this.editingTitle = false;
  }

  selectConversation(conv: Conversation): void {
    if (this.isStreaming) return;
    this.conversationService.getById(conv.id).subscribe({
      next: (full) => {
        this.currentConversationId = full.id;
        this.currentTitle = full.title;
        this.messages = (full.messages ?? [])
          .filter((m) => m.role === 'user' || m.role === 'assistant')
          .map((m) => ({ role: m.role as 'user' | 'assistant', content: m.content }));
        this.currentAssistantText = '';
        this.errorMessage = null;
        this.usage = null;
        this.scrollToBottom();
      },
      error: () => (this.errorMessage = 'Impossible de charger la conversation.'),
    });
  }

  deleteConversation(conv: Conversation, event: Event): void {
    event.stopPropagation();
    if (this.isStreaming) return;
    if (!confirm(`Supprimer la conversation "${conv.title}" ?`)) return;
    this.conversationService.delete(conv.id).subscribe({
      next: () => {
        this.conversations = this.conversations.filter((c) => c.id !== conv.id);
        if (this.currentConversationId === conv.id) this.resetConversationState();
      },
    });
  }

  startRenameTitle(): void {
    if (!this.currentConversationId) return;
    this.titleDraft = this.currentTitle;
    this.editingTitle = true;
  }

  cancelRenameTitle(): void {
    this.editingTitle = false;
    this.titleDraft = '';
  }

  submitRenameTitle(): void {
    const t = this.titleDraft.trim();
    if (!t || !this.currentConversationId) {
      this.cancelRenameTitle();
      return;
    }
    const id = this.currentConversationId;
    this.conversationService.rename(id, t).subscribe({
      next: () => {
        this.currentTitle = t;
        this.conversations = this.conversations.map((c) =>
          c.id === id ? { ...c, title: t } : c,
        );
        this.editingTitle = false;
      },
    });
  }

  toggleSidebar(): void {
    this.sidebarOpen = !this.sidebarOpen;
  }

  // --- Handlers UI --------------------------------------------------------

  onClose(): void {
    this.abortStream();
    this.close.emit();
  }

  send(): void {
    const text = this.input.trim();
    if (!text || this.isStreaming) return;
    this.sendUserMessage(text);
    this.input = '';
  }

  useQuickSuggestion(suggestion: string): void {
    if (this.isStreaming) return;
    this.sendUserMessage(suggestion);
  }

  onPrimaryAction(): void {
    if (this.isStreaming) return;
    this.primaryActionClick.emit();
  }

  // --- Envoi + streaming --------------------------------------------------

  private sendUserMessage(text: string): void {
    if (this.persistent) {
      this.ensureConversation().then((convId) => {
        if (convId) this.stream(text, convId);
      });
    } else {
      this.stream(text, null);
    }
  }

  /**
   * Cree la conversation cote serveur si elle n'existe pas encore. Resolu
   * avec l'id, ou null sur erreur (auquel cas on n'envoie pas).
   */
  private ensureConversation(): Promise<string | null> {
    if (this.currentConversationId) return Promise.resolve(this.currentConversationId);
    const ctx = this.buildContext();
    if (!ctx.loreId && !ctx.campaignId) {
      this.errorMessage = 'Contexte manquant pour creer une conversation.';
      return Promise.resolve(null);
    }
    return new Promise((resolve) => {
      this.conversationService.create(ctx).subscribe({
        next: (conv) => {
          this.currentConversationId = conv.id;
          this.currentTitle = conv.title;
          this.conversations = [conv, ...this.conversations];
          resolve(conv.id);
        },
        error: () => {
          this.errorMessage = 'Impossible de creer la conversation.';
          resolve(null);
        },
      });
    });
  }

  /**
   * Stream unifie : persistant si convId est fourni, ephemere sinon.
   * - Le message user est pousse dans le flux visuel immediatement, puis persiste
   *   (si convId) avant meme l'arrivee du premier token — evite la perte en cas
   *   d'interruption reseau.
   * - Le message assistant est persiste a la completion, et un titre auto est
   *   declenche lorsqu'il s'agit du tout premier echange de la conversation.
   */
  private stream(text: string, convId: string | null): void {
    const wasEmpty = this.messages.length === 0;
    this.errorMessage = null;
    this.messages.push({ role: 'user', content: text });
    this.currentAssistantText = '';
    this.isStreaming = true;
    this.scrollToBottom();

    if (convId) {
      this.conversationService.appendMessage(convId, 'user', text).subscribe({ error: () => {} });
    }

    this.streamSub = this.buildStream().subscribe({
      next: (event) => {
        if (event.type === 'token') {
          this.currentAssistantText += event.value;
          this.scrollToBottom();
        } else if (event.type === 'usage') {
          this.usage = event.usage;
        }
      },
      error: (err) => {
        this.isStreaming = false;
        this.errorMessage = err?.message ?? 'Erreur inconnue.';
        this.currentAssistantText = '';
      },
      complete: () => {
        const reply = this.currentAssistantText;
        if (reply) {
          this.messages.push({ role: 'assistant', content: reply });
          this.assistantReply.emit(reply);
          if (convId) {
            this.conversationService.appendMessage(convId, 'assistant', reply).subscribe({
              next: () => {
                if (wasEmpty) this.triggerAutoTitle(convId);
              },
              error: () => {},
            });
          }
        }
        this.currentAssistantText = '';
        this.isStreaming = false;
        this.scrollToBottom();
      },
    });
  }

  private buildStream() {
    const payload = this.systemPromptAddon
      ? [{ role: 'system' as const, content: this.systemPromptAddon }, ...this.messages]
      : this.messages;
    return this.campaignId
      ? this.chatService.streamChatForCampaign(this.campaignId, payload, this.entityType, this.entityId)
      : this.chatService.streamChat(this.loreId, payload, this.pageId);
  }

  private triggerAutoTitle(convId: string): void {
    this.conversationService.autoTitle(convId).subscribe({
      next: ({ title }) => {
        this.currentTitle = title;
        this.conversations = this.conversations.map((c) =>
          c.id === convId ? { ...c, title } : c,
        );
      },
      error: () => {},
    });
  }

  private abortStream(): void {
    this.streamSub?.unsubscribe();
    this.streamSub = null;
    this.isStreaming = false;
    this.currentAssistantText = '';
  }

  private scrollToBottom(): void {
    queueMicrotask(() => {
      const el = this.messagesContainer?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    });
  }
}
