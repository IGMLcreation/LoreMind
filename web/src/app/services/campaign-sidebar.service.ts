import { Injectable } from '@angular/core';
import { forkJoin, Subscription } from 'rxjs';
import { CampaignService } from './campaign.service';
import { CharacterService } from './character.service';
import { NpcService } from './npc.service';
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
    private characterService: CharacterService,
    private npcService: NpcService,
    private layoutService: LayoutService
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
      treeData: loadCampaignTreeData(
        this.campaignService,
        campaignId,
        this.characterService,
        this.npcService
      )
    }).subscribe(({ campaign, allCampaigns, treeData }) => {
      this.layoutService.show(buildCampaignSidebarConfig(campaign, allCampaigns, treeData, campaignId));
    });
  }
}
