import { Component, DestroyRef, ElementRef, HostBinding, HostListener, Input, OnInit, booleanAttribute } from '@angular/core';
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
 * <p>Composant autonome (styles propres). En mode {@code compact} (barre haute mobile) :
 * une pastille de 44 px, et la liste s'ouvre en menu déroulant (Échap / tap à côté la ferme).</p>
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

  /** Barre haute mobile : pastille seule, liste en menu déroulant. */
  @Input({ transform: booleanAttribute }) @HostBinding('class.compact') compact = false;

  entries: ActiveSessionEntry[] = [];
  expanded = false;
  currentUrl = '';

  constructor(
    private activeSessions: ActiveSessionsService,
    private router: Router,
    private destroyRef: DestroyRef,
    private host: ElementRef<HTMLElement>
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

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.compact && this.expanded && !this.host.nativeElement.contains(event.target as Node)) {
      this.expanded = false;
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.expanded) this.expanded = false;
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
