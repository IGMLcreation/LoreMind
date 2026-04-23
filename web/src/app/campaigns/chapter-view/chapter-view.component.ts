import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { LucideAngularModule, Pencil, Network, Trash2 } from 'lucide-angular';
import { CampaignService } from '../../services/campaign.service';
import { PageService } from '../../services/page.service';
import { LayoutService, GlobalItem } from '../../services/layout.service';
import { PageTitleService } from '../../services/page-title.service';
import { Campaign, Chapter } from '../../services/campaign.model';
import { Page } from '../../services/page.model';
import { loadCampaignTreeData, buildCampaignTree } from '../campaign-tree.helper';
import { ImageGalleryComponent } from '../../shared/image-gallery/image-gallery.component';

/**
 * Écran de consultation d'un Chapitre (lecture seule).
 * Route : /campaigns/:campaignId/arcs/:arcId/chapters/:chapterId
 */
@Component({
  selector: 'app-chapter-view',
  standalone: true,
  imports: [CommonModule, RouterModule, LucideAngularModule, ImageGalleryComponent],
  templateUrl: './chapter-view.component.html',
  styleUrls: ['./chapter-view.component.scss']
})
export class ChapterViewComponent implements OnInit, OnDestroy {
  readonly Pencil = Pencil;
  readonly Network = Network;
  readonly Trash2 = Trash2;

  campaignId = '';
  arcId = '';
  chapterId = '';
  chapter: Chapter | null = null;

  loreId: string | null = null;
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
      const newChapterId = pm.get('chapterId')!;
      if (newChapterId !== this.chapterId ||
          newArcId !== this.arcId ||
          newCampaignId !== this.campaignId) {
        this.campaignId = newCampaignId;
        this.arcId = newArcId;
        this.chapterId = newChapterId;
        this.load();
      }
    });
  }

  private load(): void {
    forkJoin({
      campaign: this.campaignService.getCampaignById(this.campaignId),
      allCampaigns: this.campaignService.getAllCampaigns(),
      chapter: this.campaignService.getChapterById(this.chapterId),
      treeData: loadCampaignTreeData(this.campaignService, this.campaignId)
    }).pipe(
      switchMap(data => {
        const lid = data.campaign.loreId ?? null;
        const pages$ = lid ? this.pageService.getByLoreId(lid) : of([] as Page[]);
        return pages$.pipe(switchMap(pages => of({ ...data, pages, loreId: lid })));
      })
    ).subscribe(({ campaign, allCampaigns, chapter, treeData, pages, loreId }) => {
      this.chapter = chapter;
      this.loreId = loreId;
      this.availablePages = pages;
      this.pageTitleService.set(chapter.name);

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
    this.router.navigate([
      '/campaigns', this.campaignId, 'arcs', this.arcId, 'chapters', this.chapterId, 'edit'
    ]);
  }

  openGraph(): void {
    this.router.navigate([
      '/campaigns', this.campaignId, 'arcs', this.arcId, 'chapters', this.chapterId, 'graph'
    ]);
  }

  /**
   * Suppression en cascade : récupère le compte de scènes qui tomberont avec
   * le chapitre, l'annonce dans la confirmation, puis délègue au backend.
   */
  deleteChapter(): void {
    if (!this.chapter) return;
    const chapter = this.chapter;
    this.campaignService.getChapterDeletionImpact(chapter.id!).subscribe({
      next: impact => {
        const lines = [`Supprimer le chapitre "${chapter.name}" ?`];
        if (impact.scenes > 0) {
          lines.push('');
          lines.push(`Cette action supprimera aussi : ${impact.scenes} scène${impact.scenes > 1 ? 's' : ''}.`);
        }
        lines.push('');
        lines.push('Cette action est irréversible.');

        if (!confirm(lines.join('\n'))) return;
        this.campaignService.deleteChapter(chapter.id!).subscribe({
          next: () => this.router.navigate(['/campaigns', this.campaignId, 'arcs', this.arcId]),
          error: () => console.error('Erreur lors de la suppression du chapitre')
        });
      },
      error: () => console.error('Impossible de récupérer les dépendances du chapitre')
    });
  }

  ngOnDestroy(): void {
    this.layoutService.hide();
  }
}
