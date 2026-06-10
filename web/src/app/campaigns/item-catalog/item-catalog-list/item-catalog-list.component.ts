import { Component, OnInit } from '@angular/core';

import { ActivatedRoute, Router } from '@angular/router';
import { LucideAngularModule, ArrowLeft, Plus, Trash2, Package } from 'lucide-angular';
import { ItemCatalogService } from '../../../services/item-catalog.service';
import { CampaignSidebarService } from '../../../services/campaign-sidebar.service';
import { ItemCatalog } from '../../../services/item-catalog.model';
import { ConfirmDialogService } from '../../../shared/confirm-dialog/confirm-dialog.service';

/**
 * Liste des catalogues d'objets d'une campagne + création.
 * Route : /campaigns/:campaignId/item-catalogs
 */
@Component({
    selector: 'app-item-catalog-list',
    imports: [LucideAngularModule],
    templateUrl: './item-catalog-list.component.html',
    styleUrls: ['./item-catalog-list.component.scss']
})
export class ItemCatalogListComponent implements OnInit {
  readonly ArrowLeft = ArrowLeft;
  readonly Plus = Plus;
  readonly Trash2 = Trash2;
  readonly Package = Package;

  campaignId = '';
  catalogs: ItemCatalog[] = [];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private service: ItemCatalogService,
    private campaignSidebar: CampaignSidebarService,
    private confirmDialog: ConfirmDialogService
  ) {}

  ngOnInit(): void {
    this.campaignId = this.route.snapshot.paramMap.get('campaignId') ?? '';
    if (this.campaignId) {
      this.campaignSidebar.show(this.campaignId);
      this.load();
    }
  }

  load(): void {
    this.service.getByCampaign(this.campaignId).subscribe({
      next: (list) => this.catalogs = list,
      error: () => this.catalogs = []
    });
  }

  create(): void {
    this.router.navigate(['/campaigns', this.campaignId, 'item-catalogs', 'create']);
  }

  open(c: ItemCatalog): void {
    this.router.navigate(['/campaigns', this.campaignId, 'item-catalogs', c.id]);
  }

  remove(c: ItemCatalog, ev: Event): void {
    ev.stopPropagation();
    this.confirmDialog.confirm({
      title: 'Supprimer le catalogue',
      message: `Supprimer « ${c.name} » ?`,
      confirmLabel: 'Supprimer',
      variant: 'danger'
    }).then(ok => {
      if (!ok) return;
      this.service.delete(c.id!).subscribe(() => this.load());
    });
  }

  back(): void {
    this.router.navigate(['/campaigns', this.campaignId]);
  }
}
