import { Component, Input, Output, EventEmitter, HostListener, OnDestroy, ElementRef, forwardRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { CdkDropList, CdkDrag, CdkDragDrop, moveItemInArray, transferArrayItem } from '@angular/cdk/drag-drop';
import { LucideAngularModule, ChevronRight, ChevronDown, PanelLeftClose, PanelLeftOpen, Plus, FolderPlus, FilePlus, Home, LucideIconData } from 'lucide-angular';
import { TreeItem, TreeCreateAction, SidebarAction, BottomPanel, BottomPanelItem, LayoutService, ReorderKind, SidebarReorderContext } from '../../services/layout.service';
import { SidebarReorderService } from '../../services/sidebar-reorder.service';
import { SidebarTreeNodeComponent } from './sidebar-tree-node.component';
import { resolveIcon } from '../../lore/lore-icons';

@Component({
    selector: 'app-secondary-sidebar',
    imports: [CommonModule, LucideAngularModule, TranslatePipe, CdkDropList, CdkDrag, forwardRef(() => SidebarTreeNodeComponent)],
    templateUrl: './secondary-sidebar.component.html',
    styleUrls: ['./secondary-sidebar.component.scss']
})
export class SecondarySidebarComponent implements OnDestroy {
  @Input() title = '';
  /** Si défini, le titre est cliquable et navigue vers cette route (accueil de section). */
  @Input() titleRoute: string | null = null;
  @Input() createActions: SidebarAction[] = [];
  @Input() bottomPanel: BottomPanel | null = null;
  /** DnD : types déplaçables à la racine, parent racine, et contexte de rechargement. */
  @Input() rootDropKinds: ReorderKind[] | null = null;
  @Input() rootDropParentId: string | null = null;
  @Input() reorderContext: SidebarReorderContext | null = null;
  @Output() collapsedChange = new EventEmitter<boolean>();

  /** true = ouvert (on affiche les items) ; false = replié (titre seul). */
  panelOpen = true;

  readonly ChevronDown = ChevronDown;
  readonly ChevronRight = ChevronRight;
  readonly PanelLeftClose = PanelLeftClose;
  readonly PanelLeftOpen = PanelLeftOpen;
  readonly Plus = Plus;
  readonly FolderPlus = FolderPlus;
  readonly FilePlus = FilePlus;
  readonly Home = Home;

  isCollapsed = false;

  // --- Resize (étirement horizontal) -------------------------------------
  /** Clé localStorage pour persister la largeur choisie par l'utilisateur. */
  private static readonly WIDTH_STORAGE_KEY = 'secondary-sidebar-width';
  private static readonly MIN_WIDTH = 180;
  private static readonly MAX_WIDTH = 600;
  private static readonly DEFAULT_WIDTH = 220;

  /** Largeur courante en px (bindée en [style.width.px]). */
  width = SecondarySidebarComponent.DEFAULT_WIDTH;
  private isResizing = false;

  private _items: TreeItem[] = [];

  // Données de drag STABLES (références constantes entre détections de changement).
  // Indispensable : recalculer le tableau à chaque CD réinitialiserait le tri CDK en
  // plein drag (l'élément flotterait sans s'insérer). Reconstruites au set d'items.
  private _dragData = new Map<string, TreeItem[]>();
  private _predicates = new Map<string, (drag: CdkDrag) => boolean>();
  private _nodesById = new Map<string, TreeItem>();
  rootDragData: TreeItem[] = [];
  /** Ids de TOUTES les drop lists, pour les connecter explicitement (registre global
   *  CDK → fonctionne à travers les composants, contrairement à cdkDropListGroup). */
  dropListIds: string[] = ['sidebar-root'];

  @Input() set items(value: TreeItem[]) {
    this._items = value ?? [];
    this._buildDragMaps();
    this.autoExpandActiveAncestors();
  }
  get items(): TreeItem[] { return this._items; }

  /** Prédicat racine (stable) : lit `rootDropKinds` au moment de l'appel. */
  rootEnterPredicate = (drag: CdkDrag): boolean => {
    const k = (drag.data as TreeItem | undefined)?.dragKind;
    return !!k && !!this.rootDropKinds && this.rootDropKinds.includes(k);
  };

  constructor(
    private router: Router,
    private layoutService: LayoutService,
    private elementRef: ElementRef<HTMLElement>,
    private reorderService: SidebarReorderService
  ) {
    try {
      const stored = localStorage.getItem(SecondarySidebarComponent.WIDTH_STORAGE_KEY);
      const parsed = stored ? parseInt(stored, 10) : NaN;
      if (!isNaN(parsed)) {
        this.width = Math.min(
          Math.max(parsed, SecondarySidebarComponent.MIN_WIDTH),
          SecondarySidebarComponent.MAX_WIDTH
        );
      }
    } catch { /* storage indisponible : on garde la valeur par défaut */ }
  }

  /** Début du resize — on active le flag et on désactive la sélection texte le temps du drag. */
  startResize(event: MouseEvent): void {
    if (this.isCollapsed) return;
    event.preventDefault();
    this.isResizing = true;
    document.body.style.userSelect = 'none';
    document.body.style.cursor = 'col-resize';
  }

  @HostListener('document:mousemove', ['$event'])
  onResizeMove(event: MouseEvent): void {
    if (!this.isResizing) return;
    // La sidebar peut être précédée par la sidebar primaire : on calcule la largeur
    // cible à partir du bord gauche du composant, pas de la fenêtre. Sinon le
    // curseur et la poignée se désynchronisent.
    const rect = this.elementRef.nativeElement.getBoundingClientRect();
    const delta = event.clientX - rect.left;
    const next = Math.min(
      Math.max(delta, SecondarySidebarComponent.MIN_WIDTH),
      SecondarySidebarComponent.MAX_WIDTH
    );
    this.width = next;
  }

  @HostListener('document:mouseup')
  onResizeEnd(): void {
    if (!this.isResizing) return;
    this.isResizing = false;
    document.body.style.userSelect = '';
    document.body.style.cursor = '';
    try {
      localStorage.setItem(SecondarySidebarComponent.WIDTH_STORAGE_KEY, String(this.width));
    } catch { /* storage indisponible : on ignore */ }
  }

  ngOnDestroy(): void {
    // Sécurité : si le composant est détruit en plein drag, on restaure le curseur global.
    if (this.isResizing) {
      document.body.style.userSelect = '';
      document.body.style.cursor = '';
    }
  }

  runAction(action: SidebarAction): void {
    if (action.route) { this.router.navigate([action.route]); }
  }

  /** Clic sur le titre cliquable : retour à l'accueil de la section (ex: campagne). */
  clickTitle(): void {
    if (this.titleRoute) { this.router.navigate([this.titleRoute]); }
  }

  /** True si on est déjà sur la route du titre (surligne le titre comme actif). */
  isTitleActive(): boolean {
    if (!this.titleRoute) return false;
    return this.router.isActive(this.titleRoute, {
      paths: 'exact', queryParams: 'ignored', fragment: 'ignored', matrixParams: 'ignored'
    });
  }

  clickItem(item: TreeItem): void {
    if (item.route) { this.router.navigate([item.route]); return; }
    this.toggleItem(item.id);
  }

  /**
   * Clic sur le chevron : toggle uniquement (ne navigue jamais).
   * stopPropagation évite que l'event remonte au bouton parent.
   */
  clickChevron(event: Event, item: TreeItem): void {
    event.stopPropagation();
    this.toggleItem(item.id);
  }

  toggleCollapse(): void {
    this.isCollapsed = !this.isCollapsed;
    this.collapsedChange.emit(this.isCollapsed);
  }

  toggleItem(id: string): void {
    this.layoutService.toggleExpanded(id);
  }

  isExpanded(id: string): boolean {
    return this.layoutService.isExpanded(id);
  }

  togglePanel(): void {
    this.panelOpen = !this.panelOpen;
  }

  clickPanelItem(item: BottomPanelItem): void {
    if (item.route) { this.router.navigate([item.route]); }
  }

  /** Clic sur le "+" du header : navigue sans toggler le panneau (stopPropagation). */
  runPanelHeaderAction(event: Event, action: { route: string }): void {
    event.stopPropagation();
    this.router.navigate([action.route]);
  }

  /** Résout la clé d'icône d'un TreeItem en icône lucide pour le template. */
  iconFor(item: TreeItem): LucideIconData | null {
    return item.iconKey ? resolveIcon(item.iconKey) : null;
  }

  /** Resolution d'icone pour un TreeCreateAction (hover + empty-state). */
  iconForAction(action: TreeCreateAction): LucideIconData {
    switch (action.actionIcon) {
      case 'folder-plus': return FolderPlus;
      case 'file-plus':   return FilePlus;
      default:            return Plus;
    }
  }

  /**
   * Declenche une action de creation contextuelle. stopPropagation pour eviter
   * que le clic ne remonte au bouton parent (qui navigue ou toggle).
   */
  runCreateAction(event: Event, action: TreeCreateAction): void {
    event.stopPropagation();
    this.router.navigate([action.route], action.queryParams ? { queryParams: action.queryParams } : {});
  }

  /** True si le noeud a au moins un vrai enfant (utile pour le chevron). */
  hasChildren(item: TreeItem): boolean {
    return !!item.children && item.children.length > 0;
  }

  // --- Glisser-déposer (réordonnancement dans l'arbre) --------------------

  /** DnD actif uniquement si un contexte de rechargement est fourni (campagne/lore). */
  dndEnabled(): boolean {
    return !!this.reorderContext;
  }

  /**
   * Reconstruit, pour chaque nœud, son tableau d'enfants déplaçables et son prédicat
   * de dépôt — une seule fois (au set d'items) pour fournir des références STABLES.
   * Filtrer les non-déplaçables garde les indices CDK alignés sur la donnée.
   */
  private _buildDragMaps(): void {
    this._dragData.clear();
    this._predicates.clear();
    this._nodesById.clear();
    this.dropListIds = ['sidebar-root'];
    this.rootDragData = this._items.filter(i => !!i.dragKind);
    const walk = (nodes: TreeItem[]): void => {
      for (const n of nodes) {
        this._nodesById.set(n.id, n);
        if (n.dropKinds) {
          this._predicates.set(n.id, this._makePredicate(n.dropKinds));
          this.dropListIds.push('lst-' + n.id);
        }
        if (n.children?.length) {
          this._dragData.set(n.id, n.children.filter(c => !!c.dragKind));
          walk(n.children);
        }
      }
    };
    walk(this._items);
  }

  private _makePredicate(kinds: ReorderKind[] | null): (drag: CdkDrag) => boolean {
    return (drag: CdkDrag): boolean => {
      const k = (drag.data as TreeItem | undefined)?.dragKind;
      return !!k && !!kinds && kinds.includes(k);
    };
  }

  /** Données (stables) de la drop list des enfants d'un nœud. */
  dragDataFor(item: TreeItem): TreeItem[] {
    return this._dragData.get(item.id) ?? [];
  }

  /** Prédicat (stable) de la drop list des enfants d'un nœud. */
  enterPredicateFor(item: TreeItem): (drag: CdkDrag) => boolean {
    return this._predicates.get(item.id) ?? (() => false);
  }

  /**
   * Dépôt d'un élément. Le POINT DE LÂCHER fait autorité (override fiable de la
   * détection de conteneur de CDK, qui est ambiguë avec des listes imbriquées) :
   *  - lâché sur un AUTRE dossier (compatible) → déplacement DANS ce dossier ;
   *  - sinon → réordonnancement dans la liste courante.
   * Puis on persiste et on recharge depuis le backend (source de vérité).
   */
  onDrop(parent: TreeItem | null, event: CdkDragDrop<TreeItem[]>): void {
    if (!this.reorderContext) return;
    const dragged = event.item.data as TreeItem;
    const kind = dragged?.dragKind;
    if (!kind || !dragged.dragId) return;

    // 1) Le curseur est-il sur un dossier compatible, DIFFÉRENT du dossier d'origine ?
    const folder = this.folderAtPoint(event.dropPoint, kind);
    const currentParent = parent ? (parent.dropParentId ?? null) : (this.rootDropParentId ?? null);
    if (folder && (folder.dropParentId ?? null) !== currentParent) {
      this.moveIntoFolder(dragged, kind, folder);
      return;
    }

    // 2) Sinon : réordonnancement (même liste) ou déplacement géré par CDK (autre liste).
    const target = event.container.data;
    if (event.previousContainer === event.container) {
      if (event.previousIndex === event.currentIndex) return;
      moveItemInArray(target, event.previousIndex, event.currentIndex);
    } else {
      transferArrayItem(event.previousContainer.data, target, event.previousIndex, event.currentIndex);
    }
    const orderedIds = target.filter(i => i.dragKind === kind).map(i => i.dragId!).filter(Boolean);
    this.reorderService.reorder(this.reorderContext, kind, currentParent, orderedIds);
  }

  /** Dossier compatible sous le point de lâcher (via le DOM réel), ou null. */
  private folderAtPoint(pt: { x: number; y: number } | undefined, kind: ReorderKind): TreeItem | null {
    if (!pt) return null;
    const el = document.elementFromPoint(pt.x, pt.y) as HTMLElement | null;
    const nodeEl = el?.closest('[data-drop-node]') as HTMLElement | null;
    const id = nodeEl?.getAttribute('data-drop-node');
    const node = id ? this._nodesById.get(id) : undefined;
    return node && node.dropKinds?.includes(kind) ? node : null;
  }

  /** Déplace l'élément à la FIN d'un dossier cible (déplacement inter-dossiers). */
  private moveIntoFolder(dragged: TreeItem, kind: ReorderKind, folder: TreeItem): void {
    const existing = (this._dragData.get(folder.id) ?? [])
      .filter(i => i.dragKind === kind)
      .map(i => i.dragId!)
      .filter(id => id && id !== dragged.dragId);
    this.reorderService.reorder(this.reorderContext!, kind, folder.dropParentId ?? null, [...existing, dragged.dragId!]);
  }

  /** True si le chevron doit s'afficher — seulement quand le noeud a de vrais enfants. */
  isExpandable(item: TreeItem): boolean {
    return this.hasChildren(item);
  }

  /**
   * True si la route du node correspond exactement à l'URL courante. Utilisé
   * pour surligner le dossier / page / scène en cours dans l'arbre — utile
   * quand plusieurs entrées partagent le même label (ex : deux sous-dossiers
   * "test" dans la même arborescence).
   */
  isActive(item: TreeItem): boolean {
    if (!item.route) return false;
    return this.router.isActive(item.route, {
      paths: 'exact',
      queryParams: 'ignored',
      fragment: 'ignored',
      matrixParams: 'ignored'
    });
  }

  /**
   * Auto-déplie la chaîne d'ancêtres du item dont `route` matche l'URL active.
   * Nécessaire car la sidebar est détruite/recréée à chaque navigation (ngIf
   * dans app.component.html) : sans ça, même si on persiste `expandedItems`
   * dans le service, un deep-link sur une page profonde arriverait tout replié.
   */
  private autoExpandActiveAncestors(): void {
    const url = this.router.url;
    // On descend d'abord dans les enfants pour trouver le match le plus profond :
    // sinon, un parent qui matche par préfixe (ex. /campaigns/A/arcs/X matche
    // aussi /campaigns/A/arcs/X/chapters/M) court-circuiterait la descente et
    // on ne déplierait pas l'arc pour montrer le chapitre actif.
    const walk = (item: TreeItem, ancestors: string[]): boolean => {
      if (item.children) {
        const nextAncestors = [...ancestors, item.id];
        for (const child of item.children) {
          if (walk(child, nextAncestors)) return true;
        }
      }
      const matches = !!item.route && (item.route === url || url.startsWith(item.route + '/'));
      if (matches) {
        ancestors.forEach(id => this.layoutService.setExpanded(id, true));
        return true;
      }
      return false;
    };
    for (const root of this._items) {
      walk(root, []);
    }
  }
}
