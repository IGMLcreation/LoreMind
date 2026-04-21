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
import { Campaign, Chapter } from '../../services/campaign.model';
import { Page } from '../../services/page.model';
import { loadCampaignTreeData, buildCampaignTree } from '../campaign-tree.helper';
import { LoreLinkPickerComponent } from '../../shared/lore-link-picker/lore-link-picker.component';
import { AiChatDrawerComponent } from '../../shared/ai-chat-drawer/ai-chat-drawer.component';
import { ImageGalleryComponent } from '../../shared/image-gallery/image-gallery.component';

/**
 * Écran de détail/modification d'un Chapitre.
 * Route : /campaigns/:campaignId/arcs/:arcId/chapters/:chapterId
 *
 * Inclut le picker de pages Lore (B2 cross-context) si la campagne parente
 * est associée à un Lore.
 */
@Component({
  selector: 'app-chapter-edit',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, LucideAngularModule, LoreLinkPickerComponent, AiChatDrawerComponent, ImageGalleryComponent],
  templateUrl: './chapter-edit.component.html',
  styleUrls: ['./chapter-edit.component.scss']
})
export class ChapterEditComponent implements OnInit, OnDestroy {
  readonly Trash2 = Trash2;
  readonly Sparkles = Sparkles;

  /** État drawer chat IA (b5.7 — intégration Campagne). */
  chatOpen = false;
  readonly chatQuickSuggestions = [
    'Propose des objectifs clairs pour les joueurs dans ce chapitre',
    'Imagine 2 tensions narratives qui relancent l\'intérêt en milieu de chapitre',
    'Suggère une scène d\'ouverture marquante'
  ];

  toggleChat(): void { this.chatOpen = !this.chatOpen; }

  form: FormGroup;
  campaignId = '';
  arcId = '';
  chapterId = '';
  chapter: Chapter | null = null;

  availablePages: Page[] = [];
  loreId: string | null = null;
  relatedPageIds: string[] = [];
  illustrationImageIds: string[] = [];
  mapImageIds: string[] = [];

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
      name:             ['', Validators.required],
      description:      [''],
      gmNotes:          [''],
      playerObjectives: [''],
      narrativeStakes:  ['']
    });
  }

  ngOnInit(): void {
    // On s'abonne à paramMap plutôt que de lire snapshot une fois : Angular
    // réutilise le composant quand on navigue entre chapitres frères via
    // l'arbre (même route pattern), et ngOnInit ne se relance pas.
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
        this.loadAll();
      }
    });
  }

  private loadAll(): void {
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
      this.pageTitleService.set(chapter.name);
      this.loreId = loreId;
      this.availablePages = pages;
      this.relatedPageIds = [...(chapter.relatedPageIds ?? [])];
      this.illustrationImageIds = [...(chapter.illustrationImageIds ?? [])];
      this.mapImageIds = [...(chapter.mapImageIds ?? [])];
      this.form.patchValue({
        name:             chapter.name,
        description:      chapter.description ?? '',
        gmNotes:          chapter.gmNotes ?? '',
        playerObjectives: chapter.playerObjectives ?? '',
        narrativeStakes:  chapter.narrativeStakes ?? ''
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
    if (this.form.invalid || !this.chapter) return;
    this.campaignService.updateChapter(this.chapterId, {
      name:             this.form.value.name,
      description:      this.form.value.description,
      arcId:            this.arcId,
      order:            this.chapter.order ?? 1,
      gmNotes:          this.form.value.gmNotes,
      playerObjectives: this.form.value.playerObjectives,
      narrativeStakes:  this.form.value.narrativeStakes,
      relatedPageIds:   this.relatedPageIds,
      illustrationImageIds: this.illustrationImageIds,
      mapImageIds:      this.mapImageIds
    }).subscribe({
      next: () => this.router.navigate(['/campaigns', this.campaignId, 'arcs', this.arcId, 'chapters', this.chapterId]),
      error: () => console.error('Erreur lors de la sauvegarde')
    });
  }

  delete(): void {
    if (!confirm(`Supprimer le chapitre "${this.chapter?.name}" ? Cette action est irréversible.`)) return;
    this.campaignService.deleteChapter(this.chapterId).subscribe({
      next: () => this.router.navigate(['/campaigns', this.campaignId]),
      error: () => console.error('Erreur lors de la suppression')
    });
  }

  cancel(): void {
    this.router.navigate(['/campaigns', this.campaignId, 'arcs', this.arcId, 'chapters', this.chapterId]);
  }

  ngOnDestroy(): void {
    this.layoutService.hide();
  }
}
