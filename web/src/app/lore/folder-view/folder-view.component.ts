import { Component, OnInit, DestroyRef } from '@angular/core';

import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CdkDropList, CdkDrag, CdkDragDrop, moveItemInArray } from '@angular/cdk/drag-drop';
import { DataSyncService } from '../../services/data-sync.service';
import { LucideAngularModule, LucideIconData, Folder, FileText, Pencil, Trash2, Plus, ChevronRight, GripVertical } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LoreService } from '../../services/lore.service';
import { TemplateService } from '../../services/template.service';
import { PageService } from '../../services/page.service';
import { LayoutService } from '../../services/layout.service';
import { PageTitleService } from '../../services/page-title.service';
import { Lore, LoreNode } from '../../services/lore.model';
import { Page } from '../../services/page.model';
import { loadLoreSidebarData, buildLoreSidebarConfig } from '../lore-sidebar.helper';
import { byOrder } from '../../shared/folder-grouping.util';
import { resolveIcon } from '../lore-icons';
import { ConfirmDialogService } from '../../shared/confirm-dialog/confirm-dialog.service';

/**
 * Vue "détail" d'un dossier : affiche son contenu (sous-dossiers + pages) et
 * expose les actions Modifier / Supprimer dans le header.
 *
 * L'édition du nom/icône/parent se fait dans l'écran séparé folder-edit
 * (/folders/:folderId/edit). La suppression avec cascade déclenche le même
 * dialogue d'impact que les autres écrans.
 */
@Component({
    selector: 'app-folder-view',
    imports: [LucideAngularModule, TranslatePipe, CdkDropList, CdkDrag],
    templateUrl: './folder-view.component.html',
    styleUrls: ['./folder-view.component.scss']
})
export class FolderViewComponent implements OnInit {
  readonly Folder = Folder;
  readonly FileText = FileText;
  readonly Pencil = Pencil;
  readonly Trash2 = Trash2;
  readonly Plus = Plus;
  readonly ChevronRight = ChevronRight;
  readonly GripVertical = GripVertical;

  loreId = '';
  folderId = '';
  lore: Lore | null = null;
  node: LoreNode | null = null;
  subfolders: LoreNode[] = [];
  pages: Page[] = [];
  /** Chaîne des dossiers ancêtres (du plus proche du racine vers le parent direct). */
  ancestors: LoreNode[] = [];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private loreService: LoreService,
    private templateService: TemplateService,
    private pageService: PageService,
    private layoutService: LayoutService,
    private pageTitleService: PageTitleService,
    private confirmDialog: ConfirmDialogService,
    private translate: TranslateService,
    private dataSync: DataSyncService,
    private destroyRef: DestroyRef
  ) {}

  ngOnInit(): void {
    this.dataSync.onChange(this.destroyRef, () => this.load());
    this.loreId = this.route.snapshot.paramMap.get('loreId')!;
    // Réagit aux changements de :folderId pour que la navigation d'un dossier
    // à un autre via la sidebar ne démonte/remonte pas le composant à blanc.
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(pm => {
      const next = pm.get('folderId')!;
      if (next !== this.folderId) {
        this.folderId = next;
        this.load();
      }
    });
    this.folderId = this.route.snapshot.paramMap.get('folderId')!;
    this.load();
  }

  private load(): void {
    forkJoin({
      sidebar: loadLoreSidebarData(this.loreId, this.loreService, this.templateService, this.pageService),
      node: this.loreService.getLoreNodeById(this.folderId)
    }).subscribe(({ sidebar, node }) => {
      this.layoutService.show(buildLoreSidebarConfig(sidebar));
      this.lore = sidebar.lore;
      this.node = node;
      this.pageTitleService.set(node.name);
      // Tri par `order` — cohérent avec l'arbre (comparateur `byOrder` mutualisé).
      this.subfolders = sidebar.nodes.filter(n => n.parentId === this.folderId).sort(byOrder);
      this.pages = sidebar.pages.filter(p => p.nodeId === this.folderId).sort(byOrder);
      this.ancestors = this.buildAncestors(node, sidebar.nodes);
    });
  }

  /**
   * Remonte la chaîne parentId → parent en partant du dossier courant,
   * sans s'inclure soi-même. Ordre : racine → parent direct.
   * Garde-fou sur la longueur au cas où une boucle existerait en BDD
   * (ne devrait pas, mais ceinture+bretelles).
   */
  private buildAncestors(current: LoreNode, allNodes: LoreNode[]): LoreNode[] {
    const byId = new Map(allNodes.map(n => [n.id!, n]));
    const chain: LoreNode[] = [];
    const seen = new Set<string>();
    let parentId = current.parentId ?? null;
    while (parentId && !seen.has(parentId) && chain.length < 32) {
      const parent = byId.get(parentId);
      if (!parent) break;
      chain.push(parent);
      seen.add(parent.id!);
      parentId = parent.parentId ?? null;
    }
    return chain.reverse();
  }

  /** Icône du dossier courant, résolue depuis la clé lucide stockée sur le node. */
  get folderIcon(): LucideIconData {
    return resolveIcon(this.node?.icon ?? null);
  }

  // --- Glisser-déposer (réordonnancement + déplacement de pages) -----------

  /** Ids des zones de dépôt « sous-dossier » (cibles connectées depuis la liste des pages). */
  get subfolderListIds(): string[] {
    return this.subfolders.map(s => 'sub-' + s.id);
  }

  /** Un drag transporte-t-il une Page (vs un dossier) ? La Page a un `title`. */
  private isPageDrag(drag: CdkDrag): boolean {
    const d = drag.data as { title?: string } | null;
    return !!d && typeof d.title === 'string';
  }

  /** Prédicat de dépôt : n'accepter que des pages (cibles dossier + grille pages). */
  acceptPages = (drag: CdkDrag): boolean => this.isPageDrag(drag);
  /** Prédicat de dépôt : n'accepter que des dossiers (grille des sous-dossiers). */
  acceptFolders = (drag: CdkDrag): boolean => !this.isPageDrag(drag);

  /** Réordonne les pages du dossier courant. */
  dropPage(event: CdkDragDrop<Page[]>): void {
    if (event.previousContainer !== event.container) return; // déplacement géré par dropPageIntoFolder
    if (event.previousIndex === event.currentIndex) return;
    moveItemInArray(this.pages, event.previousIndex, event.currentIndex);
    this.dataSync.persist(this.pageService.reorder(this.folderId, this.pages.map(p => p.id!)), () => this.load());
  }

  /** Réordonne les sous-dossiers du dossier courant. */
  dropSubfolder(event: CdkDragDrop<LoreNode[]>): void {
    if (event.previousContainer !== event.container) return;
    if (event.previousIndex === event.currentIndex) return;
    moveItemInArray(this.subfolders, event.previousIndex, event.currentIndex);
    this.dataSync.persist(this.loreService.reorderNodes(this.folderId, this.subfolders.map(s => s.id!)), () => this.load());
  }

  /** Déplace une page (déposée sur la carte d'un sous-dossier) dans ce sous-dossier. */
  dropPageIntoFolder(target: LoreNode, event: CdkDragDrop<LoreNode>): void {
    const page = event.item.data as Page;
    if (!page || page.nodeId === target.id) return;
    // Succès → notify() → onChange → this.load() : le dossier courant se recharge
    // et la page déplacée en disparaît.
    this.dataSync.persist(this.pageService.reorder(target.id!, [page.id!]), () => this.load());
  }

  navigateToSubfolder(id: string): void {
    this.router.navigate(['/lore', this.loreId, 'folders', id]);
  }

  navigateToLoreRoot(): void {
    this.router.navigate(['/lore', this.loreId]);
  }

  navigateToPage(id: string): void {
    this.router.navigate(['/lore', this.loreId, 'pages', id]);
  }

  navigateToCreateSubfolder(): void {
    this.router.navigate(['/lore', this.loreId, 'folders', this.folderId, 'create']);
  }

  navigateToCreatePage(): void {
    this.router.navigate(['/lore', this.loreId, 'nodes', this.folderId, 'pages', 'create']);
  }

  navigateToEdit(): void {
    this.router.navigate(['/lore', this.loreId, 'folders', this.folderId, 'edit']);
  }

  /**
   * Suppression en cascade avec dialogue d'impact. On délègue au backend (transaction
   * atomique), et au retour on remonte soit au dossier parent soit au Lore racine.
   */
  delete(): void {
    if (!this.node) return;
    const node = this.node;
    this.loreService.getLoreNodeDeletionImpact(this.folderId).subscribe({
      next: impact => {
        const parts: string[] = [];
        if (impact.folders > 0) parts.push(this.translate.instant(
          impact.folders > 1 ? 'folderView.impact.subfoldersPlural' : 'folderView.impact.subfolders',
          { n: impact.folders }));
        if (impact.pages > 0) parts.push(this.translate.instant(
          impact.pages > 1 ? 'folderView.impact.pagesPlural' : 'folderView.impact.pages',
          { n: impact.pages }));

        const details: string[] = [];
        if (parts.length) {
          details.push(this.translate.instant('folderView.impact.alsoDeletes', { items: parts.join(', ') }));
        }
        details.push(this.translate.instant('folderView.impact.irreversible'));

        this.confirmDialog.confirm({
          title: this.translate.instant('folderView.deleteConfirmTitle'),
          message: this.translate.instant('folderView.deleteConfirmMessage', { name: node.name }),
          details,
          confirmLabel: this.translate.instant('common.delete'),
          variant: 'danger'
        }).then(ok => {
          if (!ok) return;
          this.loreService.deleteLoreNode(this.folderId).subscribe({
            next: () => {
              // Remonte au dossier parent si présent, sinon au Lore.
              if (node.parentId) {
                this.router.navigate(['/lore', this.loreId, 'folders', node.parentId]);
              } else {
                this.router.navigate(['/lore', this.loreId]);
              }
            },
            error: () => console.error('Erreur lors de la suppression du dossier')
          });
        });
      },
      error: () => console.error('Impossible de récupérer les dépendances du dossier')
    });
  }
}
