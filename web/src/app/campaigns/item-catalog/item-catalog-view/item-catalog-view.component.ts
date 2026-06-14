import { Component, OnInit } from '@angular/core';

import { ActivatedRoute, Router } from '@angular/router';
import { LucideAngularModule, ArrowLeft, Edit3, Package } from 'lucide-angular';
import { ItemCatalogService } from '../../../services/item-catalog.service';
import { CampaignSidebarService } from '../../../services/campaign-sidebar.service';
import { ItemCatalog, CatalogItem } from '../../../services/item-catalog.model';
import { TranslatePipe } from '@ngx-translate/core';

interface ItemGroup { category: string; items: CatalogItem[]; }

/**
 * Vue d'un catalogue d'objets : liste (groupée par catégorie).
 * Route : /campaigns/:campaignId/item-catalogs/:catalogId
 */
@Component({
    selector: 'app-item-catalog-view',
    imports: [LucideAngularModule, TranslatePipe],
    templateUrl: './item-catalog-view.component.html',
    styleUrls: ['./item-catalog-view.component.scss']
})
export class ItemCatalogViewComponent implements OnInit {
  readonly ArrowLeft = ArrowLeft;
  readonly Edit3 = Edit3;
  readonly Package = Package;

  campaignId: string | null = null;
  catalogId: string | null = null;
  catalog: ItemCatalog | null = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private service: ItemCatalogService,
    private campaignSidebar: CampaignSidebarService
  ) {}

  ngOnInit(): void {
    const params = this.route.snapshot.paramMap;
    this.campaignId = params.get('campaignId');
    this.catalogId = params.get('catalogId');
    if (this.catalogId) {
      this.service.getById(this.catalogId).subscribe({
        next: c => this.catalog = c,
        error: () => this.back()
      });
    }
    if (this.campaignId) this.campaignSidebar.show(this.campaignId);
  }

  /** Objets groupés par catégorie (non catégorisés en dernier). */
  get groups(): ItemGroup[] {
    const map = new Map<string, CatalogItem[]>();
    for (const it of this.catalog?.items ?? []) {
      const cat = (it.category ?? '').trim() || '—';
      if (!map.has(cat)) map.set(cat, []);
      map.get(cat)!.push(it);
    }
    return [...map.entries()]
      .sort(([a], [b]) => (a === '—' ? 1 : b === '—' ? -1 : a.localeCompare(b, 'fr')))
      .map(([category, items]) => ({ category, items }));
  }

  edit(): void {
    if (this.campaignId && this.catalogId) {
      this.router.navigate(['/campaigns', this.campaignId, 'item-catalogs', this.catalogId, 'edit']);
    }
  }

  back(): void {
    this.router.navigate(this.campaignId ? ['/campaigns', this.campaignId] : ['/campaigns']);
  }
}
