import { Component, OnInit, OnDestroy, ElementRef, ViewChild } from '@angular/core';

import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { map, switchMap } from 'rxjs/operators';
import { LucideAngularModule, ArrowLeft, Network, RotateCcw } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { CampaignService } from '../../services/campaign.service';
import { PageService } from '../../services/page.service';
import { LayoutService } from '../../services/layout.service';
import { PageTitleService } from '../../services/page-title.service';
import { Campaign, Scene } from '../../services/campaign.model';
import { Page } from '../../services/page.model';
import { loadCampaignTreeData, buildCampaignSidebarConfig, CampaignTreeData } from '../campaign-tree.helper';

/** Type d'un nœud du graphe (préfixe des ids, style et légende). */
type NodeKind = 'page' | 'npc' | 'scene' | 'quest';

/** Nœud du graphe : une page de Lore, ou une entité de campagne qui référence des pages. */
interface GraphNode {
  id: string;            // '<kind>:<id>' (évite les collisions d'IDs entre tables)
  kind: NodeKind;
  label: string;
  /** Libellé affiché sur 1 à 2 lignes (moins de troncature qu'une ligne unique). */
  lines: string[];
  route: string[];       // navigation au clic
  x: number;             // centre du nœud (coords SVG)
  y: number;
  degree: number;        // nombre de liens (taille du nœud)
}

interface GraphEdge {
  key: string;
  kind: NodeKind;        // page↔page ou entité→page (style distinct par type)
  a: string;             // ids des nœuds reliés — pour le focus au survol
  b: string;
  x1: number; y1: number; x2: number; y2: number;
}

/**
 * Graphe des liens d'une CAMPAGNE : ses PNJ, scènes et quêtes reliés aux pages
 * de son univers (Lore).
 *
 * Nœuds = toutes les pages du Lore associé + les entités de la campagne (PNJ,
 * scènes, quêtes) qui référencent au moins une page. Arêtes = `relatedPageIds`
 * des pages (liens page↔page) et des entités (liens entité→page).
 *
 * Vivait historiquement côté Lore ; déplacé côté campagne car il mélange des
 * entités de campagne (PNJ) avec les pages — c'est une vue de PRÉPARATION de
 * campagne, pas une vue d'univers.
 *
 * Layout force-directed (Fruchterman-Reingold simplifié) calculé une fois au
 * chargement, puis nœuds déplaçables à la souris — même approche SVG custom
 * que chapter-graph, sans dépendance externe.
 */
@Component({
    selector: 'app-campaign-graph',
    imports: [RouterModule, LucideAngularModule, TranslatePipe],
    templateUrl: './campaign-graph.component.html',
    styleUrls: ['./campaign-graph.component.scss']
})
export class CampaignGraphComponent implements OnInit, OnDestroy {
  readonly ArrowLeft = ArrowLeft;
  readonly Network = Network;
  readonly RotateCcw = RotateCcw;

  campaignId = '';
  campaign: Campaign | null = null;
  /** Faux tant que la campagne n'a pas d'univers (Lore) associé → état vide dédié. */
  hasLore = false;

  nodes: GraphNode[] = [];
  edges: GraphEdge[] = [];
  npcCount = 0;
  sceneCount = 0;
  questCount = 0;
  pageCount = 0;
  edgeCount = 0;

  readonly MAX_LABEL_CHARS = 22;
  private readonly MARGIN = 70;

  svgWidth = 800;
  svgHeight = 600;

  @ViewChild('svgEl') svgEl?: ElementRef<SVGSVGElement>;

  draggingId: string | null = null;
  private dragOffsetX = 0;
  private dragOffsetY = 0;
  private dragMoved = false;
  private readonly DRAG_THRESHOLD = 4;

  // Adjacence (ids de nœuds) — sert à recalculer les arêtes après un drag.
  private adjacency: { key: string; kind: NodeKind; a: string; b: string }[] = [];

  // Disposition personnalisée : positions sauvegardées sur la campagne, sauvegarde
  // différée après un drag (évite un PUT par pixel déplacé).
  private savedPositions: Record<string, { x: number; y: number }> | null = null;
  private saveTimer: ReturnType<typeof setTimeout> | null = null;
  private positionsDirty = false;

  // Filtres de la légende : types d'entités masqués (persistés en localStorage).
  private static readonly HIDDEN_KINDS_KEY = 'loremind.campaignGraph.hiddenKinds';
  hiddenKinds = new Set<NodeKind>();
  // Données brutes gardées pour reconstruire le graphe quand on (dé)masque un type.
  private cachedPages: Page[] = [];
  private cachedTree: CampaignTreeData | null = null;

  // Focus au survol : le nœud survolé + ses voisins restent nets, le reste s'estompe.
  hoveredId: string | null = null;
  private hoverNeighbors = new Set<string>();

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private campaignService: CampaignService,
    private pageService: PageService,
    private layoutService: LayoutService,
    private pageTitleService: PageTitleService,
    private translate: TranslateService
  ) {}

  ngOnInit(): void {
    this.campaignId = this.route.snapshot.paramMap.get('campaignId')!;
    try {
      const raw = localStorage.getItem(CampaignGraphComponent.HIDDEN_KINDS_KEY);
      if (raw) this.hiddenKinds = new Set(JSON.parse(raw) as NodeKind[]);
    } catch { /* préférence corrompue → tout visible */ }
    forkJoin({
      campaign: this.campaignService.getCampaignById(this.campaignId),
      allCampaigns: this.campaignService.getAllCampaigns(),
      treeData: loadCampaignTreeData(this.campaignService, this.campaignId)
    }).pipe(
      switchMap(({ campaign, allCampaigns, treeData }) => {
        this.campaign = campaign;
        this.hasLore = !!campaign.loreId;
        this.savedPositions = this.parsePositions(campaign.graphPositions);
        this.layoutService.show(buildCampaignSidebarConfig(campaign, allCampaigns, treeData, this.campaignId, this.translate));
        this.pageTitleService.set(this.translate.instant('campaignGraph.title', { name: campaign.name }));
        // Les PNJ arrivent déjà avec l'arbre (une seule requête /tree) ; seules
        // les pages du Lore associé restent à charger.
        const pages$ = campaign.loreId ? this.pageService.getByLoreId(campaign.loreId) : of([] as Page[]);
        return pages$.pipe(map(pages => ({ pages, treeData })));
      })
    ).subscribe(({ pages, treeData }) => {
      this.cachedPages = pages;
      this.cachedTree = treeData;
      this.buildGraph(pages, treeData);
    });
  }

  // --- Filtres de la légende --------------------------------------------------

  isHidden(kind: NodeKind): boolean {
    return this.hiddenKinds.has(kind);
  }

  /** (Dé)masque un type d'entité et reconstruit le graphe (positions sauvées conservées). */
  toggleKind(kind: NodeKind): void {
    if (this.hiddenKinds.has(kind)) this.hiddenKinds.delete(kind);
    else this.hiddenKinds.add(kind);
    try {
      localStorage.setItem(CampaignGraphComponent.HIDDEN_KINDS_KEY, JSON.stringify([...this.hiddenKinds]));
    } catch { /* stockage indisponible : préférence non persistée */ }
    this.hoveredId = null;
    this.hoverNeighbors.clear();
    if (this.cachedTree) this.buildGraph(this.cachedPages, this.cachedTree);
  }

  // --- Focus au survol ----------------------------------------------------------

  onNodeEnter(node: GraphNode): void {
    if (this.draggingId) return; // pas de changement de focus en plein drag
    this.hoveredId = node.id;
    this.hoverNeighbors = new Set(
      this.adjacency.flatMap(e => {
        if (e.a === node.id) return [e.b];
        return e.b === node.id ? [e.a] : [];
      }));
  }

  onNodeLeave(): void {
    this.hoveredId = null;
    this.hoverNeighbors.clear();
  }

  nodeDimmed(node: GraphNode): boolean {
    return this.hoveredId !== null && node.id !== this.hoveredId && !this.hoverNeighbors.has(node.id);
  }

  edgeDimmed(edge: GraphEdge): boolean {
    return this.hoveredId !== null && edge.a !== this.hoveredId && edge.b !== this.hoveredId;
  }

  // --- Construction du graphe ----------------------------------------------

  private buildGraph(pages: Page[], treeData: CampaignTreeData): void {
    const loreId = this.campaign?.loreId ?? '';
    const pageIds = new Set(pages.map(p => p.id!));
    this.pageCount = pages.length;

    // Nœuds pages (toutes, même isolées : la vue d'ensemble inclut les orphelines).
    const nodes: GraphNode[] = pages.map(p => ({
      id: `page:${p.id}`,
      kind: 'page' as const,
      label: p.title,
      lines: this.splitLabel(p.title),
      route: ['/lore', loreId, 'pages', p.id!],
      x: 0, y: 0, degree: 0
    }));

    const linksTo = (ids?: string[]) => (ids ?? []).some(pid => pageIds.has(pid));

    // Entités de campagne : seulement celles qui référencent au moins une page du
    // Lore (une entité sans lien n'apporte rien à la carte), et dont le TYPE n'est
    // pas masqué par la légende.
    const linkedNpcs = this.hiddenKinds.has('npc') ? [] : treeData.npcs.filter(n => linksTo(n.relatedPageIds));
    for (const n of linkedNpcs) {
      nodes.push({
        id: `npc:${n.id}`,
        kind: 'npc',
        label: n.name,
        lines: this.splitLabel(n.name),
        route: ['/campaigns', this.campaignId, 'npcs', n.id!],
        x: 0, y: 0, degree: 0
      });
    }
    this.npcCount = linkedNpcs.length;

    // Scènes liées : la route de détail exige arc + chapitre → index inverse depuis l'arbre.
    const arcOfChapter = new Map<string, string>();
    for (const [arcId, chapters] of Object.entries(treeData.chaptersByArc)) {
      for (const ch of chapters) arcOfChapter.set(ch.id!, arcId);
    }
    const linkedScenes: { scene: Scene; chapterId: string }[] = [];
    if (!this.hiddenKinds.has('scene')) {
      for (const [chapterId, scenes] of Object.entries(treeData.scenesByChapter)) {
        if (!arcOfChapter.has(chapterId)) continue;
        for (const sc of scenes) {
          if (linksTo(sc.relatedPageIds)) linkedScenes.push({ scene: sc, chapterId });
        }
      }
    }
    for (const { scene, chapterId } of linkedScenes) {
      nodes.push({
        id: `scene:${scene.id}`,
        kind: 'scene',
        label: scene.name,
        lines: this.splitLabel(scene.name),
        route: ['/campaigns', this.campaignId, 'arcs', arcOfChapter.get(chapterId)!,
          'chapters', chapterId, 'scenes', scene.id!],
        x: 0, y: 0, degree: 0
      });
    }
    this.sceneCount = linkedScenes.length;

    const linkedQuests = this.hiddenKinds.has('quest')
      ? [] : (treeData.quests ?? []).filter(q => linksTo(q.relatedPageIds));
    for (const q of linkedQuests) {
      nodes.push({
        id: `quest:${q.id}`,
        kind: 'quest',
        label: q.name,
        lines: this.splitLabel(q.name),
        route: ['/campaigns', this.campaignId, 'quests', q.id!],
        x: 0, y: 0, degree: 0
      });
    }
    this.questCount = linkedQuests.length;

    // Arêtes. Les liens page↔page sont dé-dupliqués par paire non-orientée
    // (A→B et B→A = un seul trait) ; les liens entité→page portent le type de l'entité.
    const adjacency: { key: string; kind: NodeKind; a: string; b: string }[] = [];
    const seenPairs = new Set<string>();
    for (const p of pages) {
      for (const targetId of p.relatedPageIds ?? []) {
        if (!pageIds.has(targetId) || targetId === p.id) continue;
        const pair = [p.id!, targetId].sort().join('|');
        if (seenPairs.has(pair)) continue;
        seenPairs.add(pair);
        adjacency.push({ key: `pp:${pair}`, kind: 'page', a: `page:${p.id}`, b: `page:${targetId}` });
      }
    }
    const entityEdges = (kind: NodeKind, id: string, relatedPageIds?: string[]) => {
      for (const targetId of new Set(relatedPageIds ?? [])) {
        if (!pageIds.has(targetId)) continue;
        adjacency.push({ key: `${kind}:${id}|${targetId}`, kind, a: `${kind}:${id}`, b: `page:${targetId}` });
      }
    };
    for (const n of linkedNpcs) entityEdges('npc', n.id!, n.relatedPageIds);
    for (const { scene } of linkedScenes) entityEdges('scene', scene.id!, scene.relatedPageIds);
    for (const q of linkedQuests) entityEdges('quest', q.id!, q.relatedPageIds);
    this.adjacency = adjacency;
    this.edgeCount = adjacency.length;

    // Degré (pondère la taille des nœuds : une capitale très liée ressort).
    const degree = new Map<string, number>();
    for (const e of adjacency) {
      degree.set(e.a, (degree.get(e.a) ?? 0) + 1);
      degree.set(e.b, (degree.get(e.b) ?? 0) + 1);
    }
    for (const node of nodes) {
      node.degree = degree.get(node.id) ?? 0;
    }

    this.nodes = nodes;
    this.runForceLayout();
    this.applySavedPositions();
    this.recomputeEdges();
  }

  // --- Disposition personnalisée (persistée sur la campagne) -----------------

  private parsePositions(json: string | null | undefined): Record<string, { x: number; y: number }> | null {
    if (!json) return null;
    try {
      const parsed = JSON.parse(json);
      return parsed && typeof parsed === 'object' ? parsed : null;
    } catch {
      return null; // JSON corrompu → disposition auto, sans casser l'écran
    }
  }

  /** Réapplique les positions sauvegardées ; les nœuds inconnus gardent le layout auto. */
  private applySavedPositions(): void {
    if (!this.savedPositions) return;
    let applied = false;
    for (const node of this.nodes) {
      const p = this.savedPositions[node.id];
      if (!p || typeof p.x !== 'number' || typeof p.y !== 'number') continue;
      node.x = p.x;
      node.y = p.y;
      applied = true;
    }
    if (applied) this.fitSvgToNodes();
  }

  /** Sauvegarde différée (800 ms après le dernier drag) de la disposition courante. */
  private scheduleSave(): void {
    this.positionsDirty = true;
    if (this.saveTimer) clearTimeout(this.saveTimer);
    this.saveTimer = setTimeout(() => this.savePositions(), 800);
  }

  private savePositions(): void {
    if (!this.positionsDirty || !this.campaign?.id) return;
    this.positionsDirty = false;
    const positions: Record<string, { x: number; y: number }> = {};
    for (const n of this.nodes) positions[n.id] = { x: Math.round(n.x), y: Math.round(n.y) };
    this.savedPositions = positions;
    this.campaignService.updateGraphPositions(this.campaign.id, JSON.stringify(positions)).subscribe();
  }

  /** Recalcule la disposition automatique et oublie les déplacements manuels. */
  resetLayout(): void {
    if (this.saveTimer) clearTimeout(this.saveTimer);
    this.positionsDirty = false;
    this.savedPositions = null;
    this.runForceLayout();
    this.recomputeEdges();
    if (this.campaign?.id) {
      this.campaignService.updateGraphPositions(this.campaign.id, '').subscribe();
    }
  }

  /**
   * Layout force-directed (Fruchterman-Reingold simplifié) :
   * répulsion entre tous les nœuds, ressorts sur les arêtes, gravité vers le
   * centre (regroupe les composantes déconnectées), refroidissement progressif.
   * Positions initiales sur un cercle (déterministe : pas d'aléatoire, cf.
   * convention projet d'éviter Math.random pour des rendus reproductibles).
   */
  private runForceLayout(): void {
    const n = this.nodes.length;
    if (n === 0) {
      this.svgWidth = 800; this.svgHeight = 400;
      return;
    }
    const side = Math.max(600, Math.ceil(170 * Math.sqrt(n)));
    const w = side, h = side;
    const cx = w / 2, cy = h / 2;

    // Init en spirale : angle d'or → répartition uniforme et déterministe.
    const golden = Math.PI * (3 - Math.sqrt(5));
    this.nodes.forEach((node, i) => {
      const r = (Math.sqrt(i + 0.5) / Math.sqrt(n)) * (side / 2 - this.MARGIN);
      const a = i * golden;
      node.x = cx + r * Math.cos(a);
      node.y = cy + r * Math.sin(a);
    });

    const index = new Map(this.nodes.map(node => [node.id, node]));
    const k = 0.9 * Math.sqrt((w * h) / n);   // distance "idéale" entre nœuds
    let temperature = side / 8;

    for (let iter = 0; iter < 300; iter++) {
      const dx = new Map<string, number>();
      const dy = new Map<string, number>();
      for (const node of this.nodes) { dx.set(node.id, 0); dy.set(node.id, 0); }

      // Répulsion entre toutes les paires.
      for (let i = 0; i < n; i++) {
        for (let j = i + 1; j < n; j++) {
          const a = this.nodes[i], b = this.nodes[j];
          let vx = a.x - b.x, vy = a.y - b.y;
          let d = Math.hypot(vx, vy);
          if (d < 0.01) { vx = 0.1 * ((i % 3) - 1) || 0.1; vy = 0.1; d = Math.hypot(vx, vy); }
          const force = (k * k) / d;
          dx.set(a.id, dx.get(a.id)! + (vx / d) * force);
          dy.set(a.id, dy.get(a.id)! + (vy / d) * force);
          dx.set(b.id, dx.get(b.id)! - (vx / d) * force);
          dy.set(b.id, dy.get(b.id)! - (vy / d) * force);
        }
      }

      // Attraction le long des arêtes.
      for (const e of this.adjacency) {
        const a = index.get(e.a)!, b = index.get(e.b)!;
        const vx = a.x - b.x, vy = a.y - b.y;
        const d = Math.max(0.01, Math.hypot(vx, vy));
        const force = (d * d) / k;
        dx.set(a.id, dx.get(a.id)! - (vx / d) * force);
        dy.set(a.id, dy.get(a.id)! - (vy / d) * force);
        dx.set(b.id, dx.get(b.id)! + (vx / d) * force);
        dy.set(b.id, dy.get(b.id)! + (vy / d) * force);
      }

      // Gravité douce vers le centre (sinon les composantes isolées fuient).
      for (const node of this.nodes) {
        dx.set(node.id, dx.get(node.id)! + (cx - node.x) * 0.06);
        dy.set(node.id, dy.get(node.id)! + (cy - node.y) * 0.06);
      }

      // Application bornée par la température, SANS clamp au cadre : le clamp à
      // chaque itération écrasait des rangées de nœuds contre les bords (libellés
      // illisibles). Le cadrage se fait après coup dans normalizeToFrame().
      for (const node of this.nodes) {
        const ddx = dx.get(node.id)!, ddy = dy.get(node.id)!;
        const d = Math.max(0.01, Math.hypot(ddx, ddy));
        const step = Math.min(d, temperature);
        node.x += (ddx / d) * step;
        node.y += (ddy / d) * step;
      }
      temperature *= 0.96;
    }

    // Sans clamp, le nuage libre est très étendu (la gravité n'équilibre la
    // répulsion qu'à grande distance) : on le remet d'abord à l'échelle du cadre
    // cible, puis l'anti-chevauchement ré-étend LOCALEMENT là où c'est nécessaire.
    this.rescaleTo(side);
    this.fanOutLeaves();
    this.resolveLabelOverlaps();
    this.normalizeToFrame();
  }

  /**
   * Dispose en ÉVENTAIL les feuilles (degré 1) autour de leur hub (degré ≥ 3) :
   * huit PNJ pointant le même lieu forment une couronne régulière au lieu d'un
   * amas. Angles déterministes (départ = direction hub→extérieur du nuage).
   */
  private fanOutLeaves(): void {
    const neighbors = new Map<string, string[]>();
    for (const e of this.adjacency) {
      (neighbors.get(e.a) ?? neighbors.set(e.a, []).get(e.a)!).push(e.b);
      (neighbors.get(e.b) ?? neighbors.set(e.b, []).get(e.b)!).push(e.a);
    }
    const leavesByHub = new Map<string, GraphNode[]>();
    for (const node of this.nodes) {
      const nbs = neighbors.get(node.id) ?? [];
      if (nbs.length !== 1) continue;
      const hubId = nbs[0];
      if ((neighbors.get(hubId)?.length ?? 0) < 3) continue;
      (leavesByHub.get(hubId) ?? leavesByHub.set(hubId, []).get(hubId)!).push(node);
    }
    if (leavesByHub.size === 0) return;

    const index = new Map(this.nodes.map(n => [n.id, n]));
    const cx = this.nodes.reduce((s, n) => s + n.x, 0) / this.nodes.length;
    const cy = this.nodes.reduce((s, n) => s + n.y, 0) / this.nodes.length;
    for (const [hubId, leaves] of leavesByHub) {
      const hub = index.get(hubId);
      if (!hub) continue;
      leaves.sort((a, b) => a.label.localeCompare(b.label)); // ordre stable
      const radius = 85 + leaves.length * 6;
      // Première feuille vers l'extérieur du nuage : la couronne empiète moins
      // sur le cœur du graphe.
      const start = Math.atan2(hub.y - cy, hub.x - cx);
      const step = (2 * Math.PI) / leaves.length;
      leaves.forEach((leaf, i) => {
        leaf.x = hub.x + radius * Math.cos(start + i * step);
        leaf.y = hub.y + radius * Math.sin(start + i * step);
      });
    }
  }

  /** Réduit homothétiquement le nuage pour tenir dans un carré `side` (jamais d'agrandissement). */
  private rescaleTo(side: number): void {
    const xs = this.nodes.map(n => n.x), ys = this.nodes.map(n => n.y);
    const minX = Math.min(...xs), maxX = Math.max(...xs);
    const minY = Math.min(...ys), maxY = Math.max(...ys);
    const span = Math.max(1, maxX - minX, maxY - minY);
    const scale = Math.min(1, (side - 2 * this.MARGIN) / span);
    const cx = (minX + maxX) / 2, cy = (minY + maxY) / 2;
    for (const node of this.nodes) {
      node.x = cx + (node.x - cx) * scale;
      node.y = cy + (node.y - cy) * scale;
    }
  }

  /** Largeur approximative du libellé affiché (police 11px ≈ 6.4px/caractère, ligne la plus longue). */
  private labelWidth(node: GraphNode): number {
    const longest = node.lines.reduce((max, l) => Math.max(max, l.length), 0);
    return longest * 6.4 + 14;
  }

  /**
   * Anti-chevauchement TENANT COMPTE DES LIBELLÉS : le layout de force espace les
   * centres des cercles, mais un libellé fait ~120px de large — deux nœuds voisins
   * se lisaient l'un sur l'autre. Écarte itérativement chaque paire trop proche
   * selon l'axe le moins coûteux. Déterministe (pas d'aléatoire).
   */
  private resolveLabelOverlaps(): void {
    const nodes = this.nodes;
    for (let iter = 0; iter < 80; iter++) {
      let moved = false;
      for (let i = 0; i < nodes.length; i++) {
        for (let j = i + 1; j < nodes.length; j++) {
          const a = nodes[i], b = nodes[j];
          const needX = (this.labelWidth(a) + this.labelWidth(b)) / 2 + 6;
          const needY = this.radiusOf(a) + this.radiusOf(b) + 42; // cercles + jusqu'à 2 lignes de texte
          const dx = b.x - a.x, dy = b.y - a.y;
          if (Math.abs(dx) >= needX || Math.abs(dy) >= needY) continue;
          const overlapX = needX - Math.abs(dx);
          const overlapY = needY - Math.abs(dy);
          // Départage déterministe quand les centres coïncident (dx/dy = 0).
          const tieBreak = i % 2 === 0 ? 1 : -1;
          if (overlapX / needX < overlapY / needY) {
            const shift = overlapX / 2 + 0.5;
            const sign = dx !== 0 ? Math.sign(dx) : tieBreak;
            a.x -= sign * shift;
            b.x += sign * shift;
          } else {
            const shift = overlapY / 2 + 0.5;
            const sign = dy !== 0 ? Math.sign(dy) : tieBreak;
            a.y -= sign * shift;
            b.y += sign * shift;
          }
          moved = true;
        }
      }
      if (!moved) break;
    }
  }

  /**
   * Recadre le nuage dans le SVG : translation pour que tout (cercles ET libellés)
   * soit visible avec une marge, taille du SVG = étendue réelle du graphe.
   */
  private normalizeToFrame(): void {
    let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
    for (const node of this.nodes) {
      const half = Math.max(this.labelWidth(node) / 2, this.radiusOf(node));
      minX = Math.min(minX, node.x - half);
      maxX = Math.max(maxX, node.x + half);
      minY = Math.min(minY, node.y - this.radiusOf(node));
      maxY = Math.max(maxY, node.y + this.radiusOf(node) + 38); // libellé (2 lignes max) sous le cercle
    }
    const pad = 24;
    for (const node of this.nodes) {
      node.x += pad - minX;
      node.y += pad - minY;
    }
    this.svgWidth = Math.max(600, Math.ceil(maxX - minX + pad * 2));
    this.svgHeight = Math.max(400, Math.ceil(maxY - minY + pad * 2));
  }

  /** Recalcule la géométrie des arêtes depuis les positions courantes des nœuds. */
  private recomputeEdges(): void {
    const index = new Map(this.nodes.map(n => [n.id, n]));
    this.edges = this.adjacency
      .filter(e => index.has(e.a) && index.has(e.b))
      .map(e => {
        const a = index.get(e.a)!, b = index.get(e.b)!;
        return { key: e.key, kind: e.kind, a: e.a, b: e.b, x1: a.x, y1: a.y, x2: b.x, y2: b.y };
      });
  }

  /** Rayon d'un nœud : grossit doucement avec son nombre de liens. */
  radiusOf(node: GraphNode): number {
    return 14 + Math.min(10, node.degree * 1.5);
  }

  // --- Interactions (drag pour réarranger, clic pour ouvrir) ----------------

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
    if (!this.draggingId) return;
    const node = this.nodes.find(n => n.id === this.draggingId);
    if (!node) return;
    const { x, y } = this.toSvgCoords(evt);
    const newX = Math.max(this.MARGIN / 2, x - this.dragOffsetX);
    const newY = Math.max(this.MARGIN / 2, y - this.dragOffsetY);
    if (!this.dragMoved) {
      if (Math.hypot(newX - node.x, newY - node.y) < this.DRAG_THRESHOLD) return;
      this.dragMoved = true;
    }
    node.x = newX;
    node.y = newY;
    this.recomputeEdges();
    this.fitSvgToNodes();
  }

  onPointerUp(evt: PointerEvent): void {
    if (!this.draggingId) return;
    const id = this.draggingId;
    const moved = this.dragMoved;
    this.draggingId = null;
    this.dragMoved = false;
    (evt.target as Element).releasePointerCapture?.(evt.pointerId);
    if (moved) {
      this.scheduleSave();
      return;
    }
    // Clic simple → ouvre la fiche de l'entité (page, PNJ, scène, quête).
    const node = this.nodes.find(n => n.id === id);
    if (node) this.router.navigate(node.route);
  }

  /** Agrandit le SVG si un nœud déplacé s'approche du bord (jamais de réduction). */
  private fitSvgToNodes(): void {
    for (const n of this.nodes) {
      if (n.x + this.MARGIN > this.svgWidth) this.svgWidth = n.x + this.MARGIN;
      if (n.y + this.MARGIN > this.svgHeight) this.svgHeight = n.y + this.MARGIN;
    }
  }

  /**
   * Découpe un libellé en 1 à 2 lignes de {@link MAX_LABEL_CHARS} max (coupure aux
   * mots) : « 1 - Le convoi de marchands » reste lisible au lieu d'être tronqué.
   */
  private splitLabel(text: string): string[] {
    const max = this.MAX_LABEL_CHARS;
    if (text.length <= max) return [text];
    let line1 = '';
    for (const word of text.split(/\s+/)) {
      const candidate = line1 ? `${line1} ${word}` : word;
      if (candidate.length > max) break;
      line1 = candidate;
    }
    if (!line1) line1 = text.slice(0, max); // premier mot plus long que la ligne
    let rest = text.slice(line1.length).trim();
    if (!rest) return [line1];
    if (rest.length > max) rest = rest.slice(0, max - 1) + '…';
    return [line1, rest];
  }

  back(): void {
    this.router.navigate(['/campaigns', this.campaignId]);
  }

  ngOnDestroy(): void {
    // Une sauvegarde en attente part immédiatement (sinon le drag serait perdu).
    if (this.saveTimer) {
      clearTimeout(this.saveTimer);
      this.savePositions();
    }
    // Sidebar : volontairement rien — le composant suivant appellera show().
    // Eviter d'appeler hide() ici previent le clignotement.
  }
}
