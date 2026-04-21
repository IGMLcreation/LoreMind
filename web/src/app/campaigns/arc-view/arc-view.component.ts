import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { LucideAngularModule, Pencil } from 'lucide-angular';
import { CampaignService } from '../../services/campaign.service';
import { PageService } from '../../services/page.service';
import { LayoutService, GlobalItem } from '../../services/layout.service';
import { PageTitleService } from '../../services/page-title.service';
import { Campaign, Arc } from '../../services/campaign.model';
import { Page } from '../../services/page.model';
import { loadCampaignTreeData, buildCampaignTree } from '../campaign-tree.helper';
import { ImageGalleryComponent } from '../../shared/image-gallery/image-gallery.component';

/**
 * Écran de consultation d'un Arc narratif (lecture seule).
 * Route : /campaigns/:campaignId/arcs/:arcId
 * Bouton "Modifier" → /campaigns/:campaignId/arcs/:arcId/edit
 */
@Component({
  selector: 'app-arc-view',
  standalone: true,
  imports: [CommonModule, RouterModule, LucideAngularModule, ImageGalleryComponent],
  templateUrl: './arc-view.component.html',
  styleUrls: ['./arc-view.component.scss']
})
export class ArcViewComponent implements OnInit, OnDestroy {
  readonly Pencil = Pencil;

  campaignId = '';
  arcId = '';
  arc: Arc | null = null;

  /** ID du Lore associé à la campagne (null si pas d'univers lié). */
  loreId: string | null = null;
  /** Pages du Lore — pour résoudre relatedPageIds en titres. */
  availablePages: Page[] = [];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private campaignService: CampaignService,
    private pageService: PageService,
    private layoutService: LayoutService,
    private pageTitleService: PageTitleService
  ) {}

  ngOnInit(): void {
    this.route.paramMap.subscribe(pm => {
      const newCampaignId = pm.get('campaignId')!;
      const newArcId = pm.get('arcId')!;
      if (newArcId !== this.arcId || newCampaignId !== this.campaignId) {
        this.campaignId = newCampaignId;
        this.arcId = newArcId;
        this.load();
      }
    });
  }

  private load(): void {
    forkJoin({
      campaign: this.campaignService.getCampaignById(this.campaignId),
      allCampaigns: this.campaignService.getAllCampaigns(),
      arc: this.campaignService.getArcById(this.arcId),
      treeData: loadCampaignTreeData(this.campaignService, this.campaignId)
    }).pipe(
      switchMap(data => {
        const lid = data.campaign.loreId ?? null;
        const pages$ = lid ? this.pageService.getByLoreId(lid) : of([] as Page[]);
        return pages$.pipe(switchMap(pages => of({ ...data, pages, loreId: lid })));
      })
    ).subscribe(({ campaign, allCampaigns, arc, treeData, pages, loreId }) => {
      this.arc = arc;
      this.loreId = loreId;
      this.availablePages = pages;
      this.pageTitleService.set(arc.name);

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

  titleOfRelated(pageId: string): string {
    return this.availablePages.find(p => p.id === pageId)?.title ?? '(page supprimée)';
  }

  editMode(): void {
    this.router.navigate(['/campaigns', this.campaignId, 'arcs', this.arcId, 'edit']);
  }

  ngOnDestroy(): void {
    this.layoutService.hide();
  }
}
