import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { forkJoin } from 'rxjs';
import { LucideAngularModule, ArrowLeft } from 'lucide-angular';
import { CampaignService } from '../../services/campaign.service';
import { CharacterService } from '../../services/character.service';
import { LayoutService, GlobalItem } from '../../services/layout.service';
import { PageTitleService } from '../../services/page-title.service';
import { Campaign, Chapter, Scene } from '../../services/campaign.model';
import { loadCampaignTreeData, buildCampaignTree } from '../campaign-tree.helper';

interface GraphNode { id: string; name: string; displayName: string; x: number; y: number; }
interface GraphEdge { label: string; x1: number; y1: number; x2: number; y2: number; labelX: number; labelY: number; }

/**
 * Vue graphique d'un chapitre : organigramme des scènes et branches narratives.
 * Layout custom (BFS par niveaux) en SVG — évite une dépendance lourde type ngx-graph.
 */
@Component({
  selector: 'app-chapter-graph',
  standalone: true,
  imports: [CommonModule, RouterModule, LucideAngularModule],
  templateUrl: './chapter-graph.component.html',
  styleUrls: ['./chapter-graph.component.scss']
})
export class ChapterGraphComponent implements OnInit, OnDestroy {
  readonly ArrowLeft = ArrowLeft;

  campaignId = '';
  arcId = '';
  chapterId = '';
  chapter: Chapter | null = null;
  scenes: Scene[] = [];

  nodes: GraphNode[] = [];
  edges: GraphEdge[] = [];

  readonly NODE_WIDTH = 220;
  readonly NODE_HEIGHT = 64;
  readonly H_SPACING = 50;
  readonly V_SPACING = 90;
  readonly MAX_LABEL_CHARS = 26;

  svgWidth = 600;
  svgHeight = 400;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private campaignService: CampaignService,
    private characterService: CharacterService,
    private layoutService: LayoutService,
    private pageTitleService: PageTitleService
  ) {}

  ngOnInit(): void {
    this.route.paramMap.subscribe(pm => {
      this.campaignId = pm.get('campaignId')!;
      this.arcId = pm.get('arcId')!;
      this.chapterId = pm.get('chapterId')!;
      this.load();
    });
  }

  private load(): void {
    forkJoin({
      campaign: this.campaignService.getCampaignById(this.campaignId),
      allCampaigns: this.campaignService.getAllCampaigns(),
      chapter: this.campaignService.getChapterById(this.chapterId),
      scenes: this.campaignService.getScenes(this.chapterId),
      treeData: loadCampaignTreeData(this.campaignService, this.campaignId, this.characterService)
    }).subscribe(({ campaign, allCampaigns, chapter, scenes, treeData }) => {
      this.chapter = chapter;
      this.scenes = scenes;
      this.pageTitleService.set(`${chapter.name} — Carte`);
      this.buildGraph();

      const globalItems: GlobalItem[] = allCampaigns.map((c: Campaign) => ({
        id: c.id!, name: c.name, route: `/campaigns/${c.id}`
      }));
      this.layoutService.show({
        title: campaign.name,
        items: buildCampaignTree(this.campaignId, treeData),
        footerLabel: 'Toutes les campagnes',
        createActions: [],
        globalItems,
        globalBackLabel: 'Toutes les campagnes',
        globalBackRoute: '/campaigns'
      });
    });
  }

  /**
   * Layout en niveaux par BFS depuis la scène d'entrée (order le plus bas).
   * Scènes non atteignables rassemblées dans un niveau "orphelin" tout en bas.
   */
  private buildGraph(): void {
    if (this.scenes.length === 0) {
      this.nodes = []; this.edges = [];
      this.svgWidth = 600; this.svgHeight = 200;
      return;
    }

    const sorted = [...this.scenes].sort((a, b) => (a.order ?? 0) - (b.order ?? 0));
    const entry = sorted[0];

    const levelOf = new Map<string, number>();
    levelOf.set(entry.id!, 0);
    const queue: string[] = [entry.id!];
    while (queue.length > 0) {
      const curId = queue.shift()!;
      const curLevel = levelOf.get(curId)!;
      const curScene = this.scenes.find(s => s.id === curId);
      if (!curScene?.branches) continue;
      for (const b of curScene.branches) {
        if (!levelOf.has(b.targetSceneId)) {
          levelOf.set(b.targetSceneId, curLevel + 1);
          queue.push(b.targetSceneId);
        }
      }
    }

    const reachableMax = levelOf.size > 0 ? Math.max(...Array.from(levelOf.values())) : 0;
    const orphanLevel = reachableMax + 1;
    for (const s of this.scenes) {
      if (!levelOf.has(s.id!)) levelOf.set(s.id!, orphanLevel);
    }

    const byLevel = new Map<number, Scene[]>();
    for (const s of this.scenes) {
      const lvl = levelOf.get(s.id!)!;
      if (!byLevel.has(lvl)) byLevel.set(lvl, []);
      byLevel.get(lvl)!.push(s);
    }

    const maxPerLevel = Math.max(...Array.from(byLevel.values()).map(arr => arr.length));
    const rowWidth = maxPerLevel * this.NODE_WIDTH + (maxPerLevel - 1) * this.H_SPACING;

    const nodes: GraphNode[] = [];
    for (const [lvl, arr] of byLevel.entries()) {
      const count = arr.length;
      const levelWidth = count * this.NODE_WIDTH + (count - 1) * this.H_SPACING;
      const startX = (rowWidth - levelWidth) / 2;
      arr.forEach((s, i) => {
        nodes.push({
          id: s.id!,
          name: s.name,
          displayName: this.truncate(s.name),
          x: startX + i * (this.NODE_WIDTH + this.H_SPACING),
          y: lvl * (this.NODE_HEIGHT + this.V_SPACING)
        });
      });
    }

    const nodeMap = new Map(nodes.map(n => [n.id, n]));
    const edges: GraphEdge[] = [];
    for (const scene of this.scenes) {
      const from = nodeMap.get(scene.id!);
      if (!from || !scene.branches) continue;
      // On positionne chaque label a une fraction t differente de l'arete selon
      // son index parmi les sorties du meme noeud source. Evite le chevauchement
      // des labels au milieu quand plusieurs aretes convergent/divergent.
      const siblings = scene.branches.filter(b => nodeMap.has(b.targetSceneId));
      const count = siblings.length;
      siblings.forEach((b, idx) => {
        const to = nodeMap.get(b.targetSceneId)!;
        const x1 = from.x + this.NODE_WIDTH / 2;
        const y1 = from.y + this.NODE_HEIGHT;
        const x2 = to.x + this.NODE_WIDTH / 2;
        const y2 = to.y;
        // t ∈ [0.25, 0.55] : labels plutot pres de la source, echelonnes.
        const t = count === 1 ? 0.5 : 0.25 + (idx / (count - 1)) * 0.3;
        edges.push({
          label: b.label,
          x1, y1, x2, y2,
          labelX: x1 + (x2 - x1) * t,
          labelY: y1 + (y2 - y1) * t - 4
        });
      });
    }

    this.nodes = nodes;
    this.edges = edges;
    this.svgWidth = Math.max(rowWidth + 40, 600);
    this.svgHeight = (orphanLevel + 1) * (this.NODE_HEIGHT + this.V_SPACING) + 40;
  }

  private truncate(text: string): string {
    return text.length > this.MAX_LABEL_CHARS
      ? text.slice(0, this.MAX_LABEL_CHARS - 1) + '…'
      : text;
  }

  openScene(sceneId: string): void {
    this.router.navigate(['/campaigns', this.campaignId, 'arcs', this.arcId, 'chapters', this.chapterId, 'scenes', sceneId]);
  }

  back(): void {
    this.router.navigate(['/campaigns', this.campaignId, 'arcs', this.arcId, 'chapters', this.chapterId]);
  }

  ngOnDestroy(): void {
    this.layoutService.hide();
  }
}
