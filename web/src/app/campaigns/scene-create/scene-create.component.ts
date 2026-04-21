import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { LucideAngularModule } from 'lucide-angular';
import { CampaignService } from '../../services/campaign.service';
import { LayoutService, GlobalItem } from '../../services/layout.service';
import { Campaign } from '../../services/campaign.model';
import { loadCampaignTreeData, buildCampaignTree } from '../campaign-tree.helper';

/**
 * Écran de création d'une nouvelle scène rattachée à un chapitre.
 * Route : /campaigns/:campaignId/arcs/:arcId/chapters/:chapterId/scenes/create
 */
@Component({
  selector: 'app-scene-create',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, LucideAngularModule],
  templateUrl: './scene-create.component.html',
  styleUrls: ['./scene-create.component.scss']
})
export class SceneCreateComponent implements OnInit, OnDestroy {
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
      treeData: loadCampaignTreeData(this.campaignService, this.campaignId)
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
      order: this.existingSceneCount + 1
    }).subscribe({
      next: (created) => this.router.navigate(['/campaigns', this.campaignId, 'arcs', this.arcId, 'chapters', this.chapterId, 'scenes', created.id]),
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
