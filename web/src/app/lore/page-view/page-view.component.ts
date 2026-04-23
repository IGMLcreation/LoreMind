import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { forkJoin } from 'rxjs';
import { LucideAngularModule, Pencil, Trash2 } from 'lucide-angular';
import { LoreService } from '../../services/lore.service';
import { TemplateService } from '../../services/template.service';
import { PageService } from '../../services/page.service';
import { LayoutService } from '../../services/layout.service';
import { PageTitleService } from '../../services/page-title.service';
import { Lore, LoreNode } from '../../services/lore.model';
import { Template } from '../../services/template.model';
import { Page } from '../../services/page.model';
import { loadLoreSidebarData, buildLoreSidebarConfig } from '../lore-sidebar.helper';
import { BreadcrumbComponent, BreadcrumbItem } from '../../shared/breadcrumb/breadcrumb.component';
import { ImageGalleryComponent } from '../../shared/image-gallery/image-gallery.component';

/**
 * Écran de consultation d'une Page (mode lecture seule).
 *
 * Responsabilité : afficher une belle fiche, sans formulaire ni scrollbar interne.
 * Chaque champ du template est rendu en bloc titré dont le corps s'étend
 * verticalement selon le contenu (via CSS `white-space: pre-wrap`).
 *
 * Route : /lore/:loreId/pages/:pageId
 * Pour modifier → bouton "Modifier" qui navigue vers /lore/:loreId/pages/:pageId/edit.
 */
@Component({
  selector: 'app-page-view',
  standalone: true,
  imports: [CommonModule, RouterModule, LucideAngularModule, BreadcrumbComponent, ImageGalleryComponent],
  templateUrl: './page-view.component.html',
  styleUrls: ['./page-view.component.scss']
})
export class PageViewComponent implements OnInit, OnDestroy {
  readonly Pencil = Pencil;
  readonly Trash2 = Trash2;

  loreId = '';
  pageId = '';
  lore: Lore | null = null;
  page: Page | null = null;
  template: Template | null = null;
  nodes: LoreNode[] = [];
  allPages: Page[] = [];

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
    // Même pattern que page-edit : on s'abonne à paramMap pour gérer la
    // navigation d'une page à l'autre (Angular réutilise le composant).
    this.route.paramMap.subscribe(pm => {
      const newPageId = pm.get('pageId')!;
      if (newPageId && newPageId !== this.pageId) {
        this.pageId = newPageId;
        this.load();
      }
    });
  }

  private load(): void {
    forkJoin({
      sidebar: loadLoreSidebarData(this.loreId, this.loreService, this.templateService, this.pageService),
      page: this.pageService.getById(this.pageId)
    }).subscribe(({ sidebar, page }) => {
      this.lore = sidebar.lore;
      this.nodes = sidebar.nodes;
      this.allPages = sidebar.pages;
      this.template = sidebar.templates.find(t => t.id === page.templateId) ?? null;
      this.page = page;
      this.layoutService.show(buildLoreSidebarConfig(sidebar));
      this.pageTitleService.set(page.title);
    });
  }

  /** Fil d'Ariane — même logique que page-edit (remontée via parentId). */
  get breadcrumbItems(): BreadcrumbItem[] {
    if (!this.lore || !this.page) return [];
    const items: BreadcrumbItem[] = [
      { label: this.lore.name, route: ['/lore', this.loreId] }
    ];
    const folderChain: LoreNode[] = [];
    let currentNode = this.nodes.find(n => n.id === this.page!.nodeId);
    while (currentNode) {
      folderChain.unshift(currentNode);
      currentNode = currentNode.parentId
        ? this.nodes.find(n => n.id === currentNode!.parentId)
        : undefined;
    }
    for (const node of folderChain) {
      items.push({ label: node.name, route: ['/lore', this.loreId, 'folders', node.id] });
    }
    items.push({ label: this.page.title });
    return items;
  }

  /** Récupère la valeur d'un champ dynamique TEXT du template. */
  valueOf(fieldName: string): string {
    return this.page?.values?.[fieldName] ?? '';
  }

  /** IDs d'images pour un champ IMAGE (liste vide si aucune). */
  imageIdsOf(fieldName: string): string[] {
    return this.page?.imageValues?.[fieldName] ?? [];
  }

  /** Helper — résout l'ID d'une page liée en son titre (pour affichage dans les chips). */
  titleOfRelated(pageId: string): string {
    return this.allPages.find(p => p.id === pageId)?.title ?? '(page supprimée)';
  }

  editMode(): void {
    this.router.navigate(['/lore', this.loreId, 'pages', this.pageId, 'edit']);
  }

  /**
   * Suppression simple : pas d'enfants. On remonte au dossier parent
   * si on peut, sinon à la racine du Lore.
   */
  deletePage(): void {
    if (!this.page) return;
    const page = this.page;
    if (!confirm(`Supprimer la page "${page.title}" ?\n\nCette action est irréversible.`)) return;
    this.pageService.delete(page.id!).subscribe({
      next: () => {
        if (page.nodeId) {
          this.router.navigate(['/lore', this.loreId, 'folders', page.nodeId]);
        } else {
          this.router.navigate(['/lore', this.loreId]);
        }
      },
      error: () => console.error('Erreur lors de la suppression de la page')
    });
  }

  ngOnDestroy(): void {
    this.layoutService.hide();
  }
}
