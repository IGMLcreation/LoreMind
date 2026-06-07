import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { LucideAngularModule, Pencil, Trash2 } from 'lucide-angular';
import { resolveCampaignIcon } from '../../campaign-icons';
import { CampaignService } from '../../../services/campaign.service';
import { CharacterService } from '../../../services/character.service';
import { NpcService } from '../../../services/npc.service';
import { RandomTableService } from '../../../services/random-table.service';
import { PageService } from '../../../services/page.service';
import { LayoutService } from '../../../services/layout.service';
import { PageTitleService } from '../../../services/page-title.service';
import { Scene } from '../../../services/campaign.model';
import { Page } from '../../../services/page.model';
import { loadCampaignTreeData, buildCampaignSidebarConfig } from '../../campaign-tree.helper';
import { ImageGalleryComponent } from '../../../shared/image-gallery/image-gallery.component';
import { ConfirmDialogService } from '../../../shared/confirm-dialog/confirm-dialog.service';

/**
 * Écran de consultation d'une Scène (lecture seule).
 * Route : /campaigns/:campaignId/arcs/:arcId/chapters/:chapterId/scenes/:sceneId
 */
@Component({
  selector: 'app-scene-view',
  standalone: true,
  imports: [CommonModule, RouterModule, LucideAngularModule, ImageGalleryComponent],
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

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private campaignService: CampaignService,
    private characterService: CharacterService,
    private npcService: NpcService,
    private randomTableService: RandomTableService,
    private pageService: PageService,
    private layoutService: LayoutService,
    private pageTitleService: PageTitleService,
    private confirmDialog: ConfirmDialogService
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
      treeData: loadCampaignTreeData(this.campaignService, this.campaignId, this.characterService, this.npcService, this.randomTableService)
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
      this.pageTitleService.set(scene.name);

      this.layoutService.show(buildCampaignSidebarConfig(campaign, allCampaigns, treeData, this.campaignId));
    });
  }

  titleOfRelated(pageId: string): string {
    return this.availablePages.find(p => p.id === pageId)?.title ?? '(page supprimée)';
  }

  /** Résout le nom d'une pièce cible (pour afficher les sorties inter-pièces). */
  roomNameById(scene: Scene | null, roomId: string): string {
    return scene?.rooms?.find(r => r.id === roomId)?.name ?? '(pièce supprimée)';
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
      title: 'Supprimer la scène',
      message: `Supprimer la scène "${scene.name}" ?`,
      details: ['Cette action est irréversible.'],
      confirmLabel: 'Supprimer',
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
