import { Component, DestroyRef, HostListener, inject } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { NavigationStart, Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { filter, map, of, switchMap } from 'rxjs';
import { LucideAngularModule, Lightbulb, X } from 'lucide-angular';

import { WritingCoachService } from './writing-coach.service';

/** Fiche de conseils d'un champ, telle que rangée dans `writingCoach.topics.<clé>`. */
export interface WritingCoachTopic {
  title: string;
  purpose: string;
  questions: string[];
  example: string;
  pitfall: string;
}

/**
 * Panneau latéral du coach d'écriture, monté une seule fois dans le shell
 * (comme `app-confirm-dialog-host`). Non modal : on peut continuer à écrire
 * dans le formulaire pendant qu'il est ouvert.
 *
 * Posture « mentor d'abord » : il explique et questionne, il n'écrit jamais
 * à la place du MJ (l'IA qui écrit reste le bouton « Étoffer avec l'IA »).
 */
@Component({
  selector: 'app-writing-coach-panel',
  imports: [LucideAngularModule, TranslatePipe],
  templateUrl: './writing-coach-panel.component.html',
  styleUrls: ['./writing-coach-panel.component.scss']
})
export class WritingCoachPanelComponent {
  readonly Lightbulb = Lightbulb;
  readonly X = X;

  private readonly coach = inject(WritingCoachService);
  private readonly translate = inject(TranslateService);

  readonly topic = this.coach.topic;

  /** Contenu du sujet ouvert, re-résolu au changement de langue. */
  readonly content = toSignal(
    toObservable(this.coach.topic).pipe(
      switchMap(key => key
        ? this.translate.stream(`writingCoach.topics.${key}`).pipe(map(value => toTopic(value)))
        : of(null))
    ),
    { initialValue: null }
  );

  constructor() {
    // Changer d'écran ferme le panneau : ses conseils concernaient l'ancien formulaire.
    inject(Router).events
      .pipe(filter(e => e instanceof NavigationStart), takeUntilDestroyed(inject(DestroyRef)))
      .subscribe(() => this.coach.close());
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.coach.topic()) this.coach.close();
  }

  close(): void {
    this.coach.close();
  }
}

/** Clé absente → ngx-translate renvoie la clé elle-même (string) : on n'affiche rien. */
function toTopic(value: unknown): WritingCoachTopic | null {
  if (!value || typeof value !== 'object') return null;
  const t = value as Partial<WritingCoachTopic>;
  return {
    title: t.title ?? '',
    purpose: t.purpose ?? '',
    questions: Array.isArray(t.questions) ? t.questions : [],
    example: t.example ?? '',
    pitfall: t.pitfall ?? ''
  };
}
