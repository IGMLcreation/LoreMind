import {
  Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges,
  ElementRef, ViewChild
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  LucideAngularModule, Send, Sparkles, Trash2, BookmarkPlus, Square
} from 'lucide-angular';
import { Subscription } from 'rxjs';
import { AiChatService, ChatMessage } from '../../services/ai-chat.service';

/**
 * Panneau de chat IA pour le mode jeu.
 *
 * <p>Diffère du {@link AiChatDrawerComponent} :
 * - conversation 100% éphémère (le journal joue le rôle de mémoire persistante)
 * - intégré dans le panneau latéral, pas en drawer
 * - chaque réponse peut être ajoutée au journal en un clic (event {@link saveToJournal})</p>
 *
 * <p>Le backend reçoit le contexte complet via {@code /api/ai/chat/stream-session} :
 * lore + campagne + GameSystem + journal — l'IA "sait" tout ce qui s'est passé.</p>
 */
@Component({
    selector: 'app-session-ai-chat-panel',
    imports: [CommonModule, FormsModule, LucideAngularModule],
    templateUrl: './session-ai-chat-panel.component.html',
    styleUrls: ['./session-ai-chat-panel.component.scss']
})
export class SessionAiChatPanelComponent implements OnChanges, OnDestroy {
  readonly Send = Send;
  readonly Sparkles = Sparkles;
  readonly Trash2 = Trash2;
  readonly BookmarkPlus = BookmarkPlus;
  readonly Square = Square;

  @Input() sessionId!: string;
  @Input() canSaveToJournal = true;

  /** Émis quand le MJ clique "Ajouter au journal" sur une réponse. */
  @Output() saveToJournal = new EventEmitter<string>();

  @ViewChild('messagesContainer') messagesContainer?: ElementRef<HTMLDivElement>;

  messages: ChatMessage[] = [];
  currentAssistantText = '';
  input = '';
  isStreaming = false;
  error: string | null = null;

  private streamSub: Subscription | null = null;

  constructor(private aiChat: AiChatService) {}

  ngOnChanges(changes: SimpleChanges): void {
    // Reset complet si on change de session (changement d'instance jouée).
    if (changes['sessionId'] && !changes['sessionId'].firstChange) {
      this.cancelStream();
      this.messages = [];
      this.currentAssistantText = '';
      this.error = null;
    }
  }

  send(): void {
    const text = this.input.trim();
    if (!text || this.isStreaming || !this.sessionId) return;

    this.messages = [...this.messages, { role: 'user', content: text }];
    this.input = '';
    this.error = null;
    this.startStream();
  }

  private startStream(): void {
    this.isStreaming = true;
    this.currentAssistantText = '';
    this.scrollToBottomSoon();

    this.streamSub = this.aiChat.streamChatForSession(this.sessionId, this.messages).subscribe({
      next: (event) => {
        if (event.type === 'token') {
          this.currentAssistantText += event.value;
          this.scrollToBottomSoon();
        } else if (event.type === 'done') {
          this.finishAssistantMessage();
        }
      },
      error: (err: unknown) => {
        const message = err instanceof Error ? err.message : 'Erreur inconnue';
        this.error = `Erreur IA : ${message}`;
        this.isStreaming = false;
        this.streamSub = null;
      },
      complete: () => {
        this.finishAssistantMessage();
      }
    });
  }

  private finishAssistantMessage(): void {
    if (this.currentAssistantText.trim()) {
      this.messages = [...this.messages, { role: 'assistant', content: this.currentAssistantText }];
    }
    this.currentAssistantText = '';
    this.isStreaming = false;
    this.streamSub = null;
    this.scrollToBottomSoon();
  }

  cancelStream(): void {
    if (this.streamSub) {
      this.streamSub.unsubscribe();
      this.streamSub = null;
    }
    // On garde ce qui a déjà été streamé : utile si l'IA partait dans le mur.
    if (this.currentAssistantText.trim()) {
      this.messages = [...this.messages, { role: 'assistant', content: this.currentAssistantText + ' [interrompu]' }];
    }
    this.currentAssistantText = '';
    this.isStreaming = false;
  }

  clearConversation(): void {
    this.cancelStream();
    this.messages = [];
    this.error = null;
  }

  onSaveToJournal(content: string): void {
    if (!this.canSaveToJournal) return;
    this.saveToJournal.emit(content);
  }

  /** Scroll vers le bas après cycle de change detection — preuve d'affichage du dernier token. */
  private scrollToBottomSoon(): void {
    queueMicrotask(() => {
      const el = this.messagesContainer?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    });
  }

  ngOnDestroy(): void {
    this.cancelStream();
  }
}
