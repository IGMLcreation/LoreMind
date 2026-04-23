import { Component, Input, Output, EventEmitter, HostListener, OnDestroy, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { LucideAngularModule, ChevronRight, ChevronDown, PanelLeftClose, PanelLeftOpen, Plus, FolderPlus, FilePlus, LucideIconData } from 'lucide-angular';
import { TreeItem, TreeCreateAction, SidebarAction, BottomPanel, BottomPanelItem, LayoutService } from '../../services/layout.service';
import { resolveIcon } from '../../lore/lore-icons';

@Component({
  selector: 'app-secondary-sidebar',
  standalone: true,
  imports: [CommonModule, LucideAngularModule],
  templateUrl: './secondary-sidebar.component.html',
  styleUrls: ['./secondary-sidebar.component.scss']
})
export class SecondarySidebarComponent implements OnDestroy {
  @Input() title = '';
  @Input() createActions: SidebarAction[] = [];
  @Input() bottomPanel: BottomPanel | null = null;
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

  @Input() set items(value: TreeItem[]) {
    this._items = value ?? [];
    this.autoExpandActiveAncestors();
  }
  get items(): TreeItem[] { return this._items; }

  constructor(
    private router: Router,
    private layoutService: LayoutService,
    private elementRef: ElementRef<HTMLElement>
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
    this.router.navigate([action.route]);
  }

  /** True si le noeud a au moins un vrai enfant (utile pour le chevron). */
  hasChildren(item: TreeItem): boolean {
    return !!item.children && item.children.length > 0;
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
