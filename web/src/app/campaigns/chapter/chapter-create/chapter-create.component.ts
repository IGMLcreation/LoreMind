import { Component, OnInit } from '@angular/core';

import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { LucideAngularModule } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { CampaignService } from '../../../services/campaign.service';
import { NpcService } from '../../../services/npc.service';
import { RandomTableService } from '../../../services/random-table.service';
import { EnemyService } from '../../../services/enemy.service';
import { LayoutService } from '../../../services/layout.service';
import { loadCampaignTreeData, buildCampaignSidebarConfig } from '../../campaign-tree.helper';
import { IconPickerComponent } from '../../../shared/icon-picker/icon-picker.component';
import { CAMPAIGN_ICON_OPTIONS } from '../../campaign-icons';

/**
 * Écran de création d'un nouveau chapitre rattaché à un arc.
 * Route : /campaigns/:campaignId/arcs/:arcId/chapters/create
 */
@Component({
    selector: 'app-chapter-create',
    imports: [ReactiveFormsModule, LucideAngularModule, IconPickerComponent, TranslatePipe],
    templateUrl: './chapter-create.component.html',
    styleUrls: ['./chapter-create.component.scss']
})
export class ChapterCreateComponent implements OnInit {
  readonly campaignIconOptions = CAMPAIGN_ICON_OPTIONS;
  selectedIcon: string | null = null;

  form: FormGroup;
  campaignId = '';
  arcId = '';
  arcName = '';
  private existingChapterCount = 0;

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private campaignService: CampaignService,
    private npcService: NpcService,
    private randomTableService: RandomTableService,
    private enemyService: EnemyService,
    private layoutService: LayoutService,
    private translate: TranslateService
  ) {
    this.form = this.fb.group({
      name:        ['', Validators.required],
      description: ['']
    });
  }

  ngOnInit(): void {
    this.campaignId = this.route.snapshot.paramMap.get('campaignId')!;
    this.arcId = this.route.snapshot.paramMap.get('arcId')!;
    this.loadLayout();
  }

  private loadLayout(): void {
    forkJoin({
      campaign: this.campaignService.getCampaignById(this.campaignId),
      allCampaigns: this.campaignService.getAllCampaigns(),
      treeData: loadCampaignTreeData(this.campaignService, this.campaignId, this.npcService, this.randomTableService, this.enemyService)
    }).subscribe(({ campaign, allCampaigns, treeData }) => {
      const currentArc = treeData.arcs.find(a => a.id === this.arcId);
      this.arcName = currentArc?.name ?? '';
      this.existingChapterCount = treeData.chaptersByArc[this.arcId]?.length ?? 0;

      this.layoutService.show(buildCampaignSidebarConfig(campaign, allCampaigns, treeData, this.campaignId, this.translate));
    });
  }

  submit(): void {
    if (this.form.invalid) return;
    this.campaignService.createChapter({
      name: this.form.value.name,
      description: this.form.value.description,
      arcId: this.arcId,
      order: this.existingChapterCount + 1,
      icon: this.selectedIcon
    }).subscribe({
      next: (created) => this.router.navigate(['/campaigns', this.campaignId, 'arcs', this.arcId, 'chapters', created.id]),
      error: () => console.error('Erreur lors de la création du chapitre')
    });
  }

  cancel(): void {
    this.router.navigate(['/campaigns', this.campaignId]);
  }
}
