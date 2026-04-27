import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { LucideAngularModule, Map, Plus } from 'lucide-angular';
import { CampaignService } from '../services/campaign.service';
import { Campaign } from '../services/campaign.model';
import { CampaignCreateComponent, CampaignCreatePayload } from './campaign/campaign-create/campaign-create.component';

@Component({
  selector: 'app-campaigns',
  standalone: true,
  imports: [CommonModule, LucideAngularModule, CampaignCreateComponent],
  templateUrl: './campaigns.component.html',
  styleUrls: ['./campaigns.component.scss']
})
export class CampaignsComponent implements OnInit {
  readonly Map = Map;
  readonly Plus = Plus;

  campaigns: Campaign[] = [];
  showCreateModal = false;

  constructor(
    private router: Router,
    private campaignService: CampaignService
  ) {}

  ngOnInit(): void {
    this.loadCampaigns();
  }

  loadCampaigns(): void {
    this.campaignService.getAllCampaigns().subscribe({
      next: (data) => this.campaigns = data,
      error: () => this.campaigns = []
    });
  }

  openCreateModal(): void {
    this.showCreateModal = true;
  }

  onModalClose(): void {
    this.showCreateModal = false;
  }

  onCampaignCreated(data: CampaignCreatePayload): void {
    this.campaignService.createCampaign(data).subscribe({
      next: () => {
        this.showCreateModal = false;
        this.loadCampaigns();
      },
      error: () => console.error('Erreur lors de la création de la campagne')
    });
  }

  navigateToDetail(id: string): void {
    this.router.navigate(['/campaigns', id]);
  }
}
