import { Component, OnInit, OnDestroy, ElementRef, ViewChild, HostListener } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { forkJoin } from 'rxjs';
import { LucideAngularModule, ArrowLeft, Plus, ZoomIn, ZoomOut, Maximize, Trash2, Check, X } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { CampaignService } from '../../../services/campaign.service';
import { NpcService } from '../../../services/npc.service';
import { RandomTableService } from '../../../services/random-table.service';
import { EnemyService } from '../../../services/enemy.service';
import { LayoutService } from '../../../services/layout.service';
import { PageTitleService } from '../../../services/page-title.service';
import { Chapter, Scene, SceneType, LinkType, SceneBranch, SceneCreate } from '../../../services/campaign.model';
import { loadCampaignTreeData, buildCampaignSidebarConfig } from '../../campaign-tree.helper';

interface GraphNode { id: string; name: string; displayName: string; type: SceneType; x: number; y: number; }
interface GraphEdge { key: string; label: string; kind: LinkType; x1: number; y1: number; x2: number; y2: number; labelX: number; labelY: number; }

/**
 * Vue graphique d'un chapitre : organigramme des scènes et branches narratives.
 * Layout custom (BFS par niveaux) en SVG — évite une dépendance lourde type ngx-graph.
 * Caméra pan/zoom via viewBox ; les liens sont éditables/supprimables au clic.
 */
@Component({
    selector: 'app-chapter-graph',
    imports: [RouterModule, FormsModule, LucideAngularModule, TranslatePipe],
    templateUrl: './chapter-graph.component.html',
    styleUrls: ['./chapter-graph.component.scss']
})
export class ChapterGraphComponent implements OnInit, OnDestroy {
  readonly ArrowLeft = ArrowLeft;
  readonly Plus = Plus;
  readonly ZoomIn = ZoomIn;
  readonly ZoomOut = ZoomOut;
  readonly Maximize = Maximize;
  readonly Trash2 = Trash2;
  readonly Check = Check;
  readonly X = X;

  campaignId = '';
  arcId = '';
  chapterId = '';
  chapter: Chapter | null = null;
  scenes: Scene[] = [];
  /** Mode plat (1 arc, 1 chapitre) : la notion de chapitre est masquée dans cette vue. */
  isFlatMode = false;

  nodes: GraphNode[] = [];
  edges: GraphEdge[] = [];

  readonly NODE_WIDTH = 220;
  readonly NODE_HEIGHT = 64;
  readonly H_SPACING = 50;
  readonly V_SPACING = 90;
  readonly MAX_LABEL_CHARS = 26;

  // ─────────────── Caméra (viewBox) : pan + zoom ───────────────
  viewX = 0;
  viewY = 0;
  viewW = 600;
  viewH = 400;
  private readonly MIN_VIEW_W = 240;   // zoom max (avant)
  private readonly MAX_VIEW_W = 9000;  // zoom max (arrière)
  private hasFitted = false;
  private panning = false;
  private panStartX = 0;
  private panStartY = 0;
  private panStartViewX = 0;
  private panStartViewY = 0;

  // ─────────────── Édition d'un lien sélectionné ───────────────
  selectedEdgeKey: string | null = null;
  editEdgeLabel = '';
  editEdgeCondition = '';
  editEdgeKind: LinkType = 'EXIT';
  readonly linkKindOptions: LinkType[] = ['EXIT', 'CLUE', 'LEAD'];

  // ─────────────── Renommage inline d'une scène ───────────────
  editingNodeId: string | null = null;
  editNodeName = '';
  // Boîte de l'input (px, relatifs au conteneur) calculée une fois à l'ouverture.
  renameBox = { left: 0, top: 0, width: 0, height: 0 };

  @ViewChild('svgEl') svgEl?: ElementRef<SVGSVGElement>;

  // Etat de drag : id du noeud manipule, offset entre le pointeur et le coin
  // haut-gauche du noeud (en coords SVG), et flag indiquant qu'un mouvement
  // significatif a eu lieu (pour distinguer clic vs glisser).
  draggingId: string | null = null;
  draggingLabelKey: string | null = null;
  // Création de lien par glisser (2c) : nœud source + position courante du pointeur.
  linkingFromId: string | null = null;
  linkPointerX = 0;
  linkPointerY = 0;
  private dragOffsetX = 0;
  private dragOffsetY = 0;
  private dragMoved = false;
  private readonly DRAG_THRESHOLD = 4;

  // Decalage manuel applique a chaque label d'arete, indexe par cle stable
  // (sourceId|targetId|branchIdx). Persiste a travers les recalculs d'aretes.
  private labelOffsets = new Map<string, { dx: number; dy: number }>();

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private campaignService: CampaignService,
    private npcService: NpcService,
    private randomTableService: RandomTableService,
    private enemyService: EnemyService,
    private layoutService: LayoutService,
    private pageTitleService: PageTitleService,
    private translate: TranslateService
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
      treeData: loadCampaignTreeData(this.campaignService, this.campaignId, this.npcService, this.randomTableService, this.enemyService)
    }).subscribe(({ campaign, allCampaigns, chapter, scenes, treeData }) => {
      this.chapter = chapter;
      this.scenes = scenes;
      // Mode plat = campagne simple (1 arc, 1 chapitre) SANS compter la plomberie
      // (arc SYSTEM des quêtes libres et ses conteneurs).
      const narrativeArcs = treeData.arcs.filter(a => a.type !== 'SYSTEM');
      const narrativeArcIds = new Set(narrativeArcs.map(a => a.id));
      const allChapters = Object.entries(treeData.chaptersByArc)
        .filter(([arcId]) => narrativeArcIds.has(arcId))
        .flatMap(([, chs]) => chs);
      this.isFlatMode = narrativeArcs.length === 1 && allChapters.length === 1;
      this.pageTitleService.set(this.isFlatMode
        ? this.translate.instant('chapterGraph.titleFlat')
        : this.translate.instant('chapterGraph.title', { name: chapter.name }));
      this.buildGraph();

      // Recadrage automatique UNIQUEMENT au premier chargement : on préserve
      // ensuite la caméra (pan/zoom) de l'utilisateur à travers les éditions.
      if (!this.hasFitted && this.nodes.length > 0) {
        this.fitView();                          // provisoire (aspect de repli si le SVG n'est pas encore rendu)
        setTimeout(() => this.fitView(), 0);     // précis une fois le SVG dans le DOM
        this.hasFitted = true;
      }

      // Sidebar standard de campagne (titre cliquable + home, « + Nouvel arc », DnD).
      this.layoutService.show(buildCampaignSidebarConfig(campaign, allCampaigns, treeData, this.campaignId, this.translate));
    });
  }

  /**
   * Layout en niveaux par BFS depuis la scène d'entrée (order le plus bas).
   * Scènes non atteignables rassemblées dans un niveau "orphelin" tout en bas.
   */
  private buildGraph(): void {
    if (this.scenes.length === 0) {
      this.nodes = []; this.edges = [];
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
        const autoX = startX + i * (this.NODE_WIDTH + this.H_SPACING);
        const autoY = lvl * (this.NODE_HEIGHT + this.V_SPACING);
        nodes.push({
          id: s.id!,
          name: s.name,
          displayName: this.truncate(s.name),
          type: s.type ?? 'GENERIC',
          // Position sauvegardée (Niveau 2) si présente, sinon layout auto (BFS).
          x: s.graphX != null ? s.graphX : autoX,
          y: s.graphY != null ? s.graphY : autoY
        });
      });
    }

    this.nodes = nodes;
    this.recomputeEdges();
  }

  /** Recalcule la geometrie des aretes a partir des positions courantes des noeuds. */
  private recomputeEdges(): void {
    const nodeMap = new Map(this.nodes.map(n => [n.id, n]));
    const edges: GraphEdge[] = [];
    for (const scene of this.scenes) {
      const from = nodeMap.get(scene.id!);
      if (!from || !scene.branches) continue;
      const siblings = scene.branches.filter(b => nodeMap.has(b.targetSceneId));
      const count = siblings.length;
      siblings.forEach((b, idx) => {
        const to = nodeMap.get(b.targetSceneId)!;
        const x1 = from.x + this.NODE_WIDTH / 2;
        const y1 = from.y + this.NODE_HEIGHT;
        const x2 = to.x + this.NODE_WIDTH / 2;
        const y2 = to.y;
        const t = count === 1 ? 0.5 : 0.25 + (idx / (count - 1)) * 0.3;
        const key = `${scene.id}|${b.targetSceneId}|${idx}`;
        const offset = this.labelOffsets.get(key) ?? { dx: 0, dy: 0 };
        edges.push({
          key,
          label: b.label,
          kind: b.kind ?? 'EXIT',
          x1, y1, x2, y2,
          labelX: x1 + (x2 - x1) * t + offset.dx,
          labelY: y1 + (y2 - y1) * t - 4 + offset.dy
        });
      });
    }
    this.edges = edges;
  }

  /** Convertit des coordonnees ecran (PointerEvent) en coordonnees SVG via la CTM inverse. */
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

  // ─────────────── Caméra : viewBox, pan, zoom ───────────────

  get viewBox(): string {
    return `${this.viewX} ${this.viewY} ${this.viewW} ${this.viewH}`;
  }

  /** Recadre la caméra sur l'ensemble des nœuds (aspect du conteneur → pas de letterbox). */
  fitView(): void {
    if (this.nodes.length === 0) {
      this.viewX = 0; this.viewY = 0; this.viewW = 600; this.viewH = 400;
      return;
    }
    let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
    for (const n of this.nodes) {
      minX = Math.min(minX, n.x);
      minY = Math.min(minY, n.y);
      maxX = Math.max(maxX, n.x + this.NODE_WIDTH);
      maxY = Math.max(maxY, n.y + this.NODE_HEIGHT);
    }
    const margin = 80;
    minX -= margin; minY -= margin; maxX += margin; maxY += margin;
    const w = maxX - minX, h = maxY - minY;
    const rect = this.svgEl?.nativeElement.getBoundingClientRect();
    const aspect = rect && rect.height > 0 ? rect.width / rect.height : 16 / 9;
    let vw = w, vh = h;
    if (w / h > aspect) { vh = w / aspect; } else { vw = h * aspect; }
    this.viewX = minX - (vw - w) / 2;
    this.viewY = minY - (vh - h) / 2;
    this.viewW = vw;
    this.viewH = vh;
  }

  /** Zoom autour du centre (boutons). factor < 1 = zoom avant. */
  zoomBy(factor: number): void {
    const cx = this.viewX + this.viewW / 2;
    const cy = this.viewY + this.viewH / 2;
    const newW = Math.max(this.MIN_VIEW_W, Math.min(this.MAX_VIEW_W, this.viewW * factor));
    const ratio = newW / this.viewW;
    this.viewW = newW;
    this.viewH = this.viewH * ratio;
    this.viewX = cx - this.viewW / 2;
    this.viewY = cy - this.viewH / 2;
  }

  /** Zoom à la molette, centré sur le curseur. */
  onWheel(evt: WheelEvent): void {
    evt.preventDefault();
    const svg = this.svgEl?.nativeElement;
    if (!svg) return;
    const rect = svg.getBoundingClientRect();
    if (rect.width === 0 || rect.height === 0) return;
    const mx = (evt.clientX - rect.left) / rect.width;
    const my = (evt.clientY - rect.top) / rect.height;
    const svgX = this.viewX + mx * this.viewW;
    const svgY = this.viewY + my * this.viewH;
    const factor = evt.deltaY > 0 ? 1.12 : 1 / 1.12;
    const newW = Math.max(this.MIN_VIEW_W, Math.min(this.MAX_VIEW_W, this.viewW * factor));
    const ratio = newW / this.viewW;
    this.viewW = newW;
    this.viewH = this.viewH * ratio;
    this.viewX = svgX - mx * this.viewW;
    this.viewY = svgY - my * this.viewH;
  }

  /**
   * Réajuste la hauteur du viewBox à l'aspect du conteneur après un redimensionnement,
   * en préservant le centre et l'échelle horizontale — évite le letterbox et le décalage
   * du zoom-molette quand la fenêtre change de taille (la caméra reste où elle est).
   */
  private applyAspect(): void {
    const rect = this.svgEl?.nativeElement.getBoundingClientRect();
    if (!rect || rect.width === 0 || rect.height === 0) return;
    const cy = this.viewY + this.viewH / 2;
    const newH = this.viewW / (rect.width / rect.height);
    this.viewH = newH;
    this.viewY = cy - newH / 2;
  }

  @HostListener('window:resize')
  onWindowResize(): void {
    this.applyAspect();
  }

  /** Pointerdown sur le FOND du SVG : désélectionne un lien et démarre le pan. */
  onSvgPointerDown(evt: PointerEvent): void {
    if (evt.button !== 0) return;
    evt.preventDefault();
    this.selectedEdgeKey = null; // clic dans le vide → ferme l'éditeur de lien
    if (this.linkingFromId || this.draggingId || this.draggingLabelKey) return;
    this.panning = true;
    this.panStartX = evt.clientX;
    this.panStartY = evt.clientY;
    this.panStartViewX = this.viewX;
    this.panStartViewY = this.viewY;
    (evt.currentTarget as Element).setPointerCapture?.(evt.pointerId);
  }

  onPointerMove(evt: PointerEvent): void {
    // Pan (fond) : delta écran → unités viewBox selon l'échelle courante.
    if (this.panning) {
      const rect = this.svgEl?.nativeElement.getBoundingClientRect();
      if (rect && rect.width > 0 && rect.height > 0) {
        this.viewX = this.panStartViewX - (evt.clientX - this.panStartX) * (this.viewW / rect.width);
        this.viewY = this.panStartViewY - (evt.clientY - this.panStartY) * (this.viewH / rect.height);
      }
      return;
    }

    const { x, y } = this.toSvgCoords(evt);

    if (this.linkingFromId) {
      this.linkPointerX = x;
      this.linkPointerY = y;
      return;
    }

    if (this.draggingLabelKey) {
      const edge = this.edges.find(e => e.key === this.draggingLabelKey);
      if (!edge) return;
      const newX = x - this.dragOffsetX;
      const newY = y - this.dragOffsetY;
      if (!this.dragMoved && Math.hypot(newX - edge.labelX, newY - edge.labelY) < this.DRAG_THRESHOLD) return;
      this.dragMoved = true;
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
    // Avec une caméra viewBox, plus besoin de brider les coordonnées à 0 :
    // le pan/zoom permet d'atteindre n'importe quelle zone.
    const newX = x - this.dragOffsetX;
    const newY = y - this.dragOffsetY;
    if (!this.dragMoved) {
      const dx = newX - node.x;
      const dy = newY - node.y;
      if (Math.hypot(dx, dy) >= this.DRAG_THRESHOLD) this.dragMoved = true;
      else return;
    }
    node.x = newX;
    node.y = newY;
    this.recomputeEdges();
  }

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

  onPointerDown(evt: PointerEvent, node: GraphNode): void {
    if (evt.button !== 0) return;
    evt.stopPropagation();  // évite de démarrer un pan du fond
    evt.preventDefault();
    const { x, y } = this.toSvgCoords(evt);
    this.draggingId = node.id;
    this.dragOffsetX = x - node.x;
    this.dragOffsetY = y - node.y;
    this.dragMoved = false;
    (evt.target as Element).setPointerCapture?.(evt.pointerId);
  }

  onLabelPointerDown(evt: PointerEvent, edge: GraphEdge): void {
    if (evt.button !== 0) return;
    evt.stopPropagation();
    evt.preventDefault();
    const { x, y } = this.toSvgCoords(evt);
    this.draggingLabelKey = edge.key;
    this.dragOffsetX = x - edge.labelX;
    this.dragOffsetY = y - edge.labelY;
    this.dragMoved = false;
    (evt.target as Element).setPointerCapture?.(evt.pointerId);
  }

  onPointerUp(evt: PointerEvent): void {
    if (this.panning) {
      this.panning = false;
      // Le pan capture le pointeur sur le SVG (currentTarget), pas sur une cible enfant.
      (evt.currentTarget as Element).releasePointerCapture?.(evt.pointerId);
      return;
    }
    if (this.linkingFromId) {
      const { x, y } = this.toSvgCoords(evt);
      const target = this.nodeAt(x, y);
      const fromId = this.linkingFromId;
      this.linkingFromId = null;
      (evt.target as Element).releasePointerCapture?.(evt.pointerId);
      if (target && target.id !== fromId) this.createLink(fromId, target.id);
      return;
    }
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
    // Pointeur immobile → clic d'ouverture ; sinon on persiste la position (Niveau 2).
    if (!moved) { this.openScene(id); return; }
    this.persistNodePosition(id);
  }

  private persistNodePosition(nodeId: string): void {
    const node = this.nodes.find(n => n.id === nodeId);
    const scene = this.scenes.find(s => s.id === nodeId);
    if (!node || !scene) return;
    scene.graphX = node.x;
    scene.graphY = node.y;
    this.campaignService.updateScene(nodeId, { ...scene, order: scene.order ?? 0 })
      .subscribe({ error: () => {} });
  }

  private truncate(text: string): string {
    return text.length > this.MAX_LABEL_CHARS
      ? text.slice(0, this.MAX_LABEL_CHARS - 1) + '…'
      : text;
  }

  /** Couleur d'une arête selon son type de lien (Niveau 2). */
  edgeColor(kind: LinkType): string {
    switch (kind) {
      case 'CLUE': return '#f0b429'; // indice — ambre
      case 'LEAD': return '#34d399'; // piste — vert
      default:     return '#b8c0cc'; // sortie — gris neutre (historique)
    }
  }

  openScene(sceneId: string): void {
    this.router.navigate(['/campaigns', this.campaignId, 'arcs', this.arcId, 'chapters', this.chapterId, 'scenes', sceneId]);
  }

  back(): void {
    if (this.isFlatMode) {
      this.router.navigate(['/campaigns', this.campaignId]);
      return;
    }
    this.router.navigate(['/campaigns', this.campaignId, 'arcs', this.arcId, 'chapters', this.chapterId]);
  }

  // ─────────────── Édition dans le graphe (Niveau 2) ───────────────

  /** Crée une nouvelle scène dans ce chapitre et recharge la carte. */
  addScene(): void {
    const payload: SceneCreate = {
      name: this.translate.instant('chapterGraph.newSceneName'),
      chapterId: this.chapterId,
      order: this.scenes.length
    };
    this.campaignService.createScene(payload).subscribe({
      next: () => this.load(),
      error: () => {}
    });
  }

  /**
   * Démarre le renommage inline d'une scène : positionne un champ HTML par-dessus le
   * nœud (conversion coords SVG → écran, figée le temps de l'édition) et le focus.
   */
  startRenameNode(evt: PointerEvent, node: GraphNode): void {
    if (evt.button !== 0) return;
    evt.stopPropagation();
    evt.preventDefault();
    const svg = this.svgEl?.nativeElement;
    const container = svg?.parentElement;
    const ctm = svg?.getScreenCTM();
    if (svg && container && ctm) {
      const p1 = svg.createSVGPoint(); p1.x = node.x; p1.y = node.y;
      const p2 = svg.createSVGPoint(); p2.x = node.x + this.NODE_WIDTH; p2.y = node.y + this.NODE_HEIGHT;
      const s1 = p1.matrixTransform(ctm);
      const s2 = p2.matrixTransform(ctm);
      const crect = container.getBoundingClientRect();
      this.renameBox = { left: s1.x - crect.left, top: s1.y - crect.top, width: s2.x - s1.x, height: s2.y - s1.y };
    }
    this.editingNodeId = node.id;
    this.editNodeName = node.name;
    setTimeout(() => {
      const el = document.getElementById('node-rename-input') as HTMLInputElement | null;
      el?.focus();
      el?.select();
    }, 0);
  }

  /** Valide le renommage : met à jour l'affichage localement et persiste (name seul). */
  saveRename(): void {
    const id = this.editingNodeId;
    if (!id) return;
    this.editingNodeId = null;   // avant tout : évite un double-save via le (blur)
    const name = this.editNodeName.trim();
    const node = this.nodes.find(n => n.id === id);
    const scene = this.scenes.find(s => s.id === id);
    if (!name || !node || !scene || name === scene.name) return;
    node.name = name;
    node.displayName = this.truncate(name);
    scene.name = name;
    this.campaignService.updateScene(id, { ...scene, order: scene.order ?? 0 }).subscribe({ error: () => {} });
  }

  cancelRename(): void {
    this.editingNodeId = null;
  }

  /** Début d'un lien tiré depuis la pastille d'un nœud (sans déclencher son drag). */
  onConnectStart(evt: PointerEvent, node: GraphNode): void {
    if (evt.button !== 0) return;
    evt.stopPropagation();
    evt.preventDefault();
    const { x, y } = this.toSvgCoords(evt);
    this.linkingFromId = node.id;
    this.linkPointerX = x;
    this.linkPointerY = y;
    (evt.target as Element).setPointerCapture?.(evt.pointerId);
  }

  linkSourceX(): number {
    const n = this.nodes.find(x => x.id === this.linkingFromId);
    return n ? n.x + this.NODE_WIDTH / 2 : 0;
  }
  linkSourceY(): number {
    const n = this.nodes.find(x => x.id === this.linkingFromId);
    return n ? n.y + this.NODE_HEIGHT : 0;
  }

  /** Nœud dont la boîte contient le point (coords SVG), ou null. */
  private nodeAt(x: number, y: number): GraphNode | null {
    return this.nodes.find(n =>
      x >= n.x && x <= n.x + this.NODE_WIDTH &&
      y >= n.y && y <= n.y + this.NODE_HEIGHT) ?? null;
  }

  /** Crée une branche (SORTIE) de fromId vers toId si pas déjà liée, puis sauvegarde. */
  private createLink(fromId: string, toId: string): void {
    const scene = this.scenes.find(s => s.id === fromId);
    if (!scene) return;
    const existing = scene.branches ?? [];
    if (existing.some(b => b.targetSceneId === toId)) return;
    const next: SceneBranch[] = [...existing, { label: '', targetSceneId: toId, condition: '', kind: 'EXIT' }];
    this.campaignService.updateScene(fromId, { ...scene, order: scene.order ?? 0, branches: next })
      .subscribe({ next: () => this.load(), error: () => {} });
  }

  // ─────────────── Sélection / édition / suppression d'un lien ───────────────

  get selectedEdge(): GraphEdge | null {
    return this.edges.find(e => e.key === this.selectedEdgeKey) ?? null;
  }

  /** Noms des extrémités du lien sélectionné (pour l'en-tête de l'éditeur). */
  get selectedEdgeEndpoints(): { from: string; to: string } | null {
    if (!this.selectedEdgeKey) return null;
    const [sourceId, targetId] = this.selectedEdgeKey.split('|');
    const from = this.nodes.find(n => n.id === sourceId)?.name ?? '?';
    const to = this.nodes.find(n => n.id === targetId)?.name ?? '?';
    return { from, to };
  }

  /**
   * Localise la branche EXACTE désignée par une clé d'arête (`sourceId|targetId|idx`).
   * L'idx est la position parmi les branches dont la cible est un nœud affiché — ce qui
   * cible le bon lien même si deux branches pointent la même scène (data legacy).
   */
  private branchForKey(key: string): { scene: Scene; branchIndex: number } | null {
    const [sourceId, , idxStr] = key.split('|');
    const idx = Number(idxStr);
    const scene = this.scenes.find(s => s.id === sourceId);
    if (!scene?.branches) return null;
    const siblings = scene.branches.filter(b => this.nodes.some(n => n.id === b.targetSceneId));
    const branch = siblings[idx];
    if (!branch) return null;
    const branchIndex = scene.branches.indexOf(branch);
    return branchIndex >= 0 ? { scene, branchIndex } : null;
  }

  /** Sélectionne un lien au clic et pré-remplit l'éditeur depuis sa branche. */
  onEdgeSelect(evt: PointerEvent, edge: GraphEdge): void {
    if (evt.button !== 0) return;
    evt.stopPropagation();
    evt.preventDefault();
    this.selectedEdgeKey = edge.key;
    const found = this.branchForKey(edge.key);
    const branch = found ? found.scene.branches![found.branchIndex] : undefined;
    this.editEdgeLabel = branch?.label ?? '';
    this.editEdgeCondition = branch?.condition ?? '';
    this.editEdgeKind = branch?.kind ?? 'EXIT';
  }

  closeEdgeEditor(): void {
    this.selectedEdgeKey = null;
  }

  /** Enregistre le libellé / la condition / le type du lien sélectionné. */
  saveEdge(): void {
    if (!this.selectedEdgeKey) return;
    const found = this.branchForKey(this.selectedEdgeKey);
    if (!found) return;
    const { scene, branchIndex } = found;
    const branches = (scene.branches ?? []).map((b, i) =>
      i === branchIndex
        ? { ...b, label: this.editEdgeLabel.trim(), condition: this.editEdgeCondition.trim(), kind: this.editEdgeKind }
        : b);
    this.campaignService.updateScene(scene.id!, { ...scene, order: scene.order ?? 0, branches })
      .subscribe({ next: () => { this.selectedEdgeKey = null; this.load(); }, error: () => {} });
  }

  /** Supprime (sépare) le lien sélectionné. */
  deleteEdge(): void {
    if (!this.selectedEdgeKey) return;
    const found = this.branchForKey(this.selectedEdgeKey);
    if (!found) return;
    const { scene, branchIndex } = found;
    const branches = (scene.branches ?? []).filter((_, i) => i !== branchIndex);
    this.campaignService.updateScene(scene.id!, { ...scene, order: scene.order ?? 0, branches })
      .subscribe({ next: () => { this.selectedEdgeKey = null; this.load(); }, error: () => {} });
  }

  ngOnDestroy(): void {
    // Volontairement vide : la sidebar reste prise en charge par le composant suivant.
  }
}
