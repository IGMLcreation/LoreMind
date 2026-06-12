import { Component, OnDestroy, OnInit } from '@angular/core';

import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { LucideAngularModule, ArrowLeft, Edit3, Sparkles, Link2 } from 'lucide-angular';
import { NpcService } from '../../../services/npc.service';
import { CampaignService } from '../../../services/campaign.service';
import { GameSystemService } from '../../../services/game-system.service';
import { PageService } from '../../../services/page.service';
import { CampaignSidebarService } from '../../../services/campaign-sidebar.service';
import { TemplateField } from '../../../services/template.model';
import { Npc } from '../../../services/npc.model';
import { Page } from '../../../services/page.model';
import { PersonaViewComponent } from '../../../shared/persona-view/persona-view.component';
import { AiChatDrawerComponent } from '../../../shared/ai-chat-drawer/ai-chat-drawer.component';

/**
 * Vue lecture seule "WorldAnvil" d'une fiche PNJ.
 * Route : /campaigns/:campaignId/npcs/:npcId
 */
@Component({
    selector: 'app-npc-view',
    imports: [LucideAngularModule, RouterLink, PersonaViewComponent, AiChatDrawerComponent],
    templateUrl: './npc-view.component.html',
    styleUrls: ['./npc-view.component.scss']
})
export class NpcViewComponent implements OnInit, OnDestroy {
  readonly ArrowLeft = ArrowLeft;
  readonly Edit3 = Edit3;
  readonly Sparkles = Sparkles;
  readonly Link2 = Link2;

  campaignId: string | null = null;
  npcId: string | null = null;

  npc: Npc | null = null;
  templateFields: TemplateField[] = [];
  /** Lore lié à la campagne (résolution des chips de pages liées). */
  loreId: string | null = null;
  /** Pages du lore lié, indexées pour résoudre les titres des chips. */
  private lorePagesById = new Map<string, Page>();

  chatOpen = false;
  toggleChat(): void { this.chatOpen = !this.chatOpen; }

  private paramsSub?: Subscription;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private service: NpcService,
    private campaignService: CampaignService,
    private gameSystemService: GameSystemService,
    private pageService: PageService,
    private campaignSidebar: CampaignSidebarService
  ) {}

  ngOnInit(): void {
    // S'abonner aux paramMap (pas le snapshot) : quand on passe d'un PNJ à un autre,
    // Angular RÉUTILISE le composant (même route) → ngOnInit ne re-tourne pas. Sans ce
    // subscribe, la fiche du centre resterait figée sur l'ancien PNJ.
    this.paramsSub = this.route.paramMap.subscribe(params => {
      const newCampaignId = params.get('campaignId');
      this.npcId = params.get('npcId');

      // Recharge la fiche à CHAQUE changement de PNJ.
      this.chatOpen = false;
      if (this.npcId) {
        this.service.getById(this.npcId).subscribe({
          next: n => { this.npc = n; },
          error: () => this.back()
        });
      }

      // Sidebar + template du système : seulement quand la campagne change (inutile
      // de les recharger à chaque switch de PNJ d'une même campagne).
      if (newCampaignId && newCampaignId !== this.campaignId) {
        this.campaignId = newCampaignId;
        this.campaignSidebar.show(this.campaignId);
        this.campaignService.getCampaignById(this.campaignId).subscribe(camp => {
          if (camp.gameSystemId) {
            this.gameSystemService.getById(camp.gameSystemId).subscribe(gs => {
              this.templateFields = gs.npcTemplate ?? [];
            });
          }
          // Lore lié → référentiel de pages pour résoudre les chips de liens.
          if (camp.loreId) {
            this.loreId = camp.loreId;
            this.pageService.getByLoreId(camp.loreId).subscribe(pages => {
              this.lorePagesById = new Map(pages.map(p => [p.id!, p]));
            });
          }
        });
      } else if (newCampaignId) {
        this.campaignId = newCampaignId;
      }
    });
  }

  ngOnDestroy(): void {
    this.paramsSub?.unsubscribe();
  }

  /** Titre d'une page de lore liée (pour les chips). */
  titleOfPage(pageId: string): string {
    return this.lorePagesById.get(pageId)?.title ?? '(page supprimée)';
  }

  edit(): void {
    if (this.campaignId && this.npcId) {
      this.router.navigate(['/campaigns', this.campaignId, 'npcs', this.npcId, 'edit']);
    }
  }

  back(): void {
    if (this.campaignId) {
      this.router.navigate(['/campaigns', this.campaignId]);
    } else {
      this.router.navigate(['/campaigns']);
    }
  }
}
