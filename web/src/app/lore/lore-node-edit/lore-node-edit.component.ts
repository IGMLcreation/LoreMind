import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { LucideAngularModule, LucideIconData } from 'lucide-angular';
import { LoreService } from '../../services/lore.service';
import { TemplateService } from '../../services/template.service';
import { PageService } from '../../services/page.service';
import { LayoutService } from '../../services/layout.service';
import { PageTitleService } from '../../services/page-title.service';
import { LoreNode } from '../../services/lore.model';
import { Page } from '../../services/page.model';
import {
  loadLoreSidebarData,
  buildLoreSidebarConfig,
  collectDescendantIds
} from '../lore-sidebar.helper';
import { LORE_ICON_OPTIONS, IconOption } from '../lore-icons';

/**
 * Écran d'édition d'un dossier (LoreNode) existant.
 *
 * Fonctionnalités :
 *  - Renommer
 *  - Changer l'icône
 *  - Déplacer dans un autre dossier parent (ou vers la racine)
 *  - Supprimer (refusé si le dossier contient des sous-dossiers ou des pages)
 *
 * Prévention des cycles : le select "Dossier parent" exclut le dossier en cours
 * d'édition ET tous ses descendants — sinon l'arbre deviendrait circulaire.
 */
@Component({
  selector: 'app-lore-node-edit',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, LucideAngularModule],
  templateUrl: './lore-node-edit.component.html',
  styleUrls: ['./lore-node-edit.component.scss']
})
export class LoreNodeEditComponent implements OnInit, OnDestroy {

  readonly iconOptions: IconOption[] = LORE_ICON_OPTIONS;

  form: FormGroup;
  loreId = '';
  folderId = '';
  node: LoreNode | null = null;
  /** Dossiers proposables comme parent (tous sauf soi-même + descendants). */
  availableParents: LoreNode[] = [];
  /** Nombre de sous-dossiers directs (pour affichage + validation de suppression). */
  childFolderCount = 0;
  /** Nombre de pages dans ce dossier (pour affichage + validation de suppression). */
  pageCount = 0;

  selectedIcon: string | null = null;

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private loreService: LoreService,
    private templateService: TemplateService,
    private pageService: PageService,
    private layoutService: LayoutService,
    private pageTitleService: PageTitleService
  ) {
    this.form = this.fb.group({
      name:     ['', Validators.required],
      parentId: ['']
    });
  }

  ngOnInit(): void {
    this.loreId = this.route.snapshot.paramMap.get('loreId')!;
    this.folderId = this.route.snapshot.paramMap.get('folderId')!;

    // Réagir aux changements de :folderId (navigation entre dossiers dans la sidebar
    // sans démonter le composant).
    this.route.paramMap.subscribe(pm => {
      const newId = pm.get('folderId')!;
      if (newId !== this.folderId) {
        this.folderId = newId;
        this.load();
      }
    });

    this.load();
  }

  private load(): void {
    forkJoin({
      sidebar: loadLoreSidebarData(this.loreId, this.loreService, this.templateService, this.pageService),
      node: this.loreService.getLoreNodeById(this.folderId)
    }).subscribe(({ sidebar, node }) => {
      this.layoutService.show(buildLoreSidebarConfig(sidebar));
      this.hydrate(node, sidebar.nodes, sidebar.pages);
    });
  }

  private hydrate(node: LoreNode, allNodes: LoreNode[], allPages: Page[]): void {
    this.node = node;
    this.selectedIcon = node.icon ?? null;
    this.form.patchValue({
      name: node.name,
      parentId: node.parentId ?? ''
    });

    // Liste des parents autorisés : tous les dossiers sauf soi + descendants.
    const excluded = collectDescendantIds(node.id!, allNodes);
    this.availableParents = allNodes.filter(n => !excluded.has(n.id!));

    // Stats pour affichage + règle de suppression.
    this.childFolderCount = allNodes.filter(n => n.parentId === node.id).length;
    this.pageCount = allPages.filter(p => p.nodeId === node.id).length;
    this.pageTitleService.set(node.name);
  }

  selectIcon(key: string): void {
    this.selectedIcon = key;
  }

  save(): void {
    if (this.form.invalid || !this.node) return;
    const raw = this.form.value;
    const updated: LoreNode = {
      ...this.node,
      name: raw.name,
      icon: this.selectedIcon,
      parentId: raw.parentId && raw.parentId !== '' ? raw.parentId : null
    };
    this.loreService.updateLoreNode(this.folderId, updated).subscribe({
      next: () => this.router.navigate(['/lore', this.loreId]),
      error: () => console.error('Erreur lors de la sauvegarde du dossier')
    });
  }

  /**
   * Suppression en cascade : on va chercher le compte exact de sous-dossiers et
   * de pages (qui tombent avec le dossier), on l'annonce dans la confirmation,
   * puis on délègue au backend — l'atomicité est garantie côté transaction.
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
          next: () => this.router.navigate(['/lore', this.loreId]),
          error: () => console.error('Erreur lors de la suppression du dossier')
        });
      },
      error: () => console.error('Impossible de récupérer les dépendances du dossier')
    });
  }

  cancel(): void {
    this.router.navigate(['/lore', this.loreId]);
  }

  /** Retourne l'icône lucide à afficher dans l'aperçu du bouton "Sauvegarder". */
  getIcon(key: string | null): LucideIconData | null {
    if (!key) return null;
    return this.iconOptions.find(o => o.key === key)?.icon ?? null;
  }

  ngOnDestroy(): void {
    this.layoutService.hide();
  }
}
