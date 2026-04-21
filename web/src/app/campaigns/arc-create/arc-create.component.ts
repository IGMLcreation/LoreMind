import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { LucideAngularModule, BookOpen } from 'lucide-angular';
import { CampaignService } from '../../services/campaign.service';
import { LayoutService, GlobalItem } from '../../services/layout.service';
import { Campaign } from '../../services/campaign.model';
import { loadCampaignTreeData, buildCampaignTree } from '../campaign-tree.helper';

/**
 * Écran de création d'un nouvel Arc narratif (contexte Campagne).
 * Formulaire simple : nom + description. L'ordre est auto-calculé depuis
 * le nombre d'arcs existants dans la campagne courante.
 */
@Component({
  selector: 'app-arc-create',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, LucideAngularModule],
  templateUrl: './arc-create.component.html',
  styleUrls: ['./arc-create.component.scss']
})
export class ArcCreateComponent implements OnInit, OnDestroy {
  readonly BookOpen = BookOpen;

  form: FormGroup;
  campaignId = '';
  private existingArcCount = 0;

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
    this.loadLayout();
  }

  private loadLayout(): void {
    forkJoin({
      campaign: this.campaignService.getCampaignById(this.campaignId),
      allCampaigns: this.campaignService.getAllCampaigns(),
      treeData: loadCampaignTreeData(this.campaignService, this.campaignId)
    }).subscribe(({ campaign, allCampaigns, treeData }) => {
      this.existingArcCount = treeData.arcs.length;

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
    this.campaignService.createArc({
      name: this.form.value.name,
      description: this.form.value.description,
      campaignId: this.campaignId,
      order: this.existingArcCount + 1
    }).subscribe({
      next: (created) => this.router.navigate(['/campaigns', this.campaignId, 'arcs', created.id]),
      error: () => console.error('Erreur lors de la création de l\'arc')
    });
  }

  cancel(): void {
    this.router.navigate(['/campaigns', this.campaignId]);
  }

  ngOnDestroy(): void {
    this.layoutService.hide();
  }
}
