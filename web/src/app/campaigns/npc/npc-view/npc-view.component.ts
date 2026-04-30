import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { LucideAngularModule, ArrowLeft, Edit3, Sparkles } from 'lucide-angular';
import { NpcService } from '../../../services/npc.service';
import { CampaignService } from '../../../services/campaign.service';
import { GameSystemService } from '../../../services/game-system.service';
import { TemplateField } from '../../../services/template.model';
import { Npc } from '../../../services/npc.model';
import { PersonaViewComponent } from '../../../shared/persona-view/persona-view.component';
import { AiChatDrawerComponent } from '../../../shared/ai-chat-drawer/ai-chat-drawer.component';

/**
 * Vue lecture seule "WorldAnvil" d'une fiche PNJ.
 * Route : /campaigns/:campaignId/npcs/:npcId
 */
@Component({
  selector: 'app-npc-view',
  standalone: true,
  imports: [CommonModule, LucideAngularModule, PersonaViewComponent, AiChatDrawerComponent],
  templateUrl: './npc-view.component.html',
  styleUrls: ['./npc-view.component.scss']
})
export class NpcViewComponent implements OnInit {
  readonly ArrowLeft = ArrowLeft;
  readonly Edit3 = Edit3;
  readonly Sparkles = Sparkles;

  campaignId: string | null = null;
  npcId: string | null = null;

  npc: Npc | null = null;
  templateFields: TemplateField[] = [];

  chatOpen = false;
  toggleChat(): void { this.chatOpen = !this.chatOpen; }

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private service: NpcService,
    private campaignService: CampaignService,
    private gameSystemService: GameSystemService
  ) {}

  ngOnInit(): void {
    const params = this.route.snapshot.paramMap;
    this.campaignId = params.get('campaignId');
    this.npcId = params.get('npcId');
    if (this.npcId) {
      this.service.getById(this.npcId).subscribe({
        next: n => { this.npc = n; },
        error: () => this.back()
      });
    }
    if (this.campaignId) {
      this.campaignService.getCampaignById(this.campaignId).subscribe(camp => {
        if (camp.gameSystemId) {
          this.gameSystemService.getById(camp.gameSystemId).subscribe(gs => {
            this.templateFields = gs.npcTemplate ?? [];
          });
        }
      });
    }
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
