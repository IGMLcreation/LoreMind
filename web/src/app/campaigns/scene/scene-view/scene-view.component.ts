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
import { PageService } from '../../../services/page.service';
import { LayoutService, GlobalItem } from '../../../services/layout.service';
import { PageTitleService } from '../../../services/page-title.service';
import { Campaign, Scene } from '../../../services/campaign.model';
import { Page } from '../../../services/page.model';
import { loadCampaignTreeData, buildCampaignTree } from '../../campaign-tree.helper';
import { ImageGalleryComponent } from '../../../shared/image-gallery/image-gallery.component';

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
    private pageService: PageService,
    private layoutService: LayoutService,
    private pageTitleService: PageTitleService
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
      treeData: loadCampaignTreeData(this.campaignService, this.campaignId, this.characterService, this.npcService)
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
      '/campaigns', this.campaignId, 'arcs', this.arcId,
      'chapters', this.chapterId, 'scenes', this.sceneId, 'edit'
    ]);
  }

  /** Suppression simple — une scène n'a pas d'enfants. Retour au chapitre parent. */
  deleteScene(): void {
    if (!this.scene) return;
    const scene = this.scene;
    if (!confirm(`Supprimer la scène "${scene.name}" ?\n\nCette action est irréversible.`)) return;
    this.campaignService.deleteScene(scene.id!).subscribe({
      next: () => this.router.navigate([
        '/campaigns', this.campaignId, 'arcs', this.arcId, 'chapters', this.chapterId
      ]),
      error: () => console.error('Erreur lors de la suppression de la scène')
    });
  }

  ngOnDestroy(): void {
    this.layoutService.hide();
  }
}
