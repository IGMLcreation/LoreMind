import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, Save, ArrowLeft, User, Trash2, Sparkles } from 'lucide-angular';
import { CharacterService } from '../../../services/character.service';
import { CampaignService } from '../../../services/campaign.service';
import { GameSystemService } from '../../../services/game-system.service';
import { TemplateField } from '../../../services/template.model';
import { AiChatDrawerComponent } from '../../../shared/ai-chat-drawer/ai-chat-drawer.component';
import { DynamicFieldsFormComponent } from '../../../shared/dynamic-fields-form/dynamic-fields-form.component';
import { SingleImagePickerComponent } from '../../../shared/single-image-picker/single-image-picker.component';

/**
 * Editeur plein ecran d'une fiche de personnage (PJ).
 * Refonte 2026-04-30 : remplace le markdown libre par un formulaire dynamique
 * pilote par le characterTemplate du GameSystem associe a la campagne.
 *
 * Comportements :
 *  - Si la campagne n'a pas de GameSystem ou si son template est vide, affiche
 *    uniquement les champs universels (nom, portrait, header).
 *  - Le picker d'images dedie portrait/header est hors scope MVP — pour l'instant
 *    saisie manuelle d'IDs d'images.
 */
@Component({
  selector: 'app-character-edit',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideAngularModule, AiChatDrawerComponent, DynamicFieldsFormComponent, SingleImagePickerComponent],
  templateUrl: './character-edit.component.html',
  styleUrls: ['./character-edit.component.scss']
})
export class CharacterEditComponent implements OnInit {
  readonly Save = Save;
  readonly ArrowLeft = ArrowLeft;
  readonly User = User;
  readonly Trash2 = Trash2;
  readonly Sparkles = Sparkles;

  chatOpen = false;
  readonly chatQuickSuggestions = [
    'Propose une backstory coherente avec l\'univers',
    'Suggere 3 objectifs personnels pour ce personnage',
    'Aide-moi a equilibrer les stats de combat'
  ];

  toggleChat(): void { this.chatOpen = !this.chatOpen; }

  campaignId: string | null = null;
  characterId: string | null = null;

  name = '';
  portraitImageId: string | null = null;
  headerImageId: string | null = null;
  values: Record<string, string> = {};
  imageValues: Record<string, string[]> = {};
  keyValueValues: Record<string, Record<string, string>> = {};
  templateFields: TemplateField[] = [];
  private order = 0;

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

    if (this.campaignId) {
      this.loadTemplateForCampaign(this.campaignId);
    }

    if (this.characterId) {
      this.service.getById(this.characterId).subscribe({
        next: (c) => {
          this.name = c.name;
          this.portraitImageId = c.portraitImageId ?? null;
          this.headerImageId = c.headerImageId ?? null;
          this.values = c.values ?? {};
          this.imageValues = c.imageValues ?? {};
          this.keyValueValues = c.keyValueValues ?? {};
          this.order = c.order ?? 0;
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
          next: (gs) => { this.templateFields = gs.characterTemplate ?? []; },
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
      keyValueValues: this.keyValueValues,
      campaignId: this.campaignId
    };
    const req = this.characterId
      ? this.service.update(this.characterId, { ...payload, id: this.characterId, order: this.order })
      : this.service.create(payload);
    req.subscribe({
      next: () => this.back(),
      error: () => console.error('Erreur sauvegarde Character')
    });
  }

  deleteCharacter(): void {
    if (!this.characterId) return;
    if (!confirm(`Supprimer la fiche de "${this.name}" ? Cette action est irreversible.`)) return;
    this.service.delete(this.characterId).subscribe({
      next: () => this.back(),
      error: () => console.error('Erreur suppression Character')
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
