import { Component, OnInit, DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { LucideAngularModule, Pencil, Network, Trash2 } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { resolveCampaignIcon } from '../../campaign-icons';
import { CampaignService } from '../../../services/campaign.service';
import { NpcService } from '../../../services/npc.service';
import { RandomTableService } from '../../../services/random-table.service';
import { EnemyService } from '../../../services/enemy.service';
import { PageService } from '../../../services/page.service';
import { LayoutService } from '../../../services/layout.service';
import { PageTitleService } from '../../../services/page-title.service';
import { Chapter } from '../../../services/campaign.model';
import { Page } from '../../../services/page.model';
import { loadCampaignTreeData, buildCampaignSidebarConfig } from '../../campaign-tree.helper';
import { ImageGalleryComponent } from '../../../shared/image-gallery/image-gallery.component';
import { ConfirmDialogService } from '../../../shared/confirm-dialog/confirm-dialog.service';

/**
 * Écran de consultation d'un Chapitre (lecture seule).
 * Route : /campaigns/:campaignId/arcs/:arcId/chapters/:chapterId
 */
@Component({
    selector: 'app-chapter-view',
    imports: [RouterModule, LucideAngularModule, ImageGalleryComponent, TranslatePipe],
    templateUrl: './chapter-view.component.html',
    styleUrls: ['./chapter-view.component.scss']
})
export class ChapterViewComponent implements OnInit {
  readonly Pencil = Pencil;
  readonly Network = Network;
  readonly Trash2 = Trash2;
  readonly resolveCampaignIcon = resolveCampaignIcon;

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
    private npcService: NpcService,
    private randomTableService: RandomTableService,
    private enemyService: EnemyService,
    private pageService: PageService,
    private layoutService: LayoutService,
    private pageTitleService: PageTitleService,
    private confirmDialog: ConfirmDialogService,
    private translate: TranslateService,
    private destroyRef: DestroyRef
  ) {}

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(pm => {
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
      treeData: loadCampaignTreeData(this.campaignService, this.campaignId, this.npcService, this.randomTableService, this.enemyService)
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

      this.layoutService.show(buildCampaignSidebarConfig(campaign, allCampaigns, treeData, this.campaignId, this.translate));
    });
  }

  titleOfRelated(pageId: string): string {
    return this.availablePages.find(p => p.id === pageId)?.title
      ?? this.translate.instant('chapterView.deletedPage');
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
        const details: string[] = [];
        if (impact.scenes > 0) {
          const key = impact.scenes > 1 ? 'chapterView.deleteScenesPlural' : 'chapterView.deleteScenes';
          details.push(this.translate.instant(key, { n: impact.scenes }));
        }
        details.push(this.translate.instant('chapterView.irreversible'));

        this.confirmDialog.confirm({
          title: this.translate.instant('chapterView.deleteChapterTitle'),
          message: this.translate.instant('chapterView.deleteChapterMessage', { name: chapter.name }),
          details,
          confirmLabel: this.translate.instant('common.delete'),
          variant: 'danger'
        }).then(ok => {
          if (!ok) return;
          this.campaignService.deleteChapter(chapter.id!).subscribe({
            next: () => this.router.navigate(['/campaigns', this.campaignId, 'arcs', this.arcId]),
            error: () => console.error('Erreur lors de la suppression du chapitre')
          });
        });
      },
      error: () => console.error('Impossible de récupérer les dépendances du chapitre')
    });
  }
}
