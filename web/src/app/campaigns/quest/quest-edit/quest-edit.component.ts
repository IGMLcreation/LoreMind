import { Component, OnInit, OnDestroy, DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { LucideAngularModule, Trash2 } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { CampaignService } from '../../../services/campaign.service';
import { QuestService } from '../../../services/quest.service';
import { CampaignFlagService } from '../../../services/campaign-flag.service';
import { NpcService } from '../../../services/npc.service';
import { RandomTableService } from '../../../services/random-table.service';
import { EnemyService } from '../../../services/enemy.service';
import { PageService } from '../../../services/page.service';
import { LayoutService } from '../../../services/layout.service';
import { PageTitleService } from '../../../services/page-title.service';
import { Quest, QuestCreate, Prerequisite, QuestNodeRef, Chapter, Scene } from '../../../services/campaign.model';
import { Page } from '../../../services/page.model';
import { loadCampaignTreeData, buildCampaignSidebarConfig } from '../../campaign-tree.helper';
import { IconPickerComponent } from '../../../shared/icon-picker/icon-picker.component';
import { ExpandableSectionComponent } from '../../../shared/expandable-section/expandable-section.component';
import { PrerequisiteEditorComponent } from '../../../shared/prerequisite-editor/prerequisite-editor.component';
import { NodePickerComponent } from '../../../shared/node-picker/node-picker.component';
import { LoreLinkPickerComponent } from '../../../shared/lore-link-picker/lore-link-picker.component';
import { ImageGalleryComponent } from '../../../shared/image-gallery/image-gallery.component';
import { CAMPAIGN_ICON_OPTIONS } from '../../campaign-icons';
import { ConfirmDialogService } from '../../../shared/confirm-dialog/confirm-dialog.service';

/**
 * Création / édition d'une Quête (Niveau 1). Un seul composant pour les routes :
 *  - /campaigns/:campaignId/quests/create       (questId absent → mode création)
 *  - /campaigns/:campaignId/quests/:questId/edit (mode édition)
 *
 * Identité + prérequis (cœur d'une quête) + champs narratifs + illustrations,
 * pages Lore liées et nœuds narratifs traversés (chapitres / scènes).
 */
@Component({
    selector: 'app-quest-edit',
    imports: [ReactiveFormsModule, LucideAngularModule, IconPickerComponent, ExpandableSectionComponent, PrerequisiteEditorComponent, NodePickerComponent, LoreLinkPickerComponent, ImageGalleryComponent, TranslatePipe],
    templateUrl: './quest-edit.component.html',
    styleUrls: ['./quest-edit.component.scss']
})
export class QuestEditComponent implements OnInit, OnDestroy {
  readonly Trash2 = Trash2;
  readonly campaignIconOptions = CAMPAIGN_ICON_OPTIONS;
  selectedIcon: string | null = null;

  form: FormGroup;
  campaignId = '';
  questId: string | null = null;   // null = mode création
  quest: Quest | null = null;
  /** Arc de rattachement : query param à la création (arc HUB), conservé à l'édition. */
  arcIdParam: string | null = null;

  prerequisites: Prerequisite[] = [];
  /** Autres quêtes de la campagne (cibles candidates pour QUEST_COMPLETED). */
  availableQuests: Quest[] = [];
  availableFlagNames: string[] = [];

  /** Nœuds narratifs (chapitres / scènes) traversés par la quête. */
  nodes: QuestNodeRef[] = [];
  /**
   * Nœuds de PLOMBERIE (conteneur de scènes en arc SYSTEM) : jamais montrés dans le
   * sélecteur, ré-injectés tels quels à la sauvegarde pour ne pas casser le lien.
   */
  private containerNodes: QuestNodeRef[] = [];
  /** Chapitres / scènes de la campagne (alimentent le node-picker). */
  chapters: Chapter[] = [];
  scenes: Scene[] = [];

  illustrationImageIds: string[] = [];
  relatedPageIds: string[] = [];
  loreId: string | null = null;
  availablePages: Page[] = [];

  get isCreate(): boolean { return this.questId === null; }
  /**
   * Quête rattachée à un arc HUB : son CONTENEUR de scènes est géré automatiquement
   * (créé avec la quête, fusionné dans l'arbre) → le sélecteur de nœuds manuels est
   * masqué pour ne pas exposer cette plomberie. Il ne sert qu'aux quêtes TRANSVERSES.
   */
  get isAttached(): boolean {
    return !!(this.questId ? this.quest?.arcId : this.arcIdParam);
  }
  get prereqFilled(): boolean { return this.prerequisites.length > 0; }
  get gmNotesFilled(): boolean { return !!this.form.value.gmNotes; }
  get objectivesFilled(): boolean {
    const v = this.form.value;
    return !!(v.playerObjectives || v.narrativeStakes);
  }
  get nodesFilled(): boolean { return this.nodes.length > 0; }
  get illustrationsFilled(): boolean { return this.illustrationImageIds.length > 0; }
  get loreFilled(): boolean { return this.relatedPageIds.length > 0; }

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private campaignService: CampaignService,
    private questService: QuestService,
    private campaignFlagService: CampaignFlagService,
    private npcService: NpcService,
    private randomTableService: RandomTableService,
    private enemyService: EnemyService,
    private pageService: PageService,
    private layoutService: LayoutService,
    private pageTitleService: PageTitleService,
    private confirmDialog: ConfirmDialogService,
    private translate: TranslateService,
    private destroyRef: DestroyRef
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
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(pm => {
      this.campaignId = pm.get('campaignId')!;
      this.questId = pm.get('questId');   // null sur la route /create
      this.arcIdParam = this.route.snapshot.queryParamMap.get('arcId');  // ?arcId= depuis un arc HUB
      this.loadAll();
    });
  }

  private loadAll(): void {
    forkJoin({
      campaign: this.campaignService.getCampaignById(this.campaignId),
      allCampaigns: this.campaignService.getAllCampaigns(),
      quests: this.questService.getByCampaign(this.campaignId),
      treeData: loadCampaignTreeData(this.campaignService, this.campaignId, this.npcService, this.randomTableService, this.enemyService)
    }).pipe(
      switchMap(data => {
        const lid = data.campaign.loreId ?? null;
        const pages$ = lid ? this.pageService.getByLoreId(lid) : of([] as Page[]);
        return pages$.pipe(switchMap(pages => of({ ...data, pages, loreId: lid })));
      })
    ).subscribe(({ campaign, allCampaigns, quests, treeData, pages, loreId }) => {
      this.loreId = loreId;
      this.availablePages = pages;
      // L'arc SYSTEM (« Quêtes libres ») est de la plomberie : ses chapitres-conteneurs
      // et leurs scènes ne sont pas des cibles de lien pour le node-picker.
      const systemArcIds = new Set((treeData.arcs ?? []).filter(a => a.type === 'SYSTEM').map(a => a.id));
      const containerChapterIds = new Set(Object.values(treeData.chaptersByArc).flat()
        .filter(ch => systemArcIds.has(ch.arcId)).map(ch => ch.id));
      this.chapters = Object.values(treeData.chaptersByArc).flat()
        .filter(ch => !systemArcIds.has(ch.arcId));
      this.scenes = Object.values(treeData.scenesByChapter).flat()
        .filter(sc => !containerChapterIds.has(sc.chapterId));

      this.availableQuests = quests.filter(q => q.id !== this.questId);
      const current = this.questId ? quests.find(q => q.id === this.questId) ?? null : null;
      this.quest = current;
      if (current) {
        this.selectedIcon = current.icon ?? null;
        this.prerequisites = [...(current.prerequisites ?? [])];
        const allNodes = current.nodes ?? [];
        this.containerNodes = allNodes.filter(n => n.nodeType === 'CHAPTER' && containerChapterIds.has(n.nodeId));
        this.nodes = allNodes.filter(n => !this.containerNodes.includes(n));
        this.relatedPageIds = [...(current.relatedPageIds ?? [])];
        this.illustrationImageIds = [...(current.illustrationImageIds ?? [])];
        this.pageTitleService.set(current.name);
        this.form.patchValue({
          name:             current.name,
          description:      current.description ?? '',
          gmNotes:          current.gmNotes ?? '',
          playerObjectives: current.playerObjectives ?? '',
          narrativeStakes:  current.narrativeStakes ?? ''
        });
      }

      this.campaignFlagService.listReferenced(this.campaignId).subscribe({
        next: names => this.availableFlagNames = names
      });

      this.layoutService.show(buildCampaignSidebarConfig(campaign, allCampaigns, treeData, this.campaignId, this.translate));
    });
  }

  onPrerequisitesChange(next: Prerequisite[]): void { this.prerequisites = next; }

  submit(): void {
    if (this.form.invalid) return;
    const payload: QuestCreate = {
      name:                 this.form.value.name,
      // Création : arcId du query param (arc HUB) ; édition : on conserve le rattachement existant.
      arcId:                this.questId ? (this.quest?.arcId ?? null) : this.arcIdParam,
      description:          this.form.value.description,
      icon:                 this.selectedIcon,
      order:                this.quest?.order ?? this.availableQuests.length + 1,
      prerequisites:        this.prerequisites,
      nodes:                [...this.containerNodes, ...this.nodes],
      gmNotes:              this.form.value.gmNotes,
      playerObjectives:     this.form.value.playerObjectives,
      narrativeStakes:      this.form.value.narrativeStakes,
      relatedPageIds:       this.relatedPageIds,
      illustrationImageIds: this.illustrationImageIds
    };

    const op$ = this.questId
      ? this.questService.update(this.campaignId, this.questId, { ...payload, campaignId: this.campaignId } as Quest)
      : this.questService.create(this.campaignId, payload);

    op$.subscribe({
      next: created => this.router.navigate(['/campaigns', this.campaignId, 'quests', this.questId ?? created.id]),
      error: () => console.error('Erreur lors de l\'enregistrement de la quête')
    });
  }

  delete(): void {
    if (!this.questId) return;
    this.confirmDialog.confirm({
      title: this.translate.instant('questEdit.deleteTitle'),
      message: this.translate.instant('questEdit.deleteMessage', { name: this.quest?.name }),
      details: [this.translate.instant('questEdit.irreversible')],
      confirmLabel: this.translate.instant('common.delete'),
      variant: 'danger'
    }).then(ok => {
      if (!ok || !this.questId) return;
      this.questService.delete(this.campaignId, this.questId).subscribe({
        next: () => this.router.navigate(['/campaigns', this.campaignId, 'quests']),
        error: () => console.error('Erreur lors de la suppression de la quête')
      });
    });
  }

  cancel(): void {
    this.router.navigate(['/campaigns', this.campaignId, 'quests']);
  }

  ngOnDestroy(): void {}
}
