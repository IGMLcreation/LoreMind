import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { LucideAngularModule, Trash2, Sparkles } from 'lucide-angular';
import { CampaignService } from '../../services/campaign.service';
import { PageService } from '../../services/page.service';
import { LayoutService, GlobalItem } from '../../services/layout.service';
import { PageTitleService } from '../../services/page-title.service';
import { Campaign, Scene, SceneBranch } from '../../services/campaign.model';
import { Page } from '../../services/page.model';
import { loadCampaignTreeData, buildCampaignTree } from '../campaign-tree.helper';
import { ExpandableSectionComponent } from '../../shared/expandable-section/expandable-section.component';
import { LoreLinkPickerComponent } from '../../shared/lore-link-picker/lore-link-picker.component';
import { AiChatDrawerComponent } from '../../shared/ai-chat-drawer/ai-chat-drawer.component';
import { ImageGalleryComponent } from '../../shared/image-gallery/image-gallery.component';

/**
 * Écran de détail/modification d'une Scène.
 * Route : /campaigns/:campaignId/arcs/:arcId/chapters/:chapterId/scenes/:sceneId
 */
@Component({
  selector: 'app-scene-edit',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, LucideAngularModule, ExpandableSectionComponent, LoreLinkPickerComponent, AiChatDrawerComponent, ImageGalleryComponent],
  templateUrl: './scene-edit.component.html',
  styleUrls: ['./scene-edit.component.scss']
})
export class SceneEditComponent implements OnInit, OnDestroy {
  readonly Trash2 = Trash2;
  readonly Sparkles = Sparkles;

  /** État drawer chat IA (b5.7 — intégration Campagne). */
  chatOpen = false;
  readonly chatQuickSuggestions = [
    'Propose une ambiance sensorielle immersive pour cette scène',
    'Suggère une narration d\'ouverture à lire aux joueurs',
    'Imagine 2 choix avec conséquences marquantes'
  ];

  toggleChat(): void { this.chatOpen = !this.chatOpen; }

  form: FormGroup;
  campaignId = '';
  arcId = '';
  chapterId = '';
  sceneId = '';
  scene: Scene | null = null;

  availablePages: Page[] = [];
  loreId: string | null = null;
  relatedPageIds: string[] = [];
  illustrationImageIds: string[] = [];

  /** Scènes du chapitre courant (hors scène éditée) — alimente le dropdown des cibles. */
  siblingScenes: Scene[] = [];
  /** Branches narratives (état local mutable, persisté au submit). */
  branches: SceneBranch[] = [];

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private campaignService: CampaignService,
    private pageService: PageService,
    private layoutService: LayoutService,
    private pageTitleService: PageTitleService
  ) {
    this.form = this.fb.group({
      name:                 ['', Validators.required],
      description:          [''],
      // Contexte et ambiance
      location:             [''],
      timing:               [''],
      atmosphere:           [''],
      // Narration
      playerNarration:      [''],
      // Secrets MJ
      gmSecretNotes:        [''],
      // Choix
      choicesConsequences:  [''],
      // Combat
      combatDifficulty:     [''],
      enemies:              ['']
    });
  }

  ngOnInit(): void {
    // On s'abonne à paramMap plutôt que de lire snapshot une fois : Angular
    // réutilise le composant quand on navigue entre scènes frères via l'arbre
    // (même route pattern), et ngOnInit ne se relance pas.
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
        this.loadAll();
      }
    });
  }

  private loadAll(): void {
    forkJoin({
      campaign: this.campaignService.getCampaignById(this.campaignId),
      allCampaigns: this.campaignService.getAllCampaigns(),
      scene: this.campaignService.getSceneById(this.sceneId),
      chapterScenes: this.campaignService.getScenes(this.chapterId),
      treeData: loadCampaignTreeData(this.campaignService, this.campaignId)
    }).pipe(
      switchMap(data => {
        const lid = data.campaign.loreId ?? null;
        const pages$ = lid ? this.pageService.getByLoreId(lid) : of([] as Page[]);
        return pages$.pipe(switchMap(pages => of({ ...data, pages, loreId: lid })));
      })
    ).subscribe(({ campaign, allCampaigns, scene, chapterScenes, treeData, pages, loreId }) => {
      this.scene = scene;
      this.pageTitleService.set(scene.name);
      this.loreId = loreId;
      this.availablePages = pages;
      this.relatedPageIds = [...(scene.relatedPageIds ?? [])];
      this.illustrationImageIds = [...(scene.illustrationImageIds ?? [])];
      this.siblingScenes = chapterScenes.filter(s => s.id !== this.sceneId);
      this.branches = (scene.branches ?? []).map(b => ({ ...b }));
      this.form.patchValue({
        name:                 scene.name,
        description:          scene.description ?? '',
        location:             scene.location ?? '',
        timing:               scene.timing ?? '',
        atmosphere:           scene.atmosphere ?? '',
        playerNarration:      scene.playerNarration ?? '',
        gmSecretNotes:        scene.gmSecretNotes ?? '',
        choicesConsequences:  scene.choicesConsequences ?? '',
        combatDifficulty:     scene.combatDifficulty ?? '',
        enemies:              scene.enemies ?? ''
      });

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

  submit(): void {
    if (this.form.invalid || !this.scene) return;
    this.campaignService.updateScene(this.sceneId, {
      name:                 this.form.value.name,
      description:          this.form.value.description,
      chapterId:            this.chapterId,
      order:                this.scene.order ?? 1,
      location:             this.form.value.location,
      timing:               this.form.value.timing,
      atmosphere:           this.form.value.atmosphere,
      playerNarration:      this.form.value.playerNarration,
      gmSecretNotes:        this.form.value.gmSecretNotes,
      choicesConsequences:  this.form.value.choicesConsequences,
      combatDifficulty:     this.form.value.combatDifficulty,
      enemies:              this.form.value.enemies,
      relatedPageIds:       this.relatedPageIds,
      illustrationImageIds: this.illustrationImageIds,
      branches:             this.branches
    }).subscribe({
      next: () => this.router.navigate(['/campaigns', this.campaignId, 'arcs', this.arcId, 'chapters', this.chapterId, 'scenes', this.sceneId]),
      error: () => console.error('Erreur lors de la sauvegarde')
    });
  }

  delete(): void {
    if (!confirm(`Supprimer la scène "${this.scene?.name}" ? Cette action est irréversible.`)) return;
    this.campaignService.deleteScene(this.sceneId).subscribe({
      next: () => this.router.navigate(['/campaigns', this.campaignId]),
      error: () => console.error('Erreur lors de la suppression')
    });
  }

  cancel(): void {
    this.router.navigate(['/campaigns', this.campaignId, 'arcs', this.arcId, 'chapters', this.chapterId, 'scenes', this.sceneId]);
  }

  // ─────────────── Gestion des branches narratives ───────────────

  trackByIndex = (i: number) => i;

  addBranch(): void {
    this.branches.push({ label: '', targetSceneId: '', condition: '' });
  }

  removeBranch(index: number): void {
    this.branches.splice(index, 1);
  }

  updateBranchLabel(index: number, value: string): void {
    this.branches[index].label = value;
  }

  updateBranchTarget(index: number, value: string): void {
    this.branches[index].targetSceneId = value;
  }

  updateBranchCondition(index: number, value: string): void {
    this.branches[index].condition = value;
  }

  ngOnDestroy(): void {
    this.layoutService.hide();
  }
}
