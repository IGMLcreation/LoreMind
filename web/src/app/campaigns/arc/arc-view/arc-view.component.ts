import { Component, OnInit, OnDestroy, DestroyRef } from '@angular/core';

import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DataSyncService } from '../../../services/data-sync.service';
import { LucideAngularModule, Pencil, Trash2, Plus } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { resolveCampaignIcon } from '../../campaign-icons';
import { CampaignService } from '../../../services/campaign.service';
import { NpcService } from '../../../services/npc.service';
import { RandomTableService } from '../../../services/random-table.service';
import { EnemyService } from '../../../services/enemy.service';
import { PageService } from '../../../services/page.service';
import { LayoutService } from '../../../services/layout.service';
import { PageTitleService } from '../../../services/page-title.service';
import { Arc, Quest } from '../../../services/campaign.model';
import { Page } from '../../../services/page.model';
import { loadCampaignTreeData, buildCampaignSidebarConfig } from '../../campaign-tree.helper';
import { ImageGalleryComponent } from '../../../shared/image-gallery/image-gallery.component';
import { ConfirmDialogService } from '../../../shared/confirm-dialog/confirm-dialog.service';

/**
 * Écran de consultation d'un Arc narratif (lecture seule).
 * Route : /campaigns/:campaignId/arcs/:arcId
 *
 * Niveau 1 — cutover : la grille « quêtes du hub » (chapitres-as-quêtes) a été
 * retirée. Les quêtes sont désormais une entité orthogonale, gérées dans la branche
 * « Quêtes » de la campagne. Les chapitres restent navigables via l'arbre de la sidebar.
 */
@Component({
    selector: 'app-arc-view',
    imports: [RouterModule, LucideAngularModule, ImageGalleryComponent, TranslatePipe],
    templateUrl: './arc-view.component.html',
    styleUrls: ['./arc-view.component.scss']
})
export class ArcViewComponent implements OnInit, OnDestroy {
  readonly Pencil = Pencil;
  readonly Trash2 = Trash2;
  readonly Plus = Plus;
  readonly resolveCampaignIcon = resolveCampaignIcon;

  campaignId = '';
  arcId = '';
  arc: Arc | null = null;
  /** Quêtes rattachées à cet arc (affichées si l'arc est un HUB). */
  hubQuests: Quest[] = [];

  /** ID du Lore associé à la campagne (null si pas d'univers lié). */
  loreId: string | null = null;
  /** Pages du Lore — pour résoudre relatedPageIds en titres. */
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
    private dataSync: DataSyncService,
    private destroyRef: DestroyRef
  ) {}

  ngOnInit(): void {
    this.dataSync.onChange(this.destroyRef, () => {
      if (this.campaignId && this.arcId) this.load();
    });
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(pm => {
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
      treeData: loadCampaignTreeData(this.campaignService, this.campaignId, this.npcService, this.randomTableService, this.enemyService)
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
      // Les quêtes viennent déjà avec treeData (chargées par loadCampaignTreeData) — pas de fetch en plus.
      this.hubQuests = (treeData.quests ?? [])
        .filter(q => q.arcId === arc.id)
        .sort((a, b) => (a.order ?? 0) - (b.order ?? 0));
      this.pageTitleService.set(arc.name);
      this.layoutService.show(buildCampaignSidebarConfig(campaign, allCampaigns, treeData, this.campaignId, this.translate));
    });
  }

  /** Crée une quête rattachée à CET arc HUB (arcId pré-rempli). */
  createQuest(): void {
    this.router.navigate(['/campaigns', this.campaignId, 'quests', 'create'], { queryParams: { arcId: this.arcId } });
  }

  titleOfRelated(pageId: string): string {
    return this.availablePages.find(p => p.id === pageId)?.title ?? this.translate.instant('arcView.deletedPage');
  }

  editMode(): void {
    this.router.navigate(['/campaigns', this.campaignId, 'arcs', this.arcId, 'edit']);
  }

  /**
   * Suppression en cascade : récupère d'abord le compte de chapitres / scènes
   * qui tomberont avec l'arc, l'annonce dans la confirmation, puis délègue au
   * backend (transaction atomique).
   */
  deleteArc(): void {
    if (!this.arc) return;
    const arc = this.arc;
    this.campaignService.getArcDeletionImpact(arc.id!).subscribe({
      next: impact => {
        const parts: string[] = [];
        if (impact.chapters > 0) {
          parts.push(this.translate.instant(impact.chapters > 1 ? 'arcView.chaptersPlural' : 'arcView.chaptersSingular', { n: impact.chapters }));
        }
        if (impact.scenes > 0) {
          parts.push(this.translate.instant(impact.scenes > 1 ? 'arcView.scenesPlural' : 'arcView.scenesSingular', { n: impact.scenes }));
        }

        const details: string[] = [];
        if (parts.length) {
          details.push(this.translate.instant('arcView.deleteCascade', { parts: parts.join(', ') }));
        }
        details.push(this.translate.instant('arcView.irreversible'));

        this.confirmDialog.confirm({
          title: this.translate.instant('arcView.deleteConfirmTitle'),
          message: this.translate.instant('arcView.deleteConfirmMessage', { name: arc.name }),
          details,
          confirmLabel: this.translate.instant('common.delete'),
          variant: 'danger'
        }).then(ok => {
          if (!ok) return;
          this.campaignService.deleteArc(arc.id!).subscribe({
            next: () => this.router.navigate(['/campaigns', this.campaignId]),
            error: () => console.error('Erreur lors de la suppression de l\'arc')
          });
        });
      },
      error: () => console.error('Impossible de récupérer les dépendances de l\'arc')
    });
  }

  ngOnDestroy(): void {
    // Volontairement vide : la sidebar reste prise en charge par le composant suivant.
  }
}
