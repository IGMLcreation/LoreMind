import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { forkJoin } from 'rxjs';
import { LucideAngularModule, ArrowLeft } from 'lucide-angular';
import { CampaignService } from '../../../services/campaign.service';
import { CharacterService } from '../../../services/character.service';
import { NpcService } from '../../../services/npc.service';
import { PlaythroughService } from '../../../services/playthrough.service';
import { LayoutService } from '../../../services/layout.service';
import { PageTitleService } from '../../../services/page-title.service';
import { Playthrough } from '../../../services/campaign.model';
import { loadCampaignTreeData, buildCampaignSidebarConfig } from '../../campaign-tree.helper';
import { PlaythroughFlagsManagerComponent } from '../../../shared/playthrough-flags-manager/playthrough-flags-manager.component';

/**
 * Page dédiée aux "Faits" d'une Partie.
 * Route : /campaigns/:campaignId/playthroughs/:playthroughId/flags
 */
@Component({
  selector: 'app-playthrough-flags-page',
  standalone: true,
  imports: [CommonModule, RouterModule, LucideAngularModule, PlaythroughFlagsManagerComponent],
  templateUrl: './playthrough-flags-page.component.html',
  styleUrls: ['./playthrough-flags-page.component.scss']
})
export class PlaythroughFlagsPageComponent implements OnInit, OnDestroy {
  readonly ArrowLeft = ArrowLeft;

  campaignId = '';
  playthroughId = '';
  playthrough: Playthrough | null = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private campaignService: CampaignService,
    private characterService: CharacterService,
    private npcService: NpcService,
    private playthroughService: PlaythroughService,
    private layoutService: LayoutService,
    private pageTitleService: PageTitleService
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
      treeData: loadCampaignTreeData(this.campaignService, this.campaignId, this.characterService, this.npcService),
      playthrough: this.playthroughService.getById(this.playthroughId)
    }).subscribe(({ campaign, allCampaigns, treeData, playthrough }) => {
      this.playthrough = playthrough;
      this.pageTitleService.set(`${playthrough.name} — Faits`);
      this.layoutService.show(buildCampaignSidebarConfig(campaign, allCampaigns, treeData, this.campaignId));
    });
  }

  back(): void {
    this.router.navigate(['/campaigns', this.campaignId]);
  }

  ngOnDestroy(): void {}
}
