import { Component, OnInit, OnDestroy } from '@angular/core';

import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { LucideAngularModule, BookOpen } from 'lucide-angular';
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
 * Écran de création d'un nouvel Arc narratif (contexte Campagne).
 * Formulaire simple : nom + description. L'ordre est auto-calculé depuis
 * le nombre d'arcs existants dans la campagne courante.
 */
@Component({
    selector: 'app-arc-create',
    imports: [ReactiveFormsModule, LucideAngularModule, IconPickerComponent, TranslatePipe],
    templateUrl: './arc-create.component.html',
    styleUrls: ['./arc-create.component.scss']
})
export class ArcCreateComponent implements OnInit, OnDestroy {
  readonly BookOpen = BookOpen;
  readonly campaignIconOptions = CAMPAIGN_ICON_OPTIONS;

  form: FormGroup;
  campaignId = '';
  selectedIcon: string | null = null;
  private existingArcCount = 0;

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
      description: [''],
      // Type structurel : LINEAR (séquentiel) par défaut, HUB (sandbox/quêtes parallèles).
      type:        ['LINEAR', Validators.required]
    });
  }

  ngOnInit(): void {
    this.campaignId = this.route.snapshot.paramMap.get('campaignId')!;
    this.loadLayout();
  }

  private loadLayout(): void {
    forkJoin({
      campaign: this.campaignService.getCampaignById(this.campaignId),
      allCampaigns: this.campaignService.getAllCampaigns(),
      treeData: loadCampaignTreeData(this.campaignService, this.campaignId, this.npcService, this.randomTableService, this.enemyService)
    }).subscribe(({ campaign, allCampaigns, treeData }) => {
      this.existingArcCount = treeData.arcs.length;

      this.layoutService.show(buildCampaignSidebarConfig(campaign, allCampaigns, treeData, this.campaignId, this.translate));
    });
  }

  submit(): void {
    if (this.form.invalid) return;
    this.campaignService.createArc({
      name: this.form.value.name,
      description: this.form.value.description,
      campaignId: this.campaignId,
      order: this.existingArcCount + 1,
      type: this.form.value.type,
      icon: this.selectedIcon
    }).subscribe({
      next: (created) => this.router.navigate(['/campaigns', this.campaignId, 'arcs', created.id]),
      error: () => console.error('Erreur lors de la création de l\'arc')
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
