import { Component, Input, Output, EventEmitter } from '@angular/core';
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
export class SecondarySidebarComponent {
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

  private _items: TreeItem[] = [];

  @Input() set items(value: TreeItem[]) {
    this._items = value ?? [];
    this.autoExpandActiveAncestors();
  }
  get items(): TreeItem[] { return this._items; }

  constructor(private router: Router, private layoutService: LayoutService) {}

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

  /**
   * True si le chevron doit s'afficher : soit il y a des enfants, soit le
   * noeud a des createActions (dans ce cas deplier revele l'empty-state).
   */
  isExpandable(item: TreeItem): boolean {
    return this.hasChildren(item) || (item.createActions?.length ?? 0) > 0;
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
