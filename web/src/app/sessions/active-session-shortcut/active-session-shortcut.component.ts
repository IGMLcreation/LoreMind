import { Component, DestroyRef, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs/operators';
import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule, ChevronDown, ChevronUp } from 'lucide-angular';
import { ActiveSessionEntry, ActiveSessionsService } from '../../services/active-sessions.service';

/**
 * Raccourci « Séance en cours » du menu principal : visible dès qu'une séance tourne,
 * un clic y ramène depuis n'importe quel écran. Plusieurs séances (plusieurs tables) :
 * le bouton déplie la liste, la plus récente en tête.
 *
 * <p>Composant autonome (styles propres) pour pouvoir le réutiliser tel quel dans la
 * barre haute mobile.</p>
 */
@Component({
  selector: 'app-active-session-shortcut',
  imports: [LucideAngularModule, TranslatePipe],
  templateUrl: './active-session-shortcut.component.html',
  styleUrls: ['./active-session-shortcut.component.scss']
})
export class ActiveSessionShortcutComponent implements OnInit {
  readonly ChevronDown = ChevronDown;
  readonly ChevronUp = ChevronUp;

  entries: ActiveSessionEntry[] = [];
  expanded = false;
  currentUrl = '';

  constructor(
    private activeSessions: ActiveSessionsService,
    private router: Router,
    private destroyRef: DestroyRef
  ) {}

  ngOnInit(): void {
    this.currentUrl = this.router.url;
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(e => {
      this.currentUrl = e.urlAfterRedirects;
      this.expanded = false;
    });
    this.activeSessions.active$.pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(list => {
        this.entries = list;
        if (list.length < 2) this.expanded = false;
      });
  }

  /** Une seule séance : on y va ; plusieurs : on déplie la liste. */
  onMainClick(): void {
    if (this.entries.length === 1) {
      this.open(this.entries[0]);
    } else {
      this.expanded = !this.expanded;
    }
  }

  open(entry: ActiveSessionEntry): void {
    this.router.navigate(['/sessions', entry.session.id]);
  }

  isCurrent(entry: ActiveSessionEntry): boolean {
    return this.currentUrl.split(/[?#]/)[0] === `/sessions/${entry.session.id}`;
  }

  /** « Partie · Campagne » (ce qui est connu). */
  contextOf(entry: ActiveSessionEntry): string {
    return [entry.playthroughName, entry.campaignName].filter(Boolean).join(' · ');
  }
}
