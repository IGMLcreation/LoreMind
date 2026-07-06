import { Component, OnInit, DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { LucideAngularModule, Sparkles, Plus, Trash2 } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LoreService } from '../../services/lore.service';
import { TemplateService } from '../../services/template.service';
import { PageService } from '../../services/page.service';
import { LayoutService } from '../../services/layout.service';
import { PageTitleService } from '../../services/page-title.service';
import { LoreNode } from '../../services/lore.model';
import { Template } from '../../services/template.model';
import { Page, ImageFraming } from '../../services/page.model';
import { loadLoreSidebarData, buildLoreSidebarConfig } from '../lore-sidebar.helper';
import { hasBlockLayout, orderedBlocks, blockGridColumn, blockKey } from '../block-layout.helper';
import { TemplateField } from '../../services/template.model';
import { ChipsInputComponent } from '../../shared/chips-input/chips-input.component';
import { LoreLinkPickerComponent } from '../../shared/lore-link-picker/lore-link-picker.component';
import { BreadcrumbComponent, BreadcrumbItem } from '../../shared/breadcrumb/breadcrumb.component';
import { AiChatDrawerComponent, ChatPrimaryAction } from '../../shared/ai-chat-drawer/ai-chat-drawer.component';
import { ImageBlockComponent } from '../../shared/image-block/image-block.component';
import { Lore } from '../../services/lore.model';
import { ConfirmDialogService } from '../../shared/confirm-dialog/confirm-dialog.service';

/**
 * Écran d'édition d'une Page.
 *
 * Fonctionnalités actuelles (Phase 5A + 5B) :
 *  - Titre (modifiable) + Dossier (déplaçable)
 *  - Champs dynamiques du Template (un textarea par champ, valeurs stockées dans `values`)
 *  - Tags (chips) — Phase 5B
 *  - Liens vers d'autres pages (autocomplete) — Phase 5B
 *  - Notes privées MJ
 *
 * À venir (Phase 5D) :
 *  - Bouton "Assistant IA" branché (Phase 3 Python)
 */
@Component({
    selector: 'app-page-edit',
    imports: [FormsModule, RouterLink, LucideAngularModule, TranslatePipe, ChipsInputComponent, LoreLinkPickerComponent, BreadcrumbComponent, AiChatDrawerComponent, ImageBlockComponent],
    templateUrl: './page-edit.component.html',
    styleUrls: ['./page-edit.component.scss']
})
export class PageEditComponent implements OnInit {
  readonly Sparkles = Sparkles;
  readonly Plus = Plus;
  readonly Trash2 = Trash2;

  loreId = '';
  pageId = '';
  lore: Lore | null = null;
  page: Page | null = null;
  template: Template | null = null;
  nodes: LoreNode[] = [];
  /** Toutes les pages du lore — nécessaire au lore-link-picker pour l'autocomplete. */
  allPages: Page[] = [];

  /** Blocs du template ordonnes pour le rendu (tries par grille si placee). */
  orderedFields: TemplateField[] = [];
  /** True si le template porte une mise en page grille -> saisie en grille 12 col. */
  hasLayout = false;

  /** Placement horizontal d'un bloc (colonnes) ; hauteur naturelle en édition. */
  readonly gridColumn = blockGridColumn;
  /** Clé stable d'ancrage des valeurs (id, repli sur le nom). */
  readonly keyOf = blockKey;

  /** Modèle du formulaire (bindé via ngModel). */
  title = '';
  nodeId = '';
  notes = '';
  /** Valeurs des champs dynamiques TEXT, indexées par fieldName. */
  values: Record<string, string> = {};
  /**
   * Valeurs des champs dynamiques IMAGE : pour chaque nom de champ IMAGE,
   * la liste ordonnee des IDs d'images uploadees.
   */
  imageValues: Record<string, string[]> = {};
  /** Cadrage (pan/zoom) des images : fieldKey → imageId → {x, y, scale}. */
  imageFraming: Record<string, Record<string, ImageFraming>> = {};
  /** Valeurs des champs KEY_VALUE_LIST (liste clé/valeur) : fieldName → (label → valeur). */
  keyValueValues: Record<string, Record<string, string>> = {};
  /** Valeurs des champs TABLE : fieldName → lignes (colonne → cellule). */
  tableValues: Record<string, Record<string, string>[]> = {};
  /** Étiquettes libres (Phase 5B). */
  tags: string[] = [];
  /** IDs des pages liées (Phase 5B). */
  relatedPageIds: string[] = [];

  /** Phase 5D — état de l'Assistant IA (one-shot). */
  aiLoading = false;
  aiError: string | null = null;

  /** Phase b5 — drawer chat IA (conversationnel). */
  chatOpen = false;
  /** Action primaire dans le chat : déclenche le one-shot b4 (remplissage automatique). */
  readonly chatPrimaryAction: ChatPrimaryAction;
  /** Suggestions rapides hardcodées (MVP). */
  readonly chatQuickSuggestions: string[];

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
    private destroyRef: DestroyRef
  ) {
    this.chatPrimaryAction = { label: this.translate.instant('pageEdit.chatPrimaryAction') };
    this.chatQuickSuggestions = [
      this.translate.instant('pageEdit.chatSuggestion1'),
      this.translate.instant('pageEdit.chatSuggestion2'),
      this.translate.instant('pageEdit.chatSuggestion3')
    ];
  }

  ngOnInit(): void {
    this.loreId = this.route.snapshot.paramMap.get('loreId')!;

    // S'abonner à paramMap plutôt que de lire snapshot une fois : sinon, quand on
    // navigue d'une page à une autre (ex. via les chips du lore-link-picker),
    // Angular réutilise le composant et ngOnInit ne se relance pas → l'écran
    // resterait figé sur l'ancienne page.
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(pm => {
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
      this.layoutService.show(buildLoreSidebarConfig(sidebar));
      this.hydrate(page, sidebar.templates);
    });
  }

  /**
   * Construit le fil d'Ariane : Lore > [dossiers parents...] > Dossier courant > Page.
   * Les items sont cliquables sauf le dernier (position courante).
   * On remonte la hiérarchie via `parentId` jusqu'à la racine, puis on inverse.
   */
  get breadcrumbItems(): BreadcrumbItem[] {
    if (!this.lore || !this.page) return [];

    const items: BreadcrumbItem[] = [
      { label: this.lore.name, route: ['/lore', this.loreId] }
    ];

    // Chemin des dossiers (racine → dossier courant) via remontée parentId.
    const folderChain: LoreNode[] = [];
    let currentNode = this.nodes.find(n => n.id === this.nodeId);
    while (currentNode) {
      folderChain.unshift(currentNode);
      currentNode = currentNode.parentId
        ? this.nodes.find(n => n.id === currentNode!.parentId)
        : undefined;
    }

    for (const node of folderChain) {
      items.push({
        label: node.name,
        route: ['/lore', this.loreId, 'folders', node.id]
      });
    }

    // Position courante : la page (non-cliquable).
    items.push({ label: this.title || this.page.title });
    return items;
  }

  private hydrate(page: Page, templates: Template[]): void {
    this.page = page;
    this.template = templates.find(t => t.id === page.templateId) ?? null;
    this.orderedFields = orderedBlocks(this.template?.fields);
    this.hasLayout = hasBlockLayout(this.template?.fields);
    this.title = page.title;
    this.nodeId = page.nodeId;
    this.notes = page.notes ?? '';
    // On initialise une entrée pour chaque field TEXT du template, même vide,
    // pour que le formulaire ait toujours les champs attendus.
    // Les champs IMAGE ne sont pas geres dans `values` (ils auront leur propre
    // structure `imageValues: Map<String, List<String>>` a l'etape 5).
    const base: Record<string, string> = {};
    const imageBase: Record<string, string[]> = {};
    const framingBase: Record<string, Record<string, ImageFraming>> = {};
    const kvBase: Record<string, Record<string, string>> = {};
    const tableBase: Record<string, Record<string, string>[]> = {};
    // Les valeurs sont rangées par clé STABLE (id) ; on relit d'abord par clé,
    // puis par nom (pages dont les valeurs étaient encore rangées par nom — elles
    // sont ainsi migrées vers la clé id à la prochaine sauvegarde).
    for (const f of this.template?.fields ?? []) {
      const key = blockKey(f);
      if (f.type === 'TEXT') {
        base[key] = page.values?.[key] ?? page.values?.[f.name] ?? '';
      } else if (f.type === 'IMAGE') {
        // Initialise la galerie d'images pour ce champ (vide si jamais rempli).
        imageBase[key] = [...(page.imageValues?.[key] ?? page.imageValues?.[f.name] ?? [])];
        framingBase[key] = { ...(page.imageFraming?.[key] ?? page.imageFraming?.[f.name] ?? {}) };
      } else if (f.type === 'KEY_VALUE_LIST') {
        // Toujours initialiser l'objet interne : le ngModel du formulaire
        // bind directement keyValueValues[keyOf(field)][label].
        kvBase[key] = { ...(page.keyValueValues?.[key] ?? page.keyValueValues?.[f.name] ?? {}) };
      } else if (f.type === 'TABLE') {
        // Copie profonde des lignes : chaque ligne est éditée par ngModel.
        tableBase[key] = (page.tableValues?.[key] ?? page.tableValues?.[f.name] ?? []).map(row => ({ ...row }));
      }
    }
    this.values = base;
    this.imageValues = imageBase;
    this.imageFraming = framingBase;
    this.keyValueValues = kvBase;
    this.tableValues = tableBase;
    this.tags = [...(page.tags ?? [])];
    this.relatedPageIds = [...(page.relatedPageIds ?? [])];
    this.pageTitleService.set(page.title);
  }

  save(): void {
    if (!this.page || !this.title.trim()) return;
    const updated: Page = {
      ...this.page,
      title: this.title,
      nodeId: this.nodeId,
      notes: this.notes,
      values: this.values,
      imageValues: this.imageValues,
      imageFraming: this.imageFraming,
      keyValueValues: this.keyValueValues,
      tableValues: this.tableValues,
      tags: this.tags,
      relatedPageIds: this.relatedPageIds
    };
    this.pageService.update(this.pageId, updated).subscribe({
      next: () => this.router.navigate(['/lore', this.loreId, 'pages', this.pageId]),
      error: () => console.error('Erreur lors de la sauvegarde de la page')
    });
  }

  // --- Champs TABLE (lignes libres) ---------------------------------------
  // Mutation en place des lignes : recréer le tableau à chaque frappe ferait
  // perdre le focus de la cellule en cours d'édition.

  addTableRow(fieldName: string, columns: string[] | null | undefined): void {
    const row: Record<string, string> = {};
    for (const col of columns ?? []) row[col] = '';
    const rows = this.tableValues[fieldName] ?? [];
    rows.push(row);
    this.tableValues[fieldName] = rows;
  }

  removeTableRow(fieldName: string, rowIndex: number): void {
    this.tableValues[fieldName]?.splice(rowIndex, 1);
  }

  /** Lignes du tableau d'un champ — toujours un tableau (jamais undefined) pour le `@for`. */
  tableRows(fieldName: string): Record<string, string>[] {
    return this.tableValues[fieldName] ?? [];
  }

  // --- Chat IA conversationnel (Phase b5) --------------------------------

  toggleChat(): void {
    this.chatOpen = !this.chatOpen;
  }

  /** Appelé depuis le drawer quand l'utilisateur clique sur l'action primaire. */
  onChatFillRequested(): void {
    this.chatOpen = false;    // on ferme le drawer : le résultat apparaîtra dans les textareas
    this.runAssistantAI();
  }

  /**
   * Assistant IA (Phase 5D) — demande au Brain des suggestions de valeurs
   * pour les champs dynamiques du template.
   *
   * Merge soft : on n'écrase pas une valeur déjà saisie par l'utilisateur
   * si la suggestion est vide. L'utilisateur garde le contrôle final avant
   * de cliquer "Sauvegarder".
   */
  runAssistantAI(): void {
    if (this.aiLoading || !this.template?.fields?.length) return;
    this.aiLoading = true;
    this.aiError = null;
    this.pageService.generateValues(this.pageId).subscribe({
      next: (suggestions) => {
        this.mergeSuggestions(suggestions);
        this.aiLoading = false;
      },
      error: (err) => {
        this.aiLoading = false;
        this.aiError = err?.status === 502
          ? this.translate.instant('pageEdit.aiUnreachable')
          : this.translate.instant('pageEdit.aiFailed');
      }
    });
  }

  /**
   * Fusionne les suggestions dans les valeurs courantes.
   * Merge soft :
   *  - Suggestion non-vide → on applique (l'utilisateur a demandé la génération).
   *  - Suggestion vide      → on NE touche PAS à la valeur courante (l'IA n'a rien à proposer pour ce champ).
   */
  private mergeSuggestions(suggestions: Record<string, string>): void {
    // L'IA ne genere que des valeurs texte : on ignore les champs IMAGE.
    // L'IA renvoie ses suggestions par NOM de champ (le prompt liste les noms) ;
    // on les range sous la clé STABLE (id) attendue par le formulaire.
    for (const field of this.template?.fields ?? []) {
      if (field.type !== 'TEXT') continue;
      const suggestion = suggestions[field.name];
      if (suggestion && suggestion.trim()) {
        this.values[blockKey(field)] = suggestion;
      }
    }
  }

  delete(): void {
    if (!this.page) return;
    this.confirmDialog.confirm({
      title: this.translate.instant('pageEdit.deleteTitle'),
      message: this.translate.instant('pageEdit.deleteMessage', { title: this.page.title }),
      confirmLabel: this.translate.instant('common.delete'),
      variant: 'danger'
    }).then(ok => {
      if (!ok || !this.page) return;
      this.pageService.delete(this.pageId).subscribe({
        next: () => this.router.navigate(['/lore', this.loreId]),
        error: () => console.error('Erreur lors de la suppression de la page')
      });
    });
  }
}
