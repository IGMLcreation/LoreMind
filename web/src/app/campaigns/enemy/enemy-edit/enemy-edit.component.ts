import { Component, OnInit } from '@angular/core';

import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, Save, ArrowLeft, Skull, Trash2 } from 'lucide-angular';
import { EnemyService } from '../../../services/enemy.service';
import { CampaignService } from '../../../services/campaign.service';
import { GameSystemService } from '../../../services/game-system.service';
import { CampaignSidebarService } from '../../../services/campaign-sidebar.service';
import { TemplateField } from '../../../services/template.model';
import { DynamicFieldsFormComponent } from '../../../shared/dynamic-fields-form/dynamic-fields-form.component';
import { SingleImagePickerComponent } from '../../../shared/single-image-picker/single-image-picker.component';
import { ConfirmDialogService } from '../../../shared/confirm-dialog/confirm-dialog.service';

/**
 * Editeur plein écran d'une fiche d'ennemi (bestiaire). Même principe que
 * NpcEditComponent : formulaire dynamique piloté par le template ENNEMI du
 * GameSystem associé à la campagne, + champs universels niveau/dossier.
 */
@Component({
    selector: 'app-enemy-edit',
    imports: [FormsModule, LucideAngularModule, DynamicFieldsFormComponent, SingleImagePickerComponent],
    templateUrl: './enemy-edit.component.html',
    styleUrls: ['./enemy-edit.component.scss']
})
export class EnemyEditComponent implements OnInit {
  readonly Save = Save;
  readonly ArrowLeft = ArrowLeft;
  readonly Skull = Skull;
  readonly Trash2 = Trash2;

  campaignId: string | null = null;
  enemyId: string | null = null;

  name = '';
  level = '';
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

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private service: EnemyService,
    private campaignService: CampaignService,
    private gameSystemService: GameSystemService,
    private campaignSidebar: CampaignSidebarService,
    private confirmDialog: ConfirmDialogService
  ) {}

  ngOnInit(): void {
    const params = this.route.snapshot.paramMap;
    this.campaignId = params.get('campaignId');
    this.enemyId = params.get('enemyId');

    if (this.campaignId) {
      this.loadTemplateForCampaign(this.campaignId);
      this.campaignSidebar.show(this.campaignId);
      this.loadExistingFolders(this.campaignId);
    }

    if (this.enemyId) {
      this.service.getById(this.enemyId).subscribe({
        next: (e) => {
          this.name = e.name;
          this.level = e.level ?? '';
          this.folder = e.folder ?? '';
          this.portraitImageId = e.portraitImageId ?? null;
          this.headerImageId = e.headerImageId ?? null;
          this.values = e.values ?? {};
          this.imageValues = e.imageValues ?? {};
          this.keyValueValues = e.keyValueValues ?? {};
          this.order = e.order ?? 0;
        },
        error: () => this.back()
      });
    }
  }

  private loadExistingFolders(campaignId: string): void {
    this.service.getByCampaign(campaignId).subscribe({
      next: (list) => {
        this.existingFolders = [...new Set(
          list.map(e => (e.folder ?? '').trim()).filter(f => f.length > 0)
        )].sort((a, b) => a.localeCompare(b, 'fr'));
      },
      error: () => { this.existingFolders = []; }
    });
  }

  private loadTemplateForCampaign(campaignId: string): void {
    this.campaignService.getCampaignById(campaignId).subscribe({
      next: (campaign) => {
        if (!campaign.gameSystemId) {
          this.templateFields = [];
          return;
        }
        this.gameSystemService.getById(campaign.gameSystemId).subscribe({
          next: (gs) => { this.templateFields = gs.enemyTemplate ?? []; },
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
      level: this.level.trim() || null,
      folder: this.folder.trim() || null,
      portraitImageId: this.portraitImageId,
      headerImageId: this.headerImageId,
      values: this.values,
      imageValues: this.imageValues,
      keyValueValues: this.keyValueValues,
      campaignId: this.campaignId
    };
    const isCreation = !this.enemyId;
    const req = this.enemyId
      ? this.service.update(this.enemyId, { ...payload, id: this.enemyId, order: this.order })
      : this.service.create(payload);
    req.subscribe({
      next: (saved) => {
        if (isCreation && saved.id) {
          this.router.navigate(['/campaigns', this.campaignId, 'enemies', saved.id]);
        } else {
          this.back();
        }
      },
      error: () => console.error('Erreur sauvegarde Enemy')
    });
  }

  deleteEnemy(): void {
    if (!this.enemyId) return;
    this.confirmDialog.confirm({
      title: 'Supprimer la fiche ?',
      message: `Supprimer la fiche de "${this.name}" ?`,
      details: ['Cette action est irreversible.'],
      confirmLabel: 'Supprimer',
      variant: 'danger'
    }).then(ok => {
      if (!ok || !this.enemyId) return;
      this.service.delete(this.enemyId).subscribe({
        next: () => this.back(),
        error: () => console.error('Erreur suppression Enemy')
      });
    });
  }

  back(): void {
    if (this.campaignId) {
      this.router.navigate(['/campaigns', this.campaignId, 'enemies']);
    } else {
      this.router.navigate(['/campaigns']);
    }
  }
}
