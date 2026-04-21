import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { LucideAngularModule, Sparkles } from 'lucide-angular';
import { LoreService } from '../../services/lore.service';
import { TemplateService } from '../../services/template.service';
import { PageService } from '../../services/page.service';
import { LayoutService } from '../../services/layout.service';
import { PageTitleService } from '../../services/page-title.service';
import { LoreNode } from '../../services/lore.model';
import { Template } from '../../services/template.model';
import { Page } from '../../services/page.model';
import { loadLoreSidebarData, buildLoreSidebarConfig } from '../lore-sidebar.helper';
import { ChipsInputComponent } from '../../shared/chips-input/chips-input.component';
import { LoreLinkPickerComponent } from '../../shared/lore-link-picker/lore-link-picker.component';
import { BreadcrumbComponent, BreadcrumbItem } from '../../shared/breadcrumb/breadcrumb.component';
import { AiChatDrawerComponent, ChatPrimaryAction } from '../../shared/ai-chat-drawer/ai-chat-drawer.component';
import { ImageGalleryComponent } from '../../shared/image-gallery/image-gallery.component';
import { Lore } from '../../services/lore.model';

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
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, LucideAngularModule, ChipsInputComponent, LoreLinkPickerComponent, BreadcrumbComponent, AiChatDrawerComponent, ImageGalleryComponent],
  templateUrl: './page-edit.component.html',
  styleUrls: ['./page-edit.component.scss']
})
export class PageEditComponent implements OnInit, OnDestroy {
  readonly Sparkles = Sparkles;

  loreId = '';
  pageId = '';
  lore: Lore | null = null;
  page: Page | null = null;
  template: Template | null = null;
  nodes: LoreNode[] = [];
  /** Toutes les pages du lore — nécessaire au lore-link-picker pour l'autocomplete. */
  allPages: Page[] = [];

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
  readonly chatPrimaryAction: ChatPrimaryAction = { label: 'Remplir automatiquement tous les champs' };
  /** Suggestions rapides hardcodées (MVP). */
  readonly chatQuickSuggestions: string[] = [
    "Étoffe l'histoire de cette page",
    'Suggère des liens avec d\'autres pages du Lore',
    'Propose une intrigue secondaire'
  ];

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

    // S'abonner à paramMap plutôt que de lire snapshot une fois : sinon, quand on
    // navigue d'une page à une autre (ex. via les chips du lore-link-picker),
    // Angular réutilise le composant et ngOnInit ne se relance pas → l'écran
    // resterait figé sur l'ancienne page.
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
        route: ['/lore', this.loreId, 'folders', node.id, 'edit']
      });
    }

    // Position courante : la page (non-cliquable).
    items.push({ label: this.title || this.page.title });
    return items;
  }

  private hydrate(page: Page, templates: Template[]): void {
    this.page = page;
    this.template = templates.find(t => t.id === page.templateId) ?? null;
    this.title = page.title;
    this.nodeId = page.nodeId;
    this.notes = page.notes ?? '';
    // On initialise une entrée pour chaque field TEXT du template, même vide,
    // pour que le formulaire ait toujours les champs attendus.
    // Les champs IMAGE ne sont pas geres dans `values` (ils auront leur propre
    // structure `imageValues: Map<String, List<String>>` a l'etape 5).
    const base: Record<string, string> = {};
    const imageBase: Record<string, string[]> = {};
    for (const f of this.template?.fields ?? []) {
      if (f.type === 'TEXT') {
        base[f.name] = page.values?.[f.name] ?? '';
      } else if (f.type === 'IMAGE') {
        // Initialise la galerie d'images pour ce champ (vide si jamais rempli).
        imageBase[f.name] = [...(page.imageValues?.[f.name] ?? [])];
      }
    }
    this.values = base;
    this.imageValues = imageBase;
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
      tags: this.tags,
      relatedPageIds: this.relatedPageIds
    };
    this.pageService.update(this.pageId, updated).subscribe({
      next: () => this.router.navigate(['/lore', this.loreId, 'pages', this.pageId]),
      error: () => console.error('Erreur lors de la sauvegarde de la page')
    });
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
          ? "L'assistant IA est injoignable. V\u00e9rifiez que le service Brain tourne."
          : "\u00c9chec de la g\u00e9n\u00e9ration IA. R\u00e9essayez dans un instant.";
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
    for (const field of this.template?.fields ?? []) {
      if (field.type !== 'TEXT') continue;
      const suggestion = suggestions[field.name];
      if (suggestion && suggestion.trim()) {
        this.values[field.name] = suggestion;
      }
    }
  }

  delete(): void {
    if (!this.page) return;
    if (!confirm(`Supprimer la page "${this.page.title}" ?`)) return;
    this.pageService.delete(this.pageId).subscribe({
      next: () => this.router.navigate(['/lore', this.loreId]),
      error: () => console.error('Erreur lors de la suppression de la page')
    });
  }

  ngOnDestroy(): void {
    this.layoutService.hide();
  }
}
