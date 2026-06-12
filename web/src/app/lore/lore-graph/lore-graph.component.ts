import { Component, OnInit, OnDestroy, ElementRef, ViewChild } from '@angular/core';

import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { forkJoin } from 'rxjs';
import { LucideAngularModule, ArrowLeft, Network } from 'lucide-angular';
import { LoreService } from '../../services/lore.service';
import { TemplateService } from '../../services/template.service';
import { PageService } from '../../services/page.service';
import { NpcService } from '../../services/npc.service';
import { LayoutService } from '../../services/layout.service';
import { PageTitleService } from '../../services/page-title.service';
import { Lore } from '../../services/lore.model';
import { Page } from '../../services/page.model';
import { Npc } from '../../services/npc.model';
import { loadLoreSidebarData, buildLoreSidebarConfig } from '../lore-sidebar.helper';

/** Nœud du graphe : une page de Lore ou un PNJ qui référence des pages. */
interface GraphNode {
  id: string;            // 'page:<id>' ou 'npc:<id>' (évite les collisions d'IDs)
  kind: 'page' | 'npc';
  label: string;
  displayLabel: string;
  route: string[];       // navigation au clic
  x: number;             // centre du nœud (coords SVG)
  y: number;
  degree: number;        // nombre de liens (taille du nœud)
}

interface GraphEdge {
  key: string;
  kind: 'page' | 'npc';  // page↔page ou npc→page (style distinct)
  x1: number; y1: number; x2: number; y2: number;
}

/**
 * Graphe du Lore : vue d'ensemble des pages et de leurs liens.
 *
 * Nœuds = toutes les pages du Lore + les PNJ (toutes campagnes liées au Lore)
 * qui référencent au moins une page. Arêtes = `relatedPageIds` des pages
 * (liens page↔page) et des PNJ (liens PNJ→page).
 *
 * Layout force-directed (Fruchterman-Reingold simplifié) calculé une fois au
 * chargement, puis nœuds déplaçables à la souris — même approche SVG custom
 * que chapter-graph, sans dépendance externe.
 */
@Component({
    selector: 'app-lore-graph',
    imports: [RouterModule, LucideAngularModule],
    templateUrl: './lore-graph.component.html',
    styleUrls: ['./lore-graph.component.scss']
})
export class LoreGraphComponent implements OnInit, OnDestroy {
  readonly ArrowLeft = ArrowLeft;
  readonly Network = Network;

  loreId = '';
  lore: Lore | null = null;

  nodes: GraphNode[] = [];
  edges: GraphEdge[] = [];
  npcCount = 0;
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
  private adjacency: Array<{ key: string; kind: 'page' | 'npc'; a: string; b: string }> = [];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private loreService: LoreService,
    private templateService: TemplateService,
    private pageService: PageService,
    private npcService: NpcService,
    private layoutService: LayoutService,
    private pageTitleService: PageTitleService
  ) {}

  ngOnInit(): void {
    this.loreId = this.route.snapshot.paramMap.get('loreId')!;
    forkJoin({
      sidebar: loadLoreSidebarData(this.loreId, this.loreService, this.templateService, this.pageService),
      npcs: this.npcService.getByLore(this.loreId)
    }).subscribe(({ sidebar, npcs }) => {
      this.lore = sidebar.lore;
      this.layoutService.show(buildLoreSidebarConfig(sidebar));
      this.pageTitleService.set(`${sidebar.lore.name} — Graphe`);
      this.buildGraph(sidebar.pages, npcs);
    });
  }

  // --- Construction du graphe ----------------------------------------------

  private buildGraph(pages: Page[], npcs: Npc[]): void {
    const pageIds = new Set(pages.map(p => p.id!));

    // Nœuds pages (toutes, même isolées : la vue d'ensemble inclut les orphelines).
    const nodes: GraphNode[] = pages.map(p => ({
      id: `page:${p.id}`,
      kind: 'page' as const,
      label: p.title,
      displayLabel: this.truncate(p.title),
      route: ['/lore', this.loreId, 'pages', p.id!],
      x: 0, y: 0, degree: 0
    }));

    // Nœuds PNJ : seulement ceux qui référencent au moins une page de CE lore
    // (un PNJ sans lien n'apporte rien à la carte des connexions).
    const linkedNpcs = npcs.filter(n =>
      (n.relatedPageIds ?? []).some(pid => pageIds.has(pid)));
    for (const n of linkedNpcs) {
      nodes.push({
        id: `npc:${n.id}`,
        kind: 'npc',
        label: n.name,
        displayLabel: this.truncate(n.name),
        route: ['/campaigns', n.campaignId, 'npcs', n.id!],
        x: 0, y: 0, degree: 0
      });
    }
    this.npcCount = linkedNpcs.length;

    // Arêtes. Les liens page↔page sont dé-dupliqués par paire non-orientée
    // (A→B et B→A = un seul trait).
    const adjacency: Array<{ key: string; kind: 'page' | 'npc'; a: string; b: string }> = [];
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
    for (const n of linkedNpcs) {
      for (const targetId of new Set(n.relatedPageIds ?? [])) {
        if (!pageIds.has(targetId)) continue;
        adjacency.push({ key: `np:${n.id}|${targetId}`, kind: 'npc', a: `npc:${n.id}`, b: `page:${targetId}` });
      }
    }
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
    this.recomputeEdges();
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

      // Application bornée par la température, dans le cadre.
      for (const node of this.nodes) {
        const ddx = dx.get(node.id)!, ddy = dy.get(node.id)!;
        const d = Math.max(0.01, Math.hypot(ddx, ddy));
        const step = Math.min(d, temperature);
        node.x = Math.min(w - this.MARGIN, Math.max(this.MARGIN, node.x + (ddx / d) * step));
        node.y = Math.min(h - this.MARGIN, Math.max(this.MARGIN, node.y + (ddy / d) * step));
      }
      temperature *= 0.96;
    }

    this.svgWidth = w;
    this.svgHeight = h;
  }

  /** Recalcule la géométrie des arêtes depuis les positions courantes des nœuds. */
  private recomputeEdges(): void {
    const index = new Map(this.nodes.map(n => [n.id, n]));
    this.edges = this.adjacency
      .filter(e => index.has(e.a) && index.has(e.b))
      .map(e => {
        const a = index.get(e.a)!, b = index.get(e.b)!;
        return { key: e.key, kind: e.kind, x1: a.x, y1: a.y, x2: b.x, y2: b.y };
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
    if (moved) return;
    // Clic simple → ouvre la page / la fiche PNJ.
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

  private truncate(text: string): string {
    return text.length > this.MAX_LABEL_CHARS
      ? text.slice(0, this.MAX_LABEL_CHARS - 1) + '…'
      : text;
  }

  back(): void {
    this.router.navigate(['/lore', this.loreId]);
  }

  ngOnDestroy(): void {
    // Volontairement vide : la sidebar reste prise en charge par le composant
    // suivant (autre sous-route ou le composant detail parent) qui appellera
    // show(). Eviter d'appeler hide() ici previent le clignotement.
  }
}
