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
 * Écran de création d'un nouveau chapitre rattaché à un arc.
 * Route : /campaigns/:campaignId/arcs/:arcId/chapters/create
 */
@Component({
  selector: 'app-chapter-create',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, LucideAngularModule, IconPickerComponent],
  templateUrl: './chapter-create.component.html',
  styleUrls: ['./chapter-create.component.scss']
})
export class ChapterCreateComponent implements OnInit, OnDestroy {
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
    this.loadLayout();
  }

  private loadLayout(): void {
    forkJoin({
      campaign: this.campaignService.getCampaignById(this.campaignId),
      allCampaigns: this.campaignService.getAllCampaigns(),
      treeData: loadCampaignTreeData(this.campaignService, this.campaignId, this.characterService, this.npcService)
    }).subscribe(({ campaign, allCampaigns, treeData }) => {
      const currentArc = treeData.arcs.find(a => a.id === this.arcId);
      this.arcName = currentArc?.name ?? '';
      this.existingChapterCount = treeData.chaptersByArc[this.arcId]?.length ?? 0;

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

  ngOnDestroy(): void {
    this.layoutService.hide();
  }
}
