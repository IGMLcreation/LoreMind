import { Component, OnInit, OnDestroy } from '@angular/core';

import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { LucideAngularModule, Trash2, Sparkles } from 'lucide-angular';
import { CampaignService } from '../../../services/campaign.service';
import { CharacterService } from '../../../services/character.service';
import { NpcService } from '../../../services/npc.service';
import { RandomTableService } from '../../../services/random-table.service';
import { PageService } from '../../../services/page.service';
import { LayoutService } from '../../../services/layout.service';
import { PageTitleService } from '../../../services/page-title.service';
import { Chapter, Prerequisite, Arc } from '../../../services/campaign.model';
import { Page } from '../../../services/page.model';
import { loadCampaignTreeData, buildCampaignSidebarConfig } from '../../campaign-tree.helper';
import { LoreLinkPickerComponent } from '../../../shared/lore-link-picker/lore-link-picker.component';
import { AiChatDrawerComponent } from '../../../shared/ai-chat-drawer/ai-chat-drawer.component';
import { ImageGalleryComponent } from '../../../shared/image-gallery/image-gallery.component';
import { IconPickerComponent } from '../../../shared/icon-picker/icon-picker.component';
import { PrerequisiteEditorComponent } from '../../../shared/prerequisite-editor/prerequisite-editor.component';
import { CampaignFlagService } from '../../../services/campaign-flag.service';
import { CAMPAIGN_ICON_OPTIONS } from '../../campaign-icons';
import { ConfirmDialogService } from '../../../shared/confirm-dialog/confirm-dialog.service';

/**
 * Écran d'édition d'un Chapitre. Donnée de SCÉNARIO uniquement.
 *
 * Depuis Playthrough : la progression et le toggle des flags se font dans le
 * contexte d'une Partie (voir Playthrough Detail). On garde ici uniquement les
 * prérequis (conditions de déblocage), qui font partie du scénario.
 */
@Component({
    selector: 'app-chapter-edit',
    imports: [
    ReactiveFormsModule,
    LucideAngularModule,
    LoreLinkPickerComponent,
    AiChatDrawerComponent,
    ImageGalleryComponent,
    IconPickerComponent,
    PrerequisiteEditorComponent
],
    templateUrl: './chapter-edit.component.html',
    styleUrls: ['./chapter-edit.component.scss']
})
export class ChapterEditComponent implements OnInit, OnDestroy {
  readonly Trash2 = Trash2;
  readonly Sparkles = Sparkles;
  readonly campaignIconOptions = CAMPAIGN_ICON_OPTIONS;
  selectedIcon: string | null = null;

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

  /** Prérequis (donnée de scénario). */
  prerequisites: Prerequisite[] = [];

  /** Quêtes candidates pour QUEST_COMPLETED (chapitres de la campagne, sauf celui-ci). */
  availableQuests: Chapter[] = [];

  /** Faits déclarés au niveau Campagne (autocomplete FLAG_SET). */
  availableFlagNames: string[] = [];

  /** L'arc parent — pour conditionner la section Hub (prérequis pertinents). */
  parentArc: Arc | null = null;

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private campaignService: CampaignService,
    private characterService: CharacterService,
    private npcService: NpcService,
    private randomTableService: RandomTableService,
    private pageService: PageService,
    private layoutService: LayoutService,
    private pageTitleService: PageTitleService,
    private confirmDialog: ConfirmDialogService,
    private campaignFlagService: CampaignFlagService
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
      treeData: loadCampaignTreeData(this.campaignService, this.campaignId, this.characterService, this.npcService, this.randomTableService)
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
      this.selectedIcon = chapter.icon ?? null;
      this.illustrationImageIds = [...(chapter.illustrationImageIds ?? [])];
      this.mapImageIds = [...(chapter.mapImageIds ?? [])];

      this.prerequisites = [...(chapter.prerequisites ?? [])];

      const allChapters: Chapter[] = [];
      Object.values(treeData.chaptersByArc).forEach(list => allChapters.push(...list));
      this.availableQuests = allChapters.filter(c => c.id !== this.chapterId);

      this.parentArc = treeData.arcs.find(a => a.id === this.arcId) ?? null;

      // Autocomplete des FLAG_SET : les noms de faits déjà référencés ailleurs
      // dans la campagne (déduit des autres quêtes).
      this.campaignFlagService.listReferenced(this.campaignId).subscribe({
        next: names => { this.availableFlagNames = names; }
      });

      this.form.patchValue({
        name:             chapter.name,
        description:      chapter.description ?? '',
        gmNotes:          chapter.gmNotes ?? '',
        playerObjectives: chapter.playerObjectives ?? '',
        narrativeStakes:  chapter.narrativeStakes ?? ''
      });

      this.layoutService.show(buildCampaignSidebarConfig(campaign, allCampaigns, treeData, this.campaignId));
    });
  }

  onPrerequisitesChange(next: Prerequisite[]): void {
    this.prerequisites = next;
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
      prerequisites:    this.prerequisites,
      relatedPageIds:   this.relatedPageIds,
      illustrationImageIds: this.illustrationImageIds,
      mapImageIds:      this.mapImageIds,
      icon:             this.selectedIcon
    }).subscribe({
      next: () => this.router.navigate(['/campaigns', this.campaignId, 'arcs', this.arcId, 'chapters', this.chapterId]),
      error: () => console.error('Erreur lors de la sauvegarde')
    });
  }

  delete(): void {
    this.confirmDialog.confirm({
      title: 'Supprimer le chapitre',
      message: `Supprimer le chapitre "${this.chapter?.name}" ?`,
      details: ['Cette action est irréversible.'],
      confirmLabel: 'Supprimer',
      variant: 'danger'
    }).then(ok => {
      if (!ok) return;
      this.campaignService.deleteChapter(this.chapterId).subscribe({
        next: () => this.router.navigate(['/campaigns', this.campaignId]),
        error: () => console.error('Erreur lors de la suppression')
      });
    });
  }

  cancel(): void {
    this.router.navigate(['/campaigns', this.campaignId, 'arcs', this.arcId, 'chapters', this.chapterId]);
  }

  ngOnDestroy(): void {}
}
