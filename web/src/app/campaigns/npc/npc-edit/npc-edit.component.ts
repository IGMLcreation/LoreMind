import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, Save, ArrowLeft, Drama, Trash2, Sparkles } from 'lucide-angular';
import { NpcService } from '../../../services/npc.service';
import { CampaignService } from '../../../services/campaign.service';
import { GameSystemService } from '../../../services/game-system.service';
import { TemplateField } from '../../../services/template.model';
import { AiChatDrawerComponent } from '../../../shared/ai-chat-drawer/ai-chat-drawer.component';
import { DynamicFieldsFormComponent } from '../../../shared/dynamic-fields-form/dynamic-fields-form.component';
import { SingleImagePickerComponent } from '../../../shared/single-image-picker/single-image-picker.component';

/**
 * Editeur plein ecran d'une fiche de PNJ.
 * Refonte 2026-04-30 : meme principe que CharacterEditComponent — markdown
 * libre remplace par un formulaire dynamique pilote par le npcTemplate du
 * GameSystem associe a la campagne.
 */
@Component({
  selector: 'app-npc-edit',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideAngularModule, AiChatDrawerComponent, DynamicFieldsFormComponent, SingleImagePickerComponent],
  templateUrl: './npc-edit.component.html',
  styleUrls: ['./npc-edit.component.scss']
})
export class NpcEditComponent implements OnInit {
  readonly Save = Save;
  readonly ArrowLeft = ArrowLeft;
  readonly Drama = Drama;
  readonly Trash2 = Trash2;
  readonly Sparkles = Sparkles;

  chatOpen = false;
  readonly chatQuickSuggestions = [
    'Propose une apparence et une posture marquantes',
    'Suggere 2 motivations et un secret pour ce PNJ',
    'Imagine 3 repliques signatures qui le caracterisent'
  ];

  toggleChat(): void { this.chatOpen = !this.chatOpen; }

  campaignId: string | null = null;
  npcId: string | null = null;

  name = '';
  portraitImageId: string | null = null;
  headerImageId: string | null = null;
  values: Record<string, string> = {};
  imageValues: Record<string, string[]> = {};
  templateFields: TemplateField[] = [];
  private order = 0;

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

    if (this.campaignId) {
      this.loadTemplateForCampaign(this.campaignId);
    }

    if (this.npcId) {
      this.service.getById(this.npcId).subscribe({
        next: (n) => {
          this.name = n.name;
          this.portraitImageId = n.portraitImageId ?? null;
          this.headerImageId = n.headerImageId ?? null;
          this.values = n.values ?? {};
          this.imageValues = n.imageValues ?? {};
          this.order = n.order ?? 0;
        },
        error: () => this.back()
      });
    }
  }

  private loadTemplateForCampaign(campaignId: string): void {
    this.campaignService.getCampaignById(campaignId).subscribe({
      next: (campaign) => {
        if (!campaign.gameSystemId) {
          this.templateFields = [];
          return;
        }
        this.gameSystemService.getById(campaign.gameSystemId).subscribe({
          next: (gs) => { this.templateFields = gs.npcTemplate ?? []; },
          error: () => { this.templateFields = []; }
        });
      },
      error: () => { this.templateFields = []; }
    });
  }

  submit(): void {
    if (!this.name.trim() || !this.campaignId) return;
    const payload = {
      name: this.name.trim(),
      portraitImageId: this.portraitImageId,
      headerImageId: this.headerImageId,
      values: this.values,
      imageValues: this.imageValues,
      campaignId: this.campaignId
    };
    const req = this.npcId
      ? this.service.update(this.npcId, { ...payload, id: this.npcId, order: this.order })
      : this.service.create(payload);
    req.subscribe({
      next: () => this.back(),
      error: () => console.error('Erreur sauvegarde Npc')
    });
  }

  deleteNpc(): void {
    if (!this.npcId) return;
    if (!confirm(`Supprimer la fiche de "${this.name}" ? Cette action est irreversible.`)) return;
    this.service.delete(this.npcId).subscribe({
      next: () => this.back(),
      error: () => console.error('Erreur suppression Npc')
    });
  }

  back(): void {
    if (this.campaignId) {
      this.router.navigate(['/campaigns', this.campaignId]);
    } else {
      this.router.navigate(['/campaigns']);
    }
  }
}
