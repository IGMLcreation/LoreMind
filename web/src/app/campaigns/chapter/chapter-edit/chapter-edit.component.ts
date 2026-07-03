import { Component, OnInit, OnDestroy } from '@angular/core';

import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { LucideAngularModule, Trash2, Sparkles } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
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
import { LoreLinkPickerComponent } from '../../../shared/lore-link-picker/lore-link-picker.component';
import { AiChatDrawerComponent } from '../../../shared/ai-chat-drawer/ai-chat-drawer.component';
import { ImageGalleryComponent } from '../../../shared/image-gallery/image-gallery.component';
import { IconPickerComponent } from '../../../shared/icon-picker/icon-picker.component';
import { ExpandableSectionComponent } from '../../../shared/expandable-section/expandable-section.component';
import { CAMPAIGN_ICON_OPTIONS } from '../../campaign-icons';
import { ConfirmDialogService } from '../../../shared/confirm-dialog/confirm-dialog.service';
import { EntityAssistPanelComponent } from '../../../shared/entity-assist-panel/entity-assist-panel.component';
import { FieldProposal } from '../../../services/entity-assist.model';
import { SceneDraftPanelComponent } from '../../../shared/scene-draft-panel/scene-draft-panel.component';

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
    ExpandableSectionComponent,
    EntityAssistPanelComponent,
    SceneDraftPanelComponent,
    TranslatePipe
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
  readonly chatQuickSuggestions: string[];

  toggleChat(): void { this.chatOpen = !this.chatOpen; }

  /** Applique au FORMULAIRE les champs étoffés retenus (Pilier A). Non destructif. */
  onAssistApplied(fields: FieldProposal[]): void {
    const patch: Record<string, string> = {};
    for (const f of fields) {
      if (this.form.get(f.key)) patch[f.key] = f.proposedValue;
    }
    this.form.patchValue(patch);
  }

  /** Après création de scènes par l'IA : on ouvre le graphe du chapitre pour les voir. */
  onScenesCreated(n: number): void {
    if (n > 0) {
      this.router.navigate(['/campaigns', this.campaignId, 'arcs', this.arcId, 'chapters', this.chapterId, 'graph']);
    }
  }

  form: FormGroup;
  campaignId = '';
  arcId = '';
  chapterId = '';
  chapter: Chapter | null = null;

  /** `?assist=draft-scenes` (bouton « Corriger » du guidage) → panneau IA déployé d'office. */
  assistParam: string | null = null;

  availablePages: Page[] = [];
  loreId: string | null = null;
  relatedPageIds: string[] = [];
  illustrationImageIds: string[] = [];

  // ─────────────── État « rempli » par section (pastille de l'en-tête) ───────────────
  // Seul le titre est requis ; ces getters signalent ce qui contient déjà du contenu.
  get illustrationsFilled(): boolean { return this.illustrationImageIds.length > 0; }
  get gmNotesFilled(): boolean { return !!this.form.value.gmNotes; }
  get objectivesStakesFilled(): boolean {
    const v = this.form.value;
    return !!(v.playerObjectives || v.narrativeStakes);
  }
  get loreFilled(): boolean { return this.relatedPageIds.length > 0; }

  constructor(
    private fb: FormBuilder,
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
  ) {
    this.form = this.fb.group({
      name:             ['', Validators.required],
      description:      [''],
      gmNotes:          [''],
      playerObjectives: [''],
      narrativeStakes:  ['']
    });
    this.chatQuickSuggestions = [
      this.translate.instant('chapterEdit.chatSuggestion1'),
      this.translate.instant('chapterEdit.chatSuggestion2'),
      this.translate.instant('chapterEdit.chatSuggestion3')
    ];
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
        this.assistParam = this.route.snapshot.queryParamMap.get('assist');
        this.loadAll();
      }
    });
  }

  private loadAll(): void {
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
      this.pageTitleService.set(chapter.name);
      this.loreId = loreId;
      this.availablePages = pages;
      this.relatedPageIds = [...(chapter.relatedPageIds ?? [])];
      this.selectedIcon = chapter.icon ?? null;
      this.illustrationImageIds = [...(chapter.illustrationImageIds ?? [])];

      this.form.patchValue({
        name:             chapter.name,
        description:      chapter.description ?? '',
        gmNotes:          chapter.gmNotes ?? '',
        playerObjectives: chapter.playerObjectives ?? '',
        narrativeStakes:  chapter.narrativeStakes ?? ''
      });

      this.layoutService.show(buildCampaignSidebarConfig(campaign, allCampaigns, treeData, this.campaignId, this.translate));
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
      icon:             this.selectedIcon
    }).subscribe({
      next: () => this.router.navigate(['/campaigns', this.campaignId, 'arcs', this.arcId, 'chapters', this.chapterId]),
      error: () => console.error('Erreur lors de la sauvegarde')
    });
  }

  delete(): void {
    this.confirmDialog.confirm({
      title: this.translate.instant('chapterEdit.deleteTitle'),
      message: this.translate.instant('chapterEdit.deleteMessage', { name: this.chapter?.name }),
      details: [this.translate.instant('chapterEdit.irreversible')],
      confirmLabel: this.translate.instant('common.delete'),
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
