import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { LucideAngularModule, ArrowLeft, Edit3, Sparkles } from 'lucide-angular';
import { CharacterService } from '../../../services/character.service';
import { CampaignService } from '../../../services/campaign.service';
import { GameSystemService } from '../../../services/game-system.service';
import { TemplateField } from '../../../services/template.model';
import { Character } from '../../../services/character.model';
import { PersonaViewComponent } from '../../../shared/persona-view/persona-view.component';
import { AiChatDrawerComponent } from '../../../shared/ai-chat-drawer/ai-chat-drawer.component';

/**
 * Vue lecture seule "WorldAnvil" d'une fiche PJ.
 * Route : /campaigns/:campaignId/characters/:characterId
 */
@Component({
  selector: 'app-character-view',
  standalone: true,
  imports: [CommonModule, LucideAngularModule, PersonaViewComponent, AiChatDrawerComponent],
  templateUrl: './character-view.component.html',
  styleUrls: ['./character-view.component.scss']
})
export class CharacterViewComponent implements OnInit {
  readonly ArrowLeft = ArrowLeft;
  readonly Edit3 = Edit3;
  readonly Sparkles = Sparkles;

  campaignId: string | null = null;
  characterId: string | null = null;

  character: Character | null = null;
  templateFields: TemplateField[] = [];

  chatOpen = false;
  toggleChat(): void { this.chatOpen = !this.chatOpen; }

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private service: CharacterService,
    private campaignService: CampaignService,
    private gameSystemService: GameSystemService
  ) {}

  ngOnInit(): void {
    const params = this.route.snapshot.paramMap;
    this.campaignId = params.get('campaignId');
    this.characterId = params.get('characterId');
    if (this.characterId) {
      this.service.getById(this.characterId).subscribe({
        next: c => { this.character = c; },
        error: () => this.back()
      });
    }
    if (this.campaignId) {
      this.campaignService.getCampaignById(this.campaignId).subscribe(camp => {
        if (camp.gameSystemId) {
          this.gameSystemService.getById(camp.gameSystemId).subscribe(gs => {
            this.templateFields = gs.characterTemplate ?? [];
          });
        }
      });
    }
  }

  edit(): void {
    if (this.campaignId && this.characterId) {
      this.router.navigate(['/campaigns', this.campaignId, 'characters', this.characterId, 'edit']);
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
