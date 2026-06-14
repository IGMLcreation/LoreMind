import { Component, OnInit } from '@angular/core';

import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, Save, ArrowLeft, Drama, Trash2, Sparkles } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { NpcService } from '../../../services/npc.service';
import { CampaignService } from '../../../services/campaign.service';
import { GameSystemService } from '../../../services/game-system.service';
import { PageService } from '../../../services/page.service';
import { CampaignSidebarService } from '../../../services/campaign-sidebar.service';
import { TemplateField } from '../../../services/template.model';
import { Page } from '../../../services/page.model';
import { AiChatDrawerComponent } from '../../../shared/ai-chat-drawer/ai-chat-drawer.component';
import { DynamicFieldsFormComponent } from '../../../shared/dynamic-fields-form/dynamic-fields-form.component';
import { SingleImagePickerComponent } from '../../../shared/single-image-picker/single-image-picker.component';
import { LoreLinkPickerComponent } from '../../../shared/lore-link-picker/lore-link-picker.component';
import { ConfirmDialogService } from '../../../shared/confirm-dialog/confirm-dialog.service';

/**
 * Editeur plein ecran d'une fiche de PNJ.
 * Refonte 2026-04-30 : meme principe que CharacterEditComponent — markdown
 * libre remplace par un formulaire dynamique pilote par le npcTemplate du
 * GameSystem associe a la campagne.
 */
@Component({
    selector: 'app-npc-edit',
    imports: [FormsModule, LucideAngularModule, TranslatePipe, AiChatDrawerComponent, DynamicFieldsFormComponent, SingleImagePickerComponent, LoreLinkPickerComponent],
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
  get chatQuickSuggestions(): string[] {
    return [
      this.translate.instant('npcEdit.chatSuggestion1'),
      this.translate.instant('npcEdit.chatSuggestion2'),
      this.translate.instant('npcEdit.chatSuggestion3')
    ];
  }

  toggleChat(): void { this.chatOpen = !this.chatOpen; }

  campaignId: string | null = null;
  npcId: string | null = null;

  name = '';
  folder = '';
  /** Dossiers déjà utilisés dans la campagne (datalist d'auto-complétion). */
  existingFolders: string[] = [];
  portraitImageId: string | null = null;
  headerImageId: string | null = null;
  values: Record<string, string> = {};
  imageValues: Record<string, string[]> = {};
  keyValueValues: Record<string, Record<string, string>> = {};
  templateFields: TemplateField[] = [];
  private order = 0;

  /** Lore lié à la campagne (null = pas de lore → section liens masquée). */
  loreId: string | null = null;
  /** Pages du lore lié — référentiel du picker. */
  lorePages: Page[] = [];
  /** IDs des pages de lore référencées par ce PNJ. */
  relatedPageIds: string[] = [];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private service: NpcService,
    private campaignService: CampaignService,
    private gameSystemService: GameSystemService,
    private pageService: PageService,
    private campaignSidebar: CampaignSidebarService,
    private confirmDialog: ConfirmDialogService,
    private translate: TranslateService
  ) {}

  ngOnInit(): void {
    const params = this.route.snapshot.paramMap;
    this.campaignId = params.get('campaignId');
    this.npcId = params.get('npcId');

    if (this.campaignId) {
      this.loadTemplateForCampaign(this.campaignId);
      this.campaignSidebar.show(this.campaignId);
      this.loadExistingFolders(this.campaignId);
    }

    if (this.npcId) {
      this.service.getById(this.npcId).subscribe({
        next: (n) => {
          this.name = n.name;
          this.folder = n.folder ?? '';
          this.portraitImageId = n.portraitImageId ?? null;
          this.headerImageId = n.headerImageId ?? null;
          this.values = n.values ?? {};
          this.imageValues = n.imageValues ?? {};
          this.keyValueValues = n.keyValueValues ?? {};
          this.relatedPageIds = [...(n.relatedPageIds ?? [])];
          this.order = n.order ?? 0;
        },
        error: () => this.back()
      });
    }
  }

  private loadExistingFolders(campaignId: string): void {
    this.service.getByCampaign(campaignId).subscribe({
      next: (list) => {
        this.existingFolders = [...new Set(
          list.map(n => (n.folder ?? '').trim()).filter(f => f.length > 0)
        )].sort((a, b) => a.localeCompare(b, 'fr'));
      },
      error: () => { this.existingFolders = []; }
    });
  }

  private loadTemplateForCampaign(campaignId: string): void {
    this.campaignService.getCampaignById(campaignId).subscribe({
      next: (campaign) => {
        // Lore lié → charge ses pages pour le picker de références.
        if (campaign.loreId) {
          this.loreId = campaign.loreId;
          this.pageService.getByLoreId(campaign.loreId).subscribe({
            next: (pages) => { this.lorePages = pages; },
            error: () => { this.lorePages = []; }
          });
        }
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
      folder: this.folder.trim() || null,
      portraitImageId: this.portraitImageId,
      headerImageId: this.headerImageId,
      values: this.values,
      imageValues: this.imageValues,
      keyValueValues: this.keyValueValues,
      campaignId: this.campaignId,
      relatedPageIds: this.relatedPageIds
    };
    const isCreation = !this.npcId;
    const req = this.npcId
      ? this.service.update(this.npcId, { ...payload, id: this.npcId, order: this.order })
      : this.service.create(payload);
    req.subscribe({
      next: (saved) => {
        if (isCreation && saved.id) {
          this.router.navigate(['/campaigns', this.campaignId, 'npcs', saved.id]);
        } else {
          this.back();
        }
      },
      error: () => console.error('Erreur sauvegarde Npc')
    });
  }

  deleteNpc(): void {
    if (!this.npcId) return;
    this.confirmDialog.confirm({
      title: this.translate.instant('npcEdit.deleteTitle'),
      message: this.translate.instant('npcEdit.deleteMessage', { name: this.name }),
      details: [this.translate.instant('npcEdit.irreversible')],
      confirmLabel: this.translate.instant('common.delete'),
      variant: 'danger'
    }).then(ok => {
      if (!ok || !this.npcId) return;
      this.service.delete(this.npcId).subscribe({
        next: () => this.back(),
        error: () => console.error('Erreur suppression Npc')
      });
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
