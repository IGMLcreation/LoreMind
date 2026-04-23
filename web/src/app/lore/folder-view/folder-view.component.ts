import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { LucideAngularModule, LucideIconData, Folder, FileText, Pencil, Trash2, Plus, ChevronRight } from 'lucide-angular';
import { LoreService } from '../../services/lore.service';
import { TemplateService } from '../../services/template.service';
import { PageService } from '../../services/page.service';
import { LayoutService } from '../../services/layout.service';
import { PageTitleService } from '../../services/page-title.service';
import { Lore, LoreNode } from '../../services/lore.model';
import { Page } from '../../services/page.model';
import { loadLoreSidebarData, buildLoreSidebarConfig } from '../lore-sidebar.helper';
import { resolveIcon } from '../lore-icons';

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
  standalone: true,
  imports: [CommonModule, LucideAngularModule],
  templateUrl: './folder-view.component.html',
  styleUrls: ['./folder-view.component.scss']
})
export class FolderViewComponent implements OnInit, OnDestroy {
  readonly Folder = Folder;
  readonly FileText = FileText;
  readonly Pencil = Pencil;
  readonly Trash2 = Trash2;
  readonly Plus = Plus;
  readonly ChevronRight = ChevronRight;

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
    private pageTitleService: PageTitleService
  ) {}

  ngOnInit(): void {
    this.loreId = this.route.snapshot.paramMap.get('loreId')!;
    // Réagit aux changements de :folderId pour que la navigation d'un dossier
    // à un autre via la sidebar ne démonte/remonte pas le composant à blanc.
    this.route.paramMap.subscribe(pm => {
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
      this.subfolders = sidebar.nodes.filter(n => n.parentId === this.folderId);
      this.pages = sidebar.pages.filter(p => p.nodeId === this.folderId);
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
        if (impact.folders > 0) parts.push(`${impact.folders} sous-dossier${impact.folders > 1 ? 's' : ''}`);
        if (impact.pages > 0) parts.push(`${impact.pages} page${impact.pages > 1 ? 's' : ''}`);

        const lines = [`Supprimer le dossier "${node.name}" ?`];
        if (parts.length) {
          lines.push('');
          lines.push(`Cette action supprimera aussi : ${parts.join(', ')}.`);
        }
        lines.push('');
        lines.push('Cette action est irréversible.');

        if (!confirm(lines.join('\n'))) return;
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
      },
      error: () => console.error('Impossible de récupérer les dépendances du dossier')
    });
  }

  ngOnDestroy(): void {
    this.layoutService.hide();
  }
}
