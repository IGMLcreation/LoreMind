import { Component, OnInit, OnDestroy } from '@angular/core';

import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { LucideAngularModule, Pencil, Trash2 } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { resolveCampaignIcon } from '../../campaign-icons';
import { CampaignService } from '../../../services/campaign.service';
import { NpcService } from '../../../services/npc.service';
import { RandomTableService } from '../../../services/random-table.service';
import { EnemyService } from '../../../services/enemy.service';
import { PageService } from '../../../services/page.service';
import { LayoutService } from '../../../services/layout.service';
import { PageTitleService } from '../../../services/page-title.service';
import { Scene, Room } from '../../../services/campaign.model';
import { Page } from '../../../services/page.model';
import { Enemy } from '../../../services/enemy.model';
import { loadCampaignTreeData, buildCampaignSidebarConfig } from '../../campaign-tree.helper';
import { ImageGalleryComponent } from '../../../shared/image-gallery/image-gallery.component';
import { ConfirmDialogService } from '../../../shared/confirm-dialog/confirm-dialog.service';

/**
 * Écran de consultation d'une Scène (lecture seule).
 * Route : /campaigns/:campaignId/arcs/:arcId/chapters/:chapterId/scenes/:sceneId
 */
@Component({
    selector: 'app-scene-view',
    imports: [RouterModule, LucideAngularModule, ImageGalleryComponent, TranslatePipe],
    templateUrl: './scene-view.component.html',
    styleUrls: ['./scene-view.component.scss']
})
export class SceneViewComponent implements OnInit, OnDestroy {
  readonly Pencil = Pencil;
  readonly Trash2 = Trash2;
  readonly resolveCampaignIcon = resolveCampaignIcon;

  campaignId = '';
  arcId = '';
  chapterId = '';
  sceneId = '';
  scene: Scene | null = null;

  loreId: string | null = null;
  availablePages: Page[] = [];
  /** Bestiaire de la campagne — résout les enemyIds de la scène en fiches. */
  availableEnemies: Enemy[] = [];

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
    private translate: TranslateService
  ) {}

  ngOnInit(): void {
    this.route.paramMap.subscribe(pm => {
      const newCampaignId = pm.get('campaignId')!;
      const newArcId = pm.get('arcId')!;
      const newChapterId = pm.get('chapterId')!;
      const newSceneId = pm.get('sceneId')!;
      if (newSceneId !== this.sceneId ||
          newChapterId !== this.chapterId ||
          newArcId !== this.arcId ||
          newCampaignId !== this.campaignId) {
        this.campaignId = newCampaignId;
        this.arcId = newArcId;
        this.chapterId = newChapterId;
        this.sceneId = newSceneId;
        this.load();
      }
    });
  }

  private load(): void {
    forkJoin({
      campaign: this.campaignService.getCampaignById(this.campaignId),
      allCampaigns: this.campaignService.getAllCampaigns(),
      scene: this.campaignService.getSceneById(this.sceneId),
      treeData: loadCampaignTreeData(this.campaignService, this.campaignId, this.npcService, this.randomTableService, this.enemyService)
    }).pipe(
      switchMap(data => {
        const lid = data.campaign.loreId ?? null;
        const pages$ = lid ? this.pageService.getByLoreId(lid) : of([] as Page[]);
        return pages$.pipe(switchMap(pages => of({ ...data, pages, loreId: lid })));
      })
    ).subscribe(({ campaign, allCampaigns, scene, treeData, pages, loreId }) => {
      this.scene = scene;
      this.loreId = loreId;
      this.availablePages = pages;
      this.availableEnemies = treeData.enemies ?? [];
      this.pageTitleService.set(scene.name);

      this.layoutService.show(buildCampaignSidebarConfig(campaign, allCampaigns, treeData, this.campaignId, this.translate));
    });
  }

  titleOfRelated(pageId: string): string {
    return this.availablePages.find(p => p.id === pageId)?.title ?? this.translate.instant('sceneView.deletedPage');
  }

  /** Fiches du bestiaire liées à la rencontre (IDs orphelins ignorés). */
  get linkedEnemies(): Enemy[] {
    return (this.scene?.enemyIds ?? [])
      .map(id => this.availableEnemies.find(e => e.id === id))
      .filter((e): e is Enemy => !!e);
  }

  /** Libellé d'un chip ennemi : nom + niveau s'il est renseigné. */
  enemyLabel(enemy: Enemy): string {
    const level = enemy.level?.trim();
    return level ? `${enemy.name} (${level})` : enemy.name;
  }

  /** Fiches du bestiaire liées à une pièce (IDs orphelins ignorés). */
  roomLinkedEnemies(room: Room): Enemy[] {
    return (room.enemyIds ?? [])
      .map(id => this.availableEnemies.find(e => e.id === id))
      .filter((e): e is Enemy => !!e);
  }

  /** Résout le nom d'une pièce cible (pour afficher les sorties inter-pièces). */
  roomNameById(scene: Scene | null, roomId: string): string {
    return scene?.rooms?.find(r => r.id === roomId)?.name ?? this.translate.instant('sceneView.deletedRoom');
  }

  editMode(): void {
    this.router.navigate([
      '/campaigns', this.campaignId, 'arcs', this.arcId,
      'chapters', this.chapterId, 'scenes', this.sceneId, 'edit'
    ]);
  }

  /** Suppression simple — une scène n'a pas d'enfants. Retour au chapitre parent. */
  deleteScene(): void {
    if (!this.scene) return;
    const scene = this.scene;
    this.confirmDialog.confirm({
      title: this.translate.instant('sceneView.deleteTitle'),
      message: this.translate.instant('sceneView.deleteMessage', { name: scene.name }),
      details: [this.translate.instant('sceneView.deleteIrreversible')],
      confirmLabel: this.translate.instant('common.delete'),
      variant: 'danger'
    }).then(ok => {
      if (!ok) return;
      this.campaignService.deleteScene(scene.id!).subscribe({
        next: () => this.router.navigate([
          '/campaigns', this.campaignId, 'arcs', this.arcId, 'chapters', this.chapterId
        ]),
        error: () => console.error('Erreur lors de la suppression de la scène')
      });
    });
  }

  ngOnDestroy(): void {
    // Volontairement vide : la sidebar reste prise en charge par le composant
    // suivant (autre sous-route ou le composant detail parent) qui appellera
    // show(). Eviter d'appeler hide() ici previent le clignotement / la
    // disparition de la sidebar lors des navigations internes a la section.
  }
}
