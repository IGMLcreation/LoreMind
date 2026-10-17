import { Injectable, signal } from '@angular/core';

/**
 * Coach d'écriture (Mentor d'écriture, phase M1 — voir docs/conception-mentor-ecriture.md).
 *
 * État global du panneau de conseils : un seul sujet ouvert à la fois, identifié
 * par une clé « entité.champ » (ex. `scene.choices`) qui pointe vers
 * `writingCoach.topics.<clé>` dans les fichiers i18n. Contenu 100 % statique :
 * pas d'IA, pas d'appel réseau.
 */
@Injectable({ providedIn: 'root' })
export class WritingCoachService {
  private readonly current = signal<string | null>(null);

  /** Sujet actuellement affiché, ou null si le panneau est fermé. */
  readonly topic = this.current.asReadonly();

  open(topic: string): void {
    this.current.set(topic);
  }

  close(): void {
    this.current.set(null);
  }

  /** Re-cliquer sur le 💡 du sujet ouvert referme le panneau. */
  toggle(topic: string): void {
    this.current.set(this.current() === topic ? null : topic);
  }
}
