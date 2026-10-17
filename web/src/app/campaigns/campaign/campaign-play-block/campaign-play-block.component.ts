import { Component, DestroyRef, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule, Play, Plus } from 'lucide-angular';
import { ActiveSessionsService } from '../../../services/active-sessions.service';
import { SessionService } from '../../../services/session.service';
import { Playthrough } from '../../../services/campaign.model';
import { Session } from '../../../services/session.model';
import { PlayTarget, computePlayTarget } from './play-target.util';

/**
 * Bloc « Jouer » en tête du hub campagne : reprendre la séance en cours, ou en démarrer
 * une sur la Partie la plus récente (ou en créer une s'il n'y en a aucune). La liste
 * complète des Parties reste en bas du hub ; ce bloc n'est qu'un accès direct.
 */
@Component({
  selector: 'app-campaign-play-block',
  imports: [FormsModule, LucideAngularModule, TranslatePipe],
  templateUrl: './campaign-play-block.component.html',
  styleUrls: ['./campaign-play-block.component.scss']
})
export class CampaignPlayBlockComponent implements OnInit, OnChanges {
  readonly Play = Play;
  readonly Plus = Plus;

  @Input() playthroughs: Playthrough[] = [];
  /** Création de Partie déléguée au hub (même nom par défaut, même navigation). */
  @Input() creatingPlaythrough = false;
  @Output() createPlaythrough = new EventEmitter<void>();

  target: PlayTarget = { kind: 'create' };
  selectedPlaythroughId = '';
  starting = false;

  private activeSessions: Session[] = [];

  constructor(
    private activeSessionsService: ActiveSessionsService,
    private sessionService: SessionService,
    private router: Router,
    private destroyRef: DestroyRef
  ) {}

  ngOnInit(): void {
    this.activeSessionsService.active$.pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(list => {
        this.activeSessions = list.map(e => e.session);
        this.recompute();
      });
  }

  ngOnChanges(): void {
    this.recompute();
  }

  private recompute(): void {
    this.target = computePlayTarget(this.playthroughs, this.activeSessions);
    if (this.target.kind === 'start') {
      const ids = this.target.playthroughs.map(p => p.id);
      if (!ids.includes(this.selectedPlaythroughId)) {
        this.selectedPlaythroughId = this.target.defaultPlaythroughId;
      }
    }
  }

  resume(session: Session): void {
    this.router.navigate(['/sessions', session.id]);
  }

  start(): void {
    if (this.starting || !this.selectedPlaythroughId) return;
    this.starting = true;
    this.sessionService.startSession(this.selectedPlaythroughId).subscribe({
      next: s => { this.starting = false; this.router.navigate(['/sessions', s.id]); },
      error: () => { this.starting = false; }
    });
  }
}
