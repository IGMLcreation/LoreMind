import { Injectable } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { forkJoin, Subscription } from 'rxjs';
import { CampaignService } from './campaign.service';
import { NpcService } from './npc.service';
import { RandomTableService } from './random-table.service';
import { EnemyService } from './enemy.service';
import { LayoutService } from './layout.service';
import { loadCampaignTreeData, buildCampaignSidebarConfig } from '../campaigns/campaign-tree.helper';

/**
 * Service utilitaire qui charge et affiche la sidebar secondaire d'une campagne
 * (arbre arcs/chapitres/scenes + PJ/PNJ + items globaux).
 *
 * Centralise un pattern dupliquait dans 13+ composants (arc-view/edit/create,
 * chapter-*, scene-*, character-view/edit, npc-view/edit, campaign-detail) :
 * meme forkJoin de 3 sources + meme config layoutService.show().
 *
 * Utilisation :
 * ```ts
 * constructor(private campaignSidebar: CampaignSidebarService) {}
 * ngOnInit() { this.campaignSidebar.show(this.campaignId); }
 * ```
 */
@Injectable({ providedIn: 'root' })
export class CampaignSidebarService {
  constructor(
    private campaignService: CampaignService,
    private npcService: NpcService,
    private randomTableService: RandomTableService,
    private enemyService: EnemyService,
    private layoutService: LayoutService,
    private translate: TranslateService
  ) {}

  /**
   * Charge les donnees et configure la sidebar secondaire pour la campagne.
   * Renvoie la Subscription pour permettre au caller de l'annuler s'il le
   * souhaite (rarement utile vu que les requetes terminent vite).
   */
  show(campaignId: string): Subscription {
    return forkJoin({
      campaign: this.campaignService.getCampaignById(campaignId),
      allCampaigns: this.campaignService.getAllCampaigns(),
      // L'arbre agrégé embarque déjà quêtes + readiness (pastilles) en une requête.
      treeData: loadCampaignTreeData(
        this.campaignService,
        campaignId,
        this.npcService,
        this.randomTableService,
        this.enemyService
      )
    }).subscribe(({ campaign, allCampaigns, treeData }) => {
      this.layoutService.show(
        buildCampaignSidebarConfig(campaign, allCampaigns, treeData, campaignId, this.translate)
      );
    });
  }
}
