import { Injectable } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { BehaviorSubject, Observable, forkJoin, merge, of } from 'rxjs';
import { catchError, filter, map, startWith, switchMap } from 'rxjs/operators';
import { SessionService } from './session.service';
import { PlaythroughService } from './playthrough.service';
import { CampaignService } from './campaign.service';
import { Session } from './session.model';

/** Séance en cours enrichie de quoi l'identifier (Partie + Campagne). */
export interface ActiveSessionEntry {
  session: Session;
  playthroughName: string | null;
  campaignId: string | null;
  campaignName: string | null;
}

interface PlaythroughLabel {
  playthroughName: string | null;
  campaignId: string | null;
  campaignName: string | null;
}

/**
 * État partagé des séances en cours (toutes Parties confondues), la plus récente d'abord.
 *
 * <p>Alimente le raccourci « Séance en cours » du menu principal et le bloc « Jouer »
 * du hub campagne : une séance ne doit plus se perdre au fond d'une page. Rafraîchi à
 * chaque navigation et après chaque démarrage/fin/suppression ({@link SessionService.changed$}).
 * Les libellés Partie/Campagne sont mis en cache (une séance active change rarement de Partie).</p>
 */
@Injectable({ providedIn: 'root' })
export class ActiveSessionsService {
  private readonly subject = new BehaviorSubject<ActiveSessionEntry[]>([]);
  readonly active$: Observable<ActiveSessionEntry[]> = this.subject.asObservable();

  private readonly labels = new Map<string, PlaythroughLabel>();

  constructor(
    router: Router,
    private sessionService: SessionService,
    private playthroughService: PlaythroughService,
    private campaignService: CampaignService
  ) {
    merge(
      router.events.pipe(filter(e => e instanceof NavigationEnd)),
      this.sessionService.changed$
    ).pipe(
      startWith(null),
      // switchMap : une navigation rapide annule le fetch précédent (pas de réponse périmée).
      switchMap(() => this.fetch())
    ).subscribe(list => this.subject.next(list));
  }

  /** Force un rafraîchissement (ex. après une action hors SessionService). */
  refresh(): void {
    this.fetch().subscribe(list => this.subject.next(list));
  }

  private fetch(): Observable<ActiveSessionEntry[]> {
    return this.sessionService.getActiveSessions().pipe(
      // Back indisponible / ancienne version : on garde l'état connu plutôt que de faire clignoter.
      catchError(() => of(null)),
      switchMap(sessions => {
        if (!sessions) return of(this.subject.value);
        if (sessions.length === 0) return of([] as ActiveSessionEntry[]);
        return forkJoin(sessions.map(s => this.labelOf(s.playthroughId).pipe(
          map(label => ({ session: s, ...label }))
        )));
      })
    );
  }

  private labelOf(playthroughId: string): Observable<PlaythroughLabel> {
    const cached = this.labels.get(playthroughId);
    if (cached) return of(cached);
    const unknown: PlaythroughLabel = { playthroughName: null, campaignId: null, campaignName: null };
    return this.playthroughService.getById(playthroughId).pipe(
      switchMap(p => this.campaignService.getCampaignById(p.campaignId).pipe(
        map(c => ({ playthroughName: p.name, campaignId: p.campaignId, campaignName: c.name })),
        catchError(() => of({ ...unknown, playthroughName: p.name, campaignId: p.campaignId }))
      )),
      map(label => { this.labels.set(playthroughId, label); return label; }),
      catchError(() => of(unknown))
    );
  }
}
