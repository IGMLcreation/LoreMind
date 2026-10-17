import { Component, Input, computed, inject, signal } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule, Lightbulb } from 'lucide-angular';

import { WritingCoachService } from './writing-coach.service';

/**
 * Petit bouton 💡 posé à côté d'un libellé de champ : ouvre le panneau de
 * conseils d'écriture sur le sujet donné.
 *
 * Usage :
 *   <div class="field-label-row">
 *     <label for="x">Enjeux</label>
 *     <app-writing-coach-trigger topic="chapter.stakes"></app-writing-coach-trigger>
 *   </div>
 */
@Component({
  selector: 'app-writing-coach-trigger',
  imports: [LucideAngularModule, TranslatePipe],
  template: `
    <button type="button" class="coach-trigger"
      [class.active]="active()"
      [attr.aria-pressed]="active()"
      [title]="'writingCoach.triggerTitle' | translate"
      [attr.aria-label]="'writingCoach.triggerTitle' | translate"
      (click)="onClick($event)">
      <lucide-icon [img]="Lightbulb" [size]="14"></lucide-icon>
    </button>
  `,
  styles: [`
    :host { display: inline-flex; }
    .coach-trigger {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 24px;
      height: 24px;
      padding: 0;
      border-radius: 6px;
      border: 1px solid transparent;
      background: transparent;
      color: #6b7280;
      cursor: pointer;
      transition: color 0.15s, background 0.15s, border-color 0.15s;
    }
    .coach-trigger:hover { color: #fbbf24; background: rgba(251, 191, 36, 0.08); }
    .coach-trigger:focus-visible { outline: 2px solid #6c63ff; outline-offset: 1px; }
    .coach-trigger.active {
      color: #fbbf24;
      background: rgba(251, 191, 36, 0.12);
      border-color: rgba(251, 191, 36, 0.4);
    }
  `]
})
export class WritingCoachTriggerComponent {
  readonly Lightbulb = Lightbulb;
  private readonly coach = inject(WritingCoachService);
  private readonly topicKey = signal('');

  @Input({ required: true }) set topic(value: string) { this.topicKey.set(value); }

  readonly active = computed(() => this.coach.topic() === this.topicKey());

  onClick(event: MouseEvent): void {
    // Peut vivre dans l'en-tête d'une section repliable : ne pas la replier/déplier.
    event.stopPropagation();
    this.coach.toggle(this.topicKey());
  }
}
