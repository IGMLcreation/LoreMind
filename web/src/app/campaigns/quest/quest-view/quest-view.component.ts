import { Component, OnInit, OnDestroy, DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { LucideAngularModule, Pencil, Trash2, BookOpen, MapPin, Network } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { resolveCampaignIcon } from '../../campaign-icons';
import { CampaignService } from '../../../services/campaign.service';
import { QuestService } from '../../../services/quest.service';
import { NpcService } from '../../../services/npc.service';
import { RandomTableService } from '../../../services/random-table.service';
import { EnemyService } from '../../../services/enemy.service';
import { PageService } from '../../../services/page.service';
import { LayoutService } from '../../../services/layout.service';
import { PageTitleService } from '../../../services/page-title.service';
import { Quest, Prerequisite, QuestNodeRef, Chapter, Scene } from '../../../services/campaign.model';
import { Page } from '../../../services/page.model';
import { loadCampaignTreeData, buildCampaignSidebarConfig } from '../../campaign-tree.helper';
import { ImageGalleryComponent } from '../../../shared/image-gallery/image-gallery.component';
import { ConfirmDialogService } from '../../../shared/confirm-dialog/confirm-dialog.service';

/**
 * Écran de consultation d'une Quête (lecture seule, Niveau 1).
 * Route : /campaigns/:campaignId/quests/:questId
 *
 * Les quêtes sont ORTHOGONALES à l'arbre Arc→Chapitre→Scène. La vue résout les
 * références faibles (prérequis, nœuds CHAPTER/SCENE, pages Lore) en libellés.
 */
@Component({
    selector: 'app-quest-view',
    imports: [RouterModule, LucideAngularModule, ImageGalleryComponent, TranslatePipe],
    templateUrl: './quest-view.component.html',
    styleUrls: ['./quest-view.component.scss']
})
export class QuestViewComponent implements OnInit, OnDestroy {
  readonly Pencil = Pencil;
  readonly Trash2 = Trash2;
  readonly BookOpen = BookOpen;
  readonly MapPin = MapPin;
  readonly Network = Network;
  readonly resolveCampaignIcon = resolveCampaignIcon;

  campaignId = '';
  questId = '';
  quest: Quest | null = null;

  loreId: string | null = null;
  availablePages: Page[] = [];
  /** Autres quêtes — pour résoudre les prérequis QUEST_COMPLETED en noms. */
  availableQuests: Quest[] = [];
  /** Chapitres / scènes de la campagne — pour résoudre les nœuds en libellés. */
  chapters: Chapter[] = [];
  scenes: Scene[] = [];
  /** Arcs SYSTEM (« Quêtes libres ») : leurs chapitres-conteneurs sont de la plomberie. */
  private systemArcIds = new Set<string>();

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private campaignService: CampaignService,
    private questService: QuestService,
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
      const newQuestId = pm.get('questId')!;
      if (newQuestId !== this.questId || newCampaignId !== this.campaignId) {
        this.campaignId = newCampaignId;
        this.questId = newQuestId;
        this.load();
      }
    });
  }

  private load(): void {
    forkJoin({
      campaign: this.campaignService.getCampaignById(this.campaignId),
      allCampaigns: this.campaignService.getAllCampaigns(),
      quest: this.questService.getById(this.campaignId, this.questId),
      quests: this.questService.getByCampaign(this.campaignId),
      treeData: loadCampaignTreeData(this.campaignService, this.campaignId, this.npcService, this.randomTableService, this.enemyService)
    }).pipe(
      switchMap(data => {
        const lid = data.campaign.loreId ?? null;
        const pages$ = lid ? this.pageService.getByLoreId(lid) : of([] as Page[]);
        return pages$.pipe(switchMap(pages => of({ ...data, pages, loreId: lid })));
      })
    ).subscribe(({ campaign, allCampaigns, quest, quests, treeData, pages, loreId }) => {
      this.quest = quest;
      this.loreId = loreId;
      this.availablePages = pages;
      this.availableQuests = quests;
      this.chapters = Object.values(treeData.chaptersByArc).flat();
      this.scenes = Object.values(treeData.scenesByChapter).flat();
      this.systemArcIds = new Set((treeData.arcs ?? [])
        .filter(a => a.type === 'SYSTEM').map(a => a.id!));
      this.pageTitleService.set(quest.name);

      this.layoutService.show(buildCampaignSidebarConfig(campaign, allCampaigns, treeData, this.campaignId, this.translate));
    });
  }

  titleOfRelated(pageId: string): string {
    return this.availablePages.find(p => p.id === pageId)?.title
      ?? this.translate.instant('questView.deletedPage');
  }

  /** Libellé lisible d'un prérequis (réutilisé en lecture seule). */
  prereqLabel(prereq: Prerequisite): string {
    switch (prereq.kind) {
      case 'QUEST_COMPLETED': {
        const q = this.availableQuests.find(x => x.id === prereq.questId);
        const name = q?.name ?? this.translate.instant('questView.deletedQuest');
        return this.translate.instant('questView.prereqQuestCompleted', { name });
      }
      case 'SESSION_REACHED':
        return this.translate.instant('questView.prereqSessionReached', { n: prereq.minSessionNumber });
      case 'FLAG_SET':
        return this.translate.instant('questView.prereqFlagSet', { flag: prereq.flagName });
    }
  }

  /**
   * Nœuds à AFFICHER : les vrais liens narratifs. Le conteneur de scènes de la quête
   * (chapitre d'un arc SYSTEM) est de la plomberie — ses scènes sont déjà visibles
   * dans l'arbre sous la quête, inutile de montrer le lien technique.
   */
  get visibleNodes(): QuestNodeRef[] {
    return (this.quest?.nodes ?? []).filter(n => {
      if (n.nodeType !== 'CHAPTER') return true;
      const ch = this.chapters.find(c => c.id === n.nodeId);
      return !ch || !this.systemArcIds.has(ch.arcId);
    });
  }

  /** Libellé d'un nœud narratif (chapitre, ou « chapitre › scène »). */
  nodeLabel(n: QuestNodeRef): string {
    if (n.nodeType === 'CHAPTER') {
      return this.chapters.find(c => c.id === n.nodeId)?.name
        ?? this.translate.instant('questView.deletedNode');
    }
    const scene = this.scenes.find(s => s.id === n.nodeId);
    if (!scene) return this.translate.instant('questView.deletedNode');
    const chapter = this.chapters.find(c => c.id === scene.chapterId);
    return chapter ? `${chapter.name} › ${scene.name}` : scene.name;
  }

  /**
   * Conteneur de scènes de la quête : premier nœud CHAPTER résolu. C'est lui qui porte
   * la CARTE (graphe des scènes) — indispensable depuis la fusion quête/conteneur dans
   * l'arbre, sinon le graphe d'une quête de hub serait inaccessible.
   */
  get containerChapter(): Chapter | null {
    for (const n of this.quest?.nodes ?? []) {
      if (n.nodeType !== 'CHAPTER') continue;
      const ch = this.chapters.find(c => c.id === n.nodeId);
      if (ch) return ch;
    }
    return null;
  }

  /** Ouvre la carte (graphe) des scènes de la quête — création libre de scènes incluse. */
  openGraph(): void {
    const ch = this.containerChapter;
    if (!ch) return;
    this.router.navigate(['/campaigns', this.campaignId, 'arcs', ch.arcId, 'chapters', ch.id, 'graph']);
  }

  editMode(): void {
    this.router.navigate(['/campaigns', this.campaignId, 'quests', this.questId, 'edit']);
  }

  /** Suppression simple : une quête n'a pas d'enfants (les nœuds sont des références faibles). */
  deleteQuest(): void {
    if (!this.quest) return;
    const quest = this.quest;
    this.confirmDialog.confirm({
      title: this.translate.instant('questView.deleteTitle'),
      message: this.translate.instant('questView.deleteMessage', { name: quest.name }),
      details: [this.translate.instant('questView.irreversible')],
      confirmLabel: this.translate.instant('common.delete'),
      variant: 'danger'
    }).then(ok => {
      if (!ok || !quest.id) return;
      this.questService.delete(this.campaignId, quest.id).subscribe({
        next: () => this.router.navigate(['/campaigns', this.campaignId, 'quests']),
        error: () => console.error('Erreur lors de la suppression de la quête')
      });
    });
  }

  ngOnDestroy(): void {
    // Volontairement vide : la sidebar reste prise en charge par le composant suivant.
  }
}
