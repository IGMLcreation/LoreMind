import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { LucideAngularModule, ArrowLeft, Play, Flag, Users, Trash2, Pencil, Plus } from 'lucide-angular';
import { CampaignService } from '../../../services/campaign.service';
import { CharacterService } from '../../../services/character.service';
import { NpcService } from '../../../services/npc.service';
import { RandomTableService } from '../../../services/random-table.service';
import { PlaythroughService } from '../../../services/playthrough.service';
import { SessionService } from '../../../services/session.service';
import { LayoutService } from '../../../services/layout.service';
import { PageTitleService } from '../../../services/page-title.service';
import { Playthrough } from '../../../services/campaign.model';
import { Session } from '../../../services/session.model';
import { Character } from '../../../services/character.model';
import { loadCampaignTreeData, buildCampaignSidebarConfig } from '../../campaign-tree.helper';
import { ConfirmDialogService } from '../../../shared/confirm-dialog/confirm-dialog.service';

/**
 * Vue détail d'une Partie (Playthrough).
 * Minimal MVP — affiche les infos, le lien vers les faits, la liste des sessions
 * et les PJ de cette Partie.
 */
@Component({
  selector: 'app-playthrough-detail',
  standalone: true,
  imports: [CommonModule, RouterModule, LucideAngularModule],
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
    private playthroughService: PlaythroughService,
    private sessionService: SessionService,
    private layoutService: LayoutService,
    private pageTitleService: PageTitleService,
    private confirmDialog: ConfirmDialogService
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
      treeData: loadCampaignTreeData(this.campaignService, this.campaignId, this.characterService, this.npcService, this.randomTableService),
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
      this.layoutService.show(buildCampaignSidebarConfig(campaign, allCampaigns, treeData, this.campaignId));
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
        if (impact.sessions > 0) parts.push(`${impact.sessions} session(s)`);
        if (impact.characters > 0) parts.push(`${impact.characters} PJ`);
        if (impact.flags > 0) parts.push(`${impact.flags} fait(s)`);
        if (impact.progressions > 0) parts.push(`${impact.progressions} progression(s) de quête`);
        const details: string[] = [];
        if (parts.length) details.push(`Cette action supprimera aussi : ${parts.join(', ')}.`);
        details.push('Cette action est irréversible.');
        this.confirmDialog.confirm({
          title: 'Supprimer la Partie',
          message: `Supprimer "${this.playthrough?.name}" ?`,
          details,
          confirmLabel: 'Supprimer',
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
