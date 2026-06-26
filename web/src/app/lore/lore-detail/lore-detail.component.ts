import { Component, OnInit, OnDestroy } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { LucideAngularModule, Folder, Plus, Pencil, Trash2, Network } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LoreService } from '../../services/lore.service';
import { TemplateService } from '../../services/template.service';
import { PageService } from '../../services/page.service';
import { LayoutService } from '../../services/layout.service';
import { PageTitleService } from '../../services/page-title.service';
import { Lore, LoreNode } from '../../services/lore.model';
import { loadLoreSidebarData, buildLoreSidebarConfig } from '../lore-sidebar.helper';
import { byOrder } from '../../shared/folder-grouping.util';
import { ConfirmDialogService } from '../../shared/confirm-dialog/confirm-dialog.service';

@Component({
    selector: 'app-lore-detail',
    imports: [FormsModule, LucideAngularModule, TranslatePipe],
    templateUrl: './lore-detail.component.html',
    styleUrls: ['./lore-detail.component.scss']
})
export class LoreDetailComponent implements OnInit, OnDestroy {
  readonly Folder = Folder;
  readonly Plus = Plus;
  readonly Pencil = Pencil;
  readonly Trash2 = Trash2;
  readonly Network = Network;

  lore: Lore | null = null;
  /** Tous les dossiers du Lore (racines + enfants). */
  allNodes: LoreNode[] = [];
  /** Uniquement les dossiers racine — seuls affichés dans la grille principale. */
  rootNodes: LoreNode[] = [];

  /** Mode édition inline du header (nom + description). */
  editing = false;
  editName = '';
  editDescription = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private loreService: LoreService,
    private templateService: TemplateService,
    private pageService: PageService,
    private layoutService: LayoutService,
    private pageTitleService: PageTitleService,
    private confirmDialog: ConfirmDialogService,
    private translate: TranslateService
  ) {}

  ngOnInit(): void {
    // On s'abonne à paramMap (pas snapshot) pour recharger quand on switche
    // d'un Lore à l'autre via la liste globale de la sidebar — Angular réutilise
    // le même composant et ngOnInit ne se relance pas tout seul.
    this.route.paramMap.subscribe(pm => {
      const id = pm.get('id');
      if (id && id !== this.lore?.id) {
        this.load(id);
      }
    });
  }

  private load(id: string): void {
    loadLoreSidebarData(id, this.loreService, this.templateService, this.pageService).subscribe(data => {
      this.lore = data.lore;
      this.allNodes = data.nodes;
      // Bug d'affichage corrigé : on ne liste ici que les dossiers racine
      // (les sous-dossiers apparaissent dans l'arbre de la sidebar quand on
      // ouvre leur parent). parentId null OU chaîne vide = racine.
      this.rootNodes = data.nodes.filter(n => !n.parentId).sort(byOrder); // même ordre que l'arbre
      this.layoutService.show(buildLoreSidebarConfig(data));
      this.pageTitleService.set(data.lore.name);
      // On sort du mode édition si on change de Lore en cours d'édition.
      this.editing = false;
    });
  }

  navigateToCreateNode(): void {
    this.router.navigate(['/lore', this.lore!.id, 'nodes', 'create']);
  }

  navigateToFolder(nodeId: string): void {
    this.router.navigate(['/lore', this.lore!.id, 'folders', nodeId]);
  }

  /** Ouvre la vue graphe : pages du Lore + PNJ liés, reliés par leurs liens. */
  openGraph(): void {
    if (this.lore?.id) {
      this.router.navigate(['/lore', this.lore.id, 'graph']);
    }
  }

  // ─────────────── Édition / suppression du Lore ───────────────

  startEdit(): void {
    if (!this.lore) return;
    this.editName = this.lore.name;
    this.editDescription = this.lore.description ?? '';
    this.editing = true;
  }

  cancelEdit(): void {
    this.editing = false;
  }

  saveEdit(): void {
    if (!this.lore || !this.editName.trim()) return;
    this.loreService.updateLore(this.lore.id!, {
      name: this.editName.trim(),
      description: this.editDescription
    }).subscribe({
      next: (updated) => {
        this.lore = updated;
        this.editing = false;
        // Recharge la sidebar pour que le titre soit à jour.
        this.load(updated.id!);
      },
      error: () => console.error('Erreur lors de la mise à jour du Lore')
    });
  }

  /**
   * Suppression en cascade : récupère le détail de ce qui tombera (dossiers,
   * pages, templates) et de ce qui sera détaché (campagnes conservées mais
   * sans lien vers ce Lore), affiche le récapitulatif dans la confirmation,
   * puis délègue au backend (transaction atomique).
   */
  deleteLore(): void {
    if (!this.lore) return;
    const lore = this.lore;
    this.loreService.getLoreDeletionImpact(lore.id!).subscribe({
      next: impact => {
        const deleted: string[] = [];
        if (impact.folders > 0) deleted.push(this.translate.instant(
          impact.folders > 1 ? 'loreDetail.impact.foldersPlural' : 'loreDetail.impact.folders',
          { n: impact.folders }));
        if (impact.pages > 0) deleted.push(this.translate.instant(
          impact.pages > 1 ? 'loreDetail.impact.pagesPlural' : 'loreDetail.impact.pages',
          { n: impact.pages }));
        if (impact.templates > 0) deleted.push(this.translate.instant(
          impact.templates > 1 ? 'loreDetail.impact.templatesPlural' : 'loreDetail.impact.templates',
          { n: impact.templates }));

        const details: string[] = [];
        if (deleted.length) {
          details.push(this.translate.instant('loreDetail.impact.alsoDeletes', { items: deleted.join(', ') }));
        }
        if (impact.detachedCampaigns > 0) {
          details.push(this.translate.instant(
            impact.detachedCampaigns > 1 ? 'loreDetail.impact.detachedCampaignsPlural' : 'loreDetail.impact.detachedCampaigns',
            { n: impact.detachedCampaigns }));
        }
        details.push(this.translate.instant('loreDetail.impact.irreversible'));

        this.confirmDialog.confirm({
          title: this.translate.instant('loreDetail.deleteConfirmTitle'),
          message: this.translate.instant('loreDetail.deleteConfirmMessage', { name: lore.name }),
          details,
          confirmLabel: this.translate.instant('common.delete'),
          variant: 'danger'
        }).then(ok => {
          if (!ok) return;
          this.loreService.deleteLore(lore.id!).subscribe({
            next: () => this.router.navigate(['/lore']),
            error: () => console.error('Erreur lors de la suppression du Lore')
          });
        });
      },
      error: () => console.error('Impossible de récupérer les dépendances du Lore')
    });
  }

  ngOnDestroy(): void {
    this.layoutService.hide();
  }
}
