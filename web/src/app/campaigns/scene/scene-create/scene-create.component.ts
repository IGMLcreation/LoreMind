import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { LucideAngularModule } from 'lucide-angular';
import { CampaignService } from '../../../services/campaign.service';
import { CharacterService } from '../../../services/character.service';
import { NpcService } from '../../../services/npc.service';
import { LayoutService, GlobalItem } from '../../../services/layout.service';
import { Campaign } from '../../../services/campaign.model';
import { loadCampaignTreeData, buildCampaignTree } from '../../campaign-tree.helper';
import { IconPickerComponent } from '../../../shared/icon-picker/icon-picker.component';
import { CAMPAIGN_ICON_OPTIONS } from '../../campaign-icons';

/**
 * Écran de création d'une nouvelle scène rattachée à un chapitre.
 * Route : /campaigns/:campaignId/arcs/:arcId/chapters/:chapterId/scenes/create
 */
@Component({
  selector: 'app-scene-create',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, LucideAngularModule, IconPickerComponent],
  templateUrl: './scene-create.component.html',
  styleUrls: ['./scene-create.component.scss']
})
export class SceneCreateComponent implements OnInit, OnDestroy {
  readonly campaignIconOptions = CAMPAIGN_ICON_OPTIONS;
  selectedIcon: string | null = null;

  form: FormGroup;
  campaignId = '';
  arcId = '';
  chapterId = '';
  chapterName = '';
  private existingSceneCount = 0;

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private campaignService: CampaignService,
    private characterService: CharacterService,
    private npcService: NpcService,
    private layoutService: LayoutService
  ) {
    this.form = this.fb.group({
      name:        ['', Validators.required],
      description: ['']
    });
  }

  ngOnInit(): void {
    this.campaignId = this.route.snapshot.paramMap.get('campaignId')!;
    this.arcId = this.route.snapshot.paramMap.get('arcId')!;
    this.chapterId = this.route.snapshot.paramMap.get('chapterId')!;
    this.loadLayout();
  }

  private loadLayout(): void {
    forkJoin({
      campaign: this.campaignService.getCampaignById(this.campaignId),
      allCampaigns: this.campaignService.getAllCampaigns(),
      treeData: loadCampaignTreeData(this.campaignService, this.campaignId, this.characterService, this.npcService)
    }).subscribe(({ campaign, allCampaigns, treeData }) => {
      const currentChapter = (treeData.chaptersByArc[this.arcId] ?? []).find(c => c.id === this.chapterId);
      this.chapterName = currentChapter?.name ?? '';
      this.existingSceneCount = treeData.scenesByChapter[this.chapterId]?.length ?? 0;

      const globalItems: GlobalItem[] = allCampaigns.map((c: Campaign) => ({
        id: c.id!, name: c.name, route: `/campaigns/${c.id}`
      }));

      this.layoutService.show({
        title: campaign.name,
        items: buildCampaignTree(this.campaignId, treeData),
        footerLabel: 'Toutes les campagnes',
        createActions: [
          { id: 'create-arc', label: '+ Nouvel arc', variant: 'primary', route: `/campaigns/${this.campaignId}/arcs/create` }
        ],
        globalItems,
        globalBackLabel: 'Toutes les campagnes',
        globalBackRoute: '/campaigns'
      });
    });
  }

  submit(): void {
    if (this.form.invalid) return;
    this.campaignService.createScene({
      name: this.form.value.name,
      description: this.form.value.description,
      chapterId: this.chapterId,
      order: this.existingSceneCount + 1,
      icon: this.selectedIcon
    }).subscribe({
      next: (created) => this.router.navigate(['/campaigns', this.campaignId, 'arcs', this.arcId, 'chapters', this.chapterId, 'scenes', created.id, 'edit']),
      error: () => console.error('Erreur lors de la création de la scène')
    });
  }

  cancel(): void {
    this.router.navigate(['/campaigns', this.campaignId]);
  }

  ngOnDestroy(): void {
    this.layoutService.hide();
  }
}
