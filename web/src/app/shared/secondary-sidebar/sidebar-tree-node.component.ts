import { Component, Input, HostBinding, forwardRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslatePipe } from '@ngx-translate/core';
import { CdkDropList, CdkDrag, CdkDragHandle } from '@angular/cdk/drag-drop';
import { LucideAngularModule, ChevronRight, ChevronDown, GripVertical } from 'lucide-angular';
import { TreeItem } from '../../services/layout.service';
import { SecondarySidebarComponent } from './secondary-sidebar.component';

/**
 * Un nœud de l'arbre de la sidebar (récursif).
 *
 * Pourquoi un COMPOSANT récursif et pas un `*ngTemplateOutlet` ? Parce que `cdkDrag`
 * résout sa drop list par INJECTION au lieu de DÉCLARATION du template : un template
 * outlet-é se résout au site de DÉCLARATION (hors des drop lists) → le drag n'a aucun
 * conteneur → aucun réordonnancement (l'élément flotte). En rendant chaque enfant via
 * `<app-sidebar-tree-node cdkDrag>` STATIQUEMENT dans la drop list du parent, le DI
 * trouve la bonne liste et le tri/insertion fonctionne.
 *
 * Toute la logique (clic, expand, icônes, DnD) est déléguée au composant sidebar parent
 * (injecté) pour éviter la duplication.
 */
@Component({
  selector: 'app-sidebar-tree-node',
  imports: [
    CommonModule, LucideAngularModule, TranslatePipe,
    CdkDropList, CdkDrag, CdkDragHandle,
    forwardRef(() => SidebarTreeNodeComponent)
  ],
  templateUrl: './sidebar-tree-node.component.html',
  styleUrls: ['./sidebar-tree-node.component.scss']
})
export class SidebarTreeNodeComponent {
  @Input({ required: true }) item!: TreeItem;
  @Input() level = 0;

  /** Marque l'hôte comme dossier (cible de dépôt détectée par position de lâcher).
   *  Posé sur l'HÔTE = ancêtre du header ET des enfants → un drop n'importe où sur le
   *  dossier le résout correctement. */
  @HostBinding('attr.data-drop-node') get dropNodeAttr(): string | null {
    return this.item?.dropKinds ? this.item.id : null;
  }

  readonly ChevronRight = ChevronRight;
  readonly ChevronDown = ChevronDown;
  readonly GripVertical = GripVertical;

  /** Sidebar parente (toujours un ancêtre DI) : on lui délègue toute la logique. */
  readonly sidebar = inject(SecondarySidebarComponent);
}
