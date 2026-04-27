import { Component, OnInit, OnDestroy, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { forkJoin } from 'rxjs';
import { LucideAngularModule, ArrowLeft } from 'lucide-angular';
import { CampaignService } from '../../../services/campaign.service';
import { CharacterService } from '../../../services/character.service';
import { NpcService } from '../../../services/npc.service';
import { LayoutService, GlobalItem } from '../../../services/layout.service';
import { PageTitleService } from '../../../services/page-title.service';
import { Campaign, Chapter, Scene } from '../../../services/campaign.model';
import { loadCampaignTreeData, buildCampaignTree } from '../../campaign-tree.helper';

interface GraphNode { id: string; name: string; displayName: string; x: number; y: number; }
interface GraphEdge { key: string; label: string; x1: number; y1: number; x2: number; y2: number; labelX: number; labelY: number; }

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

  @ViewChild('svgEl') svgEl?: ElementRef<SVGSVGElement>;

  // Etat de drag : id du noeud manipule, offset entre le pointeur et le coin
  // haut-gauche du noeud (en coords SVG), et flag indiquant qu'un mouvement
  // significatif a eu lieu (pour distinguer clic vs glisser).
  draggingId: string | null = null;
  draggingLabelKey: string | null = null;
  private dragOffsetX = 0;
  private dragOffsetY = 0;
  private dragMoved = false;
  private readonly DRAG_THRESHOLD = 4;

  // Decalage manuel applique a chaque label d'arete, indexe par cle stable
  // (sourceId|targetId|branchIdx). Persiste a travers les recalculs d'aretes
  // pour que le label suive son arete quand on deplace un noeud, tout en
  // conservant le repositionnement manuel de l'utilisateur.
  private labelOffsets = new Map<string, { dx: number; dy: number }>();

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private campaignService: CampaignService,
    private characterService: CharacterService,
    private npcService: NpcService,
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
      treeData: loadCampaignTreeData(this.campaignService, this.campaignId, this.characterService, this.npcService)
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

    this.nodes = nodes;
    this.recomputeEdges();
    this.svgWidth = Math.max(rowWidth + 40, 600);
    this.svgHeight = (orphanLevel + 1) * (this.NODE_HEIGHT + this.V_SPACING) + 40;
  }

  /**
   * Recalcule la geometrie des aretes a partir des positions courantes des noeuds.
   * Appele apres le layout initial et apres chaque deplacement manuel d'un noeud.
   */
  private recomputeEdges(): void {
    const nodeMap = new Map(this.nodes.map(n => [n.id, n]));
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
        const key = `${scene.id}|${b.targetSceneId}|${idx}`;
        const offset = this.labelOffsets.get(key) ?? { dx: 0, dy: 0 };
        edges.push({
          key,
          label: b.label,
          x1, y1, x2, y2,
          labelX: x1 + (x2 - x1) * t + offset.dx,
          labelY: y1 + (y2 - y1) * t - 4 + offset.dy
        });
      });
    }
    this.edges = edges;
  }

  /**
   * Convertit des coordonnees ecran (PointerEvent) en coordonnees SVG via la CTM
   * inverse. Necessaire car le SVG peut etre redimensionne par max-width.
   */
  private toSvgCoords(evt: PointerEvent): { x: number; y: number } {
    const svg = this.svgEl?.nativeElement;
    if (!svg) return { x: evt.clientX, y: evt.clientY };
    const pt = svg.createSVGPoint();
    pt.x = evt.clientX;
    pt.y = evt.clientY;
    const ctm = svg.getScreenCTM();
    if (!ctm) return { x: evt.clientX, y: evt.clientY };
    const local = pt.matrixTransform(ctm.inverse());
    return { x: local.x, y: local.y };
  }

  onPointerDown(evt: PointerEvent, node: GraphNode): void {
    // Bouton gauche uniquement.
    if (evt.button !== 0) return;
    evt.preventDefault();
    const { x, y } = this.toSvgCoords(evt);
    this.draggingId = node.id;
    this.dragOffsetX = x - node.x;
    this.dragOffsetY = y - node.y;
    this.dragMoved = false;
    (evt.target as Element).setPointerCapture?.(evt.pointerId);
  }

  onPointerMove(evt: PointerEvent): void {
    const { x, y } = this.toSvgCoords(evt);

    if (this.draggingLabelKey) {
      const edge = this.edges.find(e => e.key === this.draggingLabelKey);
      if (!edge) return;
      const newX = x - this.dragOffsetX;
      const newY = y - this.dragOffsetY;
      if (!this.dragMoved && Math.hypot(newX - edge.labelX, newY - edge.labelY) < this.DRAG_THRESHOLD) return;
      this.dragMoved = true;
      // Recalcule la position automatique courante puis stocke la difference,
      // pour que l'offset reste valable meme apres deplacement d'un noeud.
      const auto = this.autoLabelPosition(edge.key);
      if (auto) {
        this.labelOffsets.set(edge.key, { dx: newX - auto.x, dy: newY - auto.y });
      }
      edge.labelX = newX;
      edge.labelY = newY;
      return;
    }

    if (!this.draggingId) return;
    const node = this.nodes.find(n => n.id === this.draggingId);
    if (!node) return;
    // Empeche le noeud de partir en coordonnees negatives : sinon il sort
    // du viewport SVG et se fait clipper par le navigateur (le SVG a
    // overflow: hidden par defaut quand on lui donne width/height explicites).
    const newX = Math.max(0, x - this.dragOffsetX);
    const newY = Math.max(0, y - this.dragOffsetY);
    if (!this.dragMoved) {
      const dx = newX - node.x;
      const dy = newY - node.y;
      if (Math.hypot(dx, dy) >= this.DRAG_THRESHOLD) this.dragMoved = true;
      else return;
    }
    node.x = newX;
    node.y = newY;
    this.recomputeEdges();
    this.fitSvgToNodes();
  }

  /**
   * Recalcule la position "auto" (sans offset manuel) du label d'une arete
   * a partir de sa cle. Utilise pour deriver le delta a stocker pendant le drag.
   */
  private autoLabelPosition(key: string): { x: number; y: number } | null {
    const [sourceId, targetId, idxStr] = key.split('|');
    const idx = Number(idxStr);
    const scene = this.scenes.find(s => s.id === sourceId);
    if (!scene?.branches) return null;
    const siblings = scene.branches.filter(b => this.nodes.some(n => n.id === b.targetSceneId));
    const count = siblings.length;
    if (idx >= count) return null;
    const from = this.nodes.find(n => n.id === sourceId);
    const to = this.nodes.find(n => n.id === targetId);
    if (!from || !to) return null;
    const x1 = from.x + this.NODE_WIDTH / 2;
    const y1 = from.y + this.NODE_HEIGHT;
    const x2 = to.x + this.NODE_WIDTH / 2;
    const y2 = to.y;
    const t = count === 1 ? 0.5 : 0.25 + (idx / (count - 1)) * 0.3;
    return { x: x1 + (x2 - x1) * t, y: y1 + (y2 - y1) * t - 4 };
  }

  onLabelPointerDown(evt: PointerEvent, edge: GraphEdge): void {
    if (evt.button !== 0) return;
    // Empeche l'event de remonter au <g class="node"> ou au svg, sinon on
    // declencherait aussi un drag de noeud.
    evt.stopPropagation();
    evt.preventDefault();
    const { x, y } = this.toSvgCoords(evt);
    this.draggingLabelKey = edge.key;
    this.dragOffsetX = x - edge.labelX;
    this.dragOffsetY = y - edge.labelY;
    this.dragMoved = false;
    (evt.target as Element).setPointerCapture?.(evt.pointerId);
  }

  /**
   * Agrandit le SVG si un noeud s'approche du bord droit ou bas, pour eviter
   * que le contenu deplace soit rogne. On ne reduit jamais en-dessous de la
   * taille initiale du layout pour rester stable visuellement.
   */
  private fitSvgToNodes(): void {
    const margin = 40;
    let maxX = 600;
    let maxY = 200;
    for (const n of this.nodes) {
      if (n.x + this.NODE_WIDTH + margin > maxX) maxX = n.x + this.NODE_WIDTH + margin;
      if (n.y + this.NODE_HEIGHT + margin > maxY) maxY = n.y + this.NODE_HEIGHT + margin;
    }
    if (maxX > this.svgWidth) this.svgWidth = maxX;
    if (maxY > this.svgHeight) this.svgHeight = maxY;
  }

  onPointerUp(evt: PointerEvent): void {
    if (this.draggingLabelKey) {
      this.draggingLabelKey = null;
      this.dragMoved = false;
      (evt.target as Element).releasePointerCapture?.(evt.pointerId);
      return;
    }
    if (!this.draggingId) return;
    const id = this.draggingId;
    const moved = this.dragMoved;
    this.draggingId = null;
    this.dragMoved = false;
    (evt.target as Element).releasePointerCapture?.(evt.pointerId);
    // Si le pointeur n'a pas reellement bouge, on traite comme un clic d'ouverture.
    if (!moved) this.openScene(id);
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
