import { Component, ElementRef, EventEmitter, Input, Output, ViewChild, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, X, Send, Sparkles, Lightbulb, Wand2 } from 'lucide-angular';
import { Subscription } from 'rxjs';
import { AiChatService, ChatMessage, NarrativeEntityType } from '../../services/ai-chat.service';

/**
 * Action primaire optionnelle rendue en gros bouton au-dessus des suggestions.
 * Utilisée pour les actions "spéciales" qui NE passent PAS par le chat
 * (ex: "Remplir automatiquement tous les champs" → déclenche le one-shot b4).
 */
export interface ChatPrimaryAction {
  label: string;
}

/**
 * Drawer de chat IA réutilisable — panneau fixe à droite de l'écran.
 *
 * Usage minimal :
 *   <app-ai-chat-drawer
 *     [loreId]="loreId"
 *     [isOpen]="chatOpen"
 *     [quickSuggestions]="['Développe l'histoire', ...]"
 *     (close)="chatOpen = false">
 *   </app-ai-chat-drawer>
 *
 * Contrainte de design : conversation éphémère (on perd tout à la fermeture
 * ou à la destruction du composant — choix MVP assumé).
 */
@Component({
  selector: 'app-ai-chat-drawer',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideAngularModule],
  templateUrl: './ai-chat-drawer.component.html',
  styleUrls: ['./ai-chat-drawer.component.scss']
})
export class AiChatDrawerComponent implements OnDestroy {
  readonly X = X;
  readonly Send = Send;
  readonly Sparkles = Sparkles;
  readonly Lightbulb = Lightbulb;
  readonly Wand2 = Wand2;

  /**
   * Mode Lore : fournir `loreId` (et optionnellement `pageId`).
   * Mode Campagne : fournir `campaignId` (et optionnellement `entityType`+`entityId`).
   * Les deux modes sont exclusifs — si `campaignId` est non-vide, on route
   * vers l'endpoint Campagne, sinon vers l'endpoint Lore.
   */
  @Input() loreId = '';
  /**
   * Optionnel : ID d'une page précise en cours d'édition. Si fourni, le
   * backend focalise l'IA sur cette page (template, champs, valeurs) via
   * un bloc "PAGE EN COURS" dans le system prompt. Sans cet ID, le chat
   * reste générique au Lore.
   */
  @Input() pageId: string | null = null;

  /** ID de la Campagne — active le mode chat Campagne si non-vide. */
  @Input() campaignId: string | null = null;
  /** Optionnel : "arc"|"chapter"|"scene" — focalise l'IA sur une entité narrative. */
  @Input() entityType: NarrativeEntityType | null = null;
  /** Optionnel : ID de l'entité narrative en cours d'édition. */
  @Input() entityId: string | null = null;
  @Input() isOpen = false;
  /** Texte accueil affiché au premier ouverture (avant tout échange). */
  @Input() welcomeMessage = 'Bonjour ! Je peux vous aider à développer cette page. Que souhaitez-vous créer ?';
  /** Suggestions rapides cliquables en bas (hardcodées par le parent, MVP). */
  @Input() quickSuggestions: string[] = [];
  /** Action primaire optionnelle (ex: "Remplir automatiquement") — ne passe PAS par le chat. */
  @Input() primaryAction: ChatPrimaryAction | null = null;
  /**
   * Instructions système supplémentaires injectées en tête de la conversation
   * envoyée au backend, INVISIBLES côté UI. Usage : mode wizard, où on veut
   * contextualiser l'IA (template cible, format JSON attendu) sans polluer
   * l'historique visuel.
   */
  @Input() systemPromptAddon: string | null = null;

  @Output() close = new EventEmitter<void>();
  /** Émis au clic sur l'action primaire — le parent gère entièrement (one-shot, etc.). */
  @Output() primaryActionClick = new EventEmitter<void>();
  /** Émis à chaque fin de réponse assistant — utile pour parser côté parent (ex: bloc <values> du wizard). */
  @Output() assistantReply = new EventEmitter<string>();

  @ViewChild('messagesContainer') messagesContainer?: ElementRef<HTMLDivElement>;

  /** Conversation en cours (user + assistant). Le welcome n'est pas dedans — rendu séparément. */
  messages: ChatMessage[] = [];
  /** Texte en cours de streaming (écrit token par token, pas encore poussé dans `messages`). */
  currentAssistantText = '';
  /** Champ de saisie. */
  input = '';
  /** Stream en cours ? Désactive le bouton envoyer + les suggestions rapides. */
  isStreaming = false;
  /** Dernier message d'erreur (affiché dans une bannière locale au drawer). */
  errorMessage: string | null = null;

  private streamSub: Subscription | null = null;

  constructor(private readonly chatService: AiChatService) {}

  // --- Handlers UI --------------------------------------------------------

  onClose(): void {
    this.abortStream();
    this.close.emit();
  }

  /** Envoi explicite depuis le formulaire (Entrée ou bouton envoyer). */
  send(): void {
    const text = this.input.trim();
    if (!text || this.isStreaming) return;
    this.sendUserMessage(text);
    this.input = '';
  }

  /** Envoi depuis une suggestion rapide (bouton cliquable en bas). */
  useQuickSuggestion(suggestion: string): void {
    if (this.isStreaming) return;
    this.sendUserMessage(suggestion);
  }

  /** Clic sur l'action primaire — on délègue entièrement au parent. */
  onPrimaryAction(): void {
    if (this.isStreaming) return;
    this.primaryActionClick.emit();
  }

  // --- Logique envoi + streaming -----------------------------------------

  private sendUserMessage(text: string): void {
    this.errorMessage = null;
    this.messages.push({ role: 'user', content: text });
    this.currentAssistantText = '';
    this.isStreaming = true;
    this.scrollToBottom();

    // Construit la liste effectivement envoyée au backend : systemPromptAddon
    // (si fourni) préfixé, puis l'historique visible. Le system n'est PAS stocké
    // dans this.messages → reste invisible côté UI.
    const payload = this.systemPromptAddon
      ? [{ role: 'system' as const, content: this.systemPromptAddon }, ...this.messages]
      : this.messages;

    const stream$ = this.campaignId
      ? this.chatService.streamChatForCampaign(this.campaignId, payload, this.entityType, this.entityId)
      : this.chatService.streamChat(this.loreId, payload, this.pageId);

    this.streamSub = stream$.subscribe({
      next: (event) => {
        if (event.type === 'token') {
          this.currentAssistantText += event.value;
          this.scrollToBottom();
        }
        // 'done' : l'Observable va compléter → géré par complete()
      },
      error: (err) => {
        this.isStreaming = false;
        this.errorMessage = err?.message ?? 'Erreur inconnue.';
        this.currentAssistantText = '';
      },
      complete: () => {
        // On fige le texte streamé en message assistant réel, puis on reset le buffer.
        const reply = this.currentAssistantText;
        if (reply) {
          this.messages.push({ role: 'assistant', content: reply });
          this.assistantReply.emit(reply);
        }
        this.currentAssistantText = '';
        this.isStreaming = false;
        this.scrollToBottom();
      }
    });
  }

  private abortStream(): void {
    this.streamSub?.unsubscribe();
    this.streamSub = null;
    this.isStreaming = false;
    this.currentAssistantText = '';
  }

  /**
   * Scroll différé au prochain tick : donne à Angular le temps de rendre
   * le nouveau contenu avant qu'on mesure/ajuste la position du scroll.
   */
  private scrollToBottom(): void {
    queueMicrotask(() => {
      const el = this.messagesContainer?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    });
  }

  ngOnDestroy(): void {
    this.abortStream();
  }
}
