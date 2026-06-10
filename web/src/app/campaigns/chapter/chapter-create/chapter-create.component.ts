import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { LucideAngularModule } from 'lucide-angular';
import { CampaignService } from '../../../services/campaign.service';
import { CharacterService } from '../../../services/character.service';
import { NpcService } from '../../../services/npc.service';
import { RandomTableService } from '../../../services/random-table.service';
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
  /** Arc parent de type hub : un "chapitre" y est présenté comme une "quête". */
  isHub = false;
  private existingChapterCount = 0;

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private campaignService: CampaignService,
    private characterService: CharacterService,
    private npcService: NpcService,
    private randomTableService: RandomTableService,
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
      treeData: loadCampaignTreeData(this.campaignService, this.campaignId, this.characterService, this.npcService, this.randomTableService)
    }).subscribe(({ campaign, allCampaigns, treeData }) => {
      const currentArc = treeData.arcs.find(a => a.id === this.arcId);
      this.arcName = currentArc?.name ?? '';
      this.isHub = currentArc?.type === 'HUB';
      this.existingChapterCount = treeData.chaptersByArc[this.arcId]?.length ?? 0;

      this.layoutService.show(buildCampaignSidebarConfig(campaign, allCampaigns, treeData, this.campaignId));
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
    // Volontairement vide : la sidebar reste prise en charge par le composant
    // suivant (autre sous-route ou le composant detail parent) qui appellera
    // show(). Eviter d'appeler hide() ici previent le clignotement / la
    // disparition de la sidebar lors des navigations internes a la section.
  }
}
