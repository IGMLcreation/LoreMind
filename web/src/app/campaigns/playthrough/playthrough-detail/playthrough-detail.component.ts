import { Component, OnInit, OnDestroy } from '@angular/core';

import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { LucideAngularModule, ArrowLeft, Play, Flag, Users, Trash2, Pencil, Plus } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { CampaignService } from '../../../services/campaign.service';
import { CharacterService } from '../../../services/character.service';
import { NpcService } from '../../../services/npc.service';
import { RandomTableService } from '../../../services/random-table.service';
import { EnemyService } from '../../../services/enemy.service';
import { PlaythroughService } from '../../../services/playthrough.service';
import { SessionService } from '../../../services/session.service';
import { LayoutService } from '../../../services/layout.service';
import { PageTitleService } from '../../../services/page-title.service';
import { Playthrough } from '../../../services/campaign.model';
import { Session } from '../../../services/session.model';
import { Character } from '../../../services/character.model';
import { loadCampaignTreeData, buildCampaignSidebarConfig } from '../../campaign-tree.helper';
import { ConfirmDialogService } from '../../../shared/confirm-dialog/confirm-dialog.service';
import { ClocksManagerComponent } from '../../../shared/clocks-manager/clocks-manager.component';
import { SessionPrepPanelComponent } from '../../../shared/session-prep-panel/session-prep-panel.component';

/**
 * Vue détail d'une Partie (Playthrough).
 * Minimal MVP — affiche les infos, le lien vers les faits, la liste des sessions
 * et les PJ de cette Partie.
 */
@Component({
    selector: 'app-playthrough-detail',
    imports: [RouterModule, LucideAngularModule, TranslatePipe, ClocksManagerComponent, SessionPrepPanelComponent],
    templateUrl: './playthrough-detail.component.html',
    styleUrls: ['./playthrough-detail.component.scss']
})
export class PlaythroughDetailComponent implements OnInit, OnDestroy {
  readonly ArrowLeft = ArrowLeft;
  readonly Play = Play;
  readonly Flag = Flag;
  readonly Users = Users;
  readonly Trash2 = Trash2;
  readonly Pencil = Pencil;
  readonly Plus = Plus;

  campaignId = '';
  playthroughId = '';

  playthrough: Playthrough | null = null;
  sessions: Session[] = [];
  characters: Character[] = [];
  /** Session active de CETTE Partie (null si aucune). Plus de check global. */
  activeOnThis: Session | null = null;

  startingSession = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private campaignService: CampaignService,
    private characterService: CharacterService,
    private npcService: NpcService,
    private randomTableService: RandomTableService,
    private enemyService: EnemyService,
    private playthroughService: PlaythroughService,
    private sessionService: SessionService,
    private layoutService: LayoutService,
    private pageTitleService: PageTitleService,
    private confirmDialog: ConfirmDialogService,
    private translate: TranslateService
  ) {}

  ngOnInit(): void {
    this.route.paramMap.subscribe(pm => {
      const cid = pm.get('campaignId')!;
      const pid = pm.get('playthroughId')!;
      if (cid !== this.campaignId || pid !== this.playthroughId) {
        this.campaignId = cid;
        this.playthroughId = pid;
        this.load();
      }
    });
  }

  private load(): void {
    forkJoin({
      campaign: this.campaignService.getCampaignById(this.campaignId),
      allCampaigns: this.campaignService.getAllCampaigns(),
      treeData: loadCampaignTreeData(this.campaignService, this.campaignId, this.npcService, this.randomTableService, this.enemyService),
      playthrough: this.playthroughService.getById(this.playthroughId),
      sessions: this.sessionService.getSessions(this.playthroughId).pipe(catchError(() => of([] as Session[]))),
      characters: this.characterService.getByPlaythrough(this.playthroughId).pipe(catchError(() => of([] as Character[]))),
      activeOnThis: this.sessionService.getActiveByPlaythrough(this.playthroughId).pipe(catchError(() => of(null)))
    }).subscribe(({ campaign, allCampaigns, treeData, playthrough, sessions, characters, activeOnThis }) => {
      this.playthrough = playthrough;
      this.sessions = sessions;
      this.characters = characters;
      this.activeOnThis = activeOnThis;
      this.pageTitleService.set(`${playthrough.name} — ${campaign.name}`);
      this.layoutService.show(buildCampaignSidebarConfig(campaign, allCampaigns, treeData, this.campaignId, this.translate));
    });
  }

  startSession(): void {
    if (this.startingSession || this.activeOnThis) return;
    this.startingSession = true;
    this.sessionService.startSession(this.playthroughId).subscribe({
      next: s => { this.startingSession = false; this.router.navigate(['/sessions', s.id]); },
      error: () => { this.startingSession = false; }
    });
  }

  openSession(s: Session): void {
    this.router.navigate(['/sessions', s.id]);
  }

  /** Crée un PJ rattaché à CETTE Partie (route scoping Playthrough). */
  createCharacter(): void {
    this.router.navigate(['/campaigns', this.campaignId, 'playthroughs', this.playthroughId, 'characters', 'create']);
  }

  openFlags(): void {
    this.router.navigate(['/campaigns', this.campaignId, 'playthroughs', this.playthroughId, 'flags']);
  }

  back(): void {
    this.router.navigate(['/campaigns', this.campaignId]);
  }

  delete(): void {
    if (!this.playthrough) return;
    this.playthroughService.deletionImpact(this.playthroughId).subscribe({
      next: impact => {
        const parts: string[] = [];
        if (impact.sessions > 0) parts.push(this.translate.instant('playthroughDetail.impactSessions', { n: impact.sessions }));
        if (impact.characters > 0) parts.push(this.translate.instant('playthroughDetail.impactCharacters', { n: impact.characters }));
        if (impact.flags > 0) parts.push(this.translate.instant('playthroughDetail.impactFlags', { n: impact.flags }));
        if (impact.progressions > 0) parts.push(this.translate.instant('playthroughDetail.impactProgressions', { n: impact.progressions }));
        const details: string[] = [];
        if (parts.length) details.push(this.translate.instant('playthroughDetail.deleteCascade', { parts: parts.join(', ') }));
        details.push(this.translate.instant('playthroughDetail.irreversible'));
        this.confirmDialog.confirm({
          title: this.translate.instant('playthroughDetail.deleteTitle'),
          message: this.translate.instant('playthroughDetail.deleteMessage', { name: this.playthrough?.name }),
          details,
          confirmLabel: this.translate.instant('common.delete'),
          variant: 'danger'
        }).then(ok => {
          if (!ok) return;
          this.playthroughService.delete(this.playthroughId).subscribe({
            next: () => this.router.navigate(['/campaigns', this.campaignId])
          });
        });
      }
    });
  }

  ngOnDestroy(): void {}
}
