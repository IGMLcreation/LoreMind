import { Component, OnInit, OnDestroy } from '@angular/core';

import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { LucideAngularModule, Plus, Trash2, Type, Image as ImageIcon, ChevronUp, ChevronDown, ListOrdered, Table as TableIcon, X } from 'lucide-angular';
import { TranslatePipe } from '@ngx-translate/core';
import { LoreService } from '../../services/lore.service';
import { TemplateService } from '../../services/template.service';
import { PageService } from '../../services/page.service';
import { LayoutService } from '../../services/layout.service';
import { LoreNode } from '../../services/lore.model';
import { FieldType, ImageLayout, TemplateField, buildLoreTemplateField, cleanFieldLabels } from '../../services/template.model';
import { loadLoreSidebarData, buildLoreSidebarConfig } from '../lore-sidebar.helper';
import { popReturnTo } from '../return-stack.helper';

/**
 * Écran de création d'un Template (gabarit de Page).
 * - Champs principaux : nom, description, noeud par défaut.
 * - Liste dynamique de "champs du template" (ex: "Nom", "Description", "Personnalité").
 *   Le user peut ajouter/retirer n'importe lequel — tous sont égaux.
 */
@Component({
    selector: 'app-template-create',
    imports: [FormsModule, ReactiveFormsModule, RouterModule, LucideAngularModule, TranslatePipe],
    templateUrl: './template-create.component.html',
    styleUrls: ['./template-create.component.scss']
})
export class TemplateCreateComponent implements OnInit, OnDestroy {
  readonly Plus = Plus;
  readonly Trash2 = Trash2;
  readonly Type = Type;
  readonly ImageIcon = ImageIcon;
  readonly ChevronUp = ChevronUp;
  readonly ChevronDown = ChevronDown;
  readonly ListOrdered = ListOrdered;
  readonly TableIcon = TableIcon;
  readonly X = X;

  /** Icone du chip selon le type du champ. */
  iconFor(type: FieldType) {
    switch (type) {
      case 'IMAGE': return this.ImageIcon;
      case 'KEY_VALUE_LIST': return this.ListOrdered;
      case 'TABLE': return this.TableIcon;
      default: return this.Type;
    }
  }

  form: FormGroup;
  loreId = '';
  nodes: LoreNode[] = [];
  /**
   * Champs dynamiques actuellement definis. Chaque champ a un type discriminant
   * (TEXT ou IMAGE) qui pilote son rendu sur les pages.
   */
  fields: TemplateField[] = [
    { name: 'Nom', type: 'TEXT' },
    { name: 'Description', type: 'TEXT' },
    { name: 'Illustration', type: 'IMAGE', layout: 'GALLERY' }
  ];
  /** Valeur courante de l'input d'ajout de champ (non binding direct pour reset facile). */
  newFieldName = '';
  /** Type choisi pour le prochain champ a ajouter. */
  newFieldType: FieldType = 'TEXT';

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private loreService: LoreService,
    private templateService: TemplateService,
    private pageService: PageService,
    private layoutService: LayoutService
  ) {
    this.form = this.fb.group({
      name:          ['', Validators.required],
      description:   [''],
      defaultNodeId: ['', Validators.required]
    });
  }

  ngOnInit(): void {
    this.loreId = this.route.snapshot.paramMap.get('loreId')!;
    loadLoreSidebarData(this.loreId, this.loreService, this.templateService, this.pageService).subscribe(data => {
      this.nodes = data.nodes;
      this.layoutService.show(buildLoreSidebarConfig(data));
      this.restoreDraft();
    });
  }

  /** Clé sessionStorage pour le brouillon de template — scopée au lore. */
  private get draftKey(): string {
    return `template-create-draft:${this.loreId}`;
  }

  /**
   * Sauvegarde le formulaire courant avant un détour (création de dossier).
   * defaultNodeId volontairement omis : il référence potentiellement un dossier
   * qui n'existe pas encore.
   */
  saveDraft(): void {
    const draft = {
      name: this.form.value.name ?? '',
      description: this.form.value.description ?? '',
      fields: this.fields
    };
    try {
      sessionStorage.setItem(this.draftKey, JSON.stringify(draft));
    } catch { /* storage indisponible : on ignore */ }
  }

  private restoreDraft(): void {
    let raw: string | null = null;
    try { raw = sessionStorage.getItem(this.draftKey); } catch { return; }
    if (!raw) return;
    sessionStorage.removeItem(this.draftKey);
    try {
      const draft = JSON.parse(raw) as { name?: string; description?: string; fields?: TemplateField[] };
      if (draft.name) this.form.patchValue({ name: draft.name });
      if (draft.description) this.form.patchValue({ description: draft.description });
      if (Array.isArray(draft.fields) && draft.fields.length) this.fields = draft.fields;
    } catch { /* JSON corrompu : on ignore */ }
  }

  /**
   * Construit le `returnTo` à passer à l'écran de création de dossier :
   * on empile 'template-create' par-dessus la pile courante, pour que node-create
   * revienne ici puis remonte à l'écran d'origine le cas échéant.
   */
  get nodeCreateReturnTo(): string {
    const current = this.route.snapshot.queryParamMap.get('returnTo');
    return current ? `template-create,${current}` : 'template-create';
  }

  addField(): void {
    const name = this.newFieldName.trim();
    if (!name) return;
    // Unicite par nom (on ignore le type pour eviter des collisions d'affichage).
    if (this.fields.some(f => f.name === name)) return;
    this.fields = [...this.fields, buildLoreTemplateField(name, this.newFieldType)];
    this.newFieldName = '';
    // Le type reste sur la derniere valeur choisie : pratique pour enchainer
    // plusieurs champs du meme type.
  }

  removeField(index: number): void {
    this.fields = this.fields.filter((_, i) => i !== index);
  }

  /** Deplace un champ d'un cran vers le haut ou le bas. No-op aux bords. */
  moveField(index: number, direction: -1 | 1): void {
    const target = index + direction;
    if (target < 0 || target >= this.fields.length) return;
    const next = [...this.fields];
    [next[index], next[target]] = [next[target], next[index]];
    this.fields = next;
  }

  /** Change le type d'un champ existant (TEXT / IMAGE / KEY_VALUE_LIST). */
  setFieldType(index: number, type: FieldType): void {
    this.fields = this.fields.map((f, i) =>
      i === index ? buildLoreTemplateField(f.name, type, f) : f
    );
  }

  /** Met a jour le layout d'un champ IMAGE. */
  setFieldLayout(index: number, layout: ImageLayout): void {
    this.fields = this.fields.map((f, i) =>
      i === index && f.type === 'IMAGE' ? { ...f, layout } : f
    );
  }

  // --- Sous-editeur des libelles (KEY_VALUE_LIST) -------------------------
  // Mutation en place des labels : recreer le tableau de fields a chaque
  // frappe ferait perdre le focus de l'input en cours d'edition.

  addLabel(field: TemplateField): void {
    field.labels = [...(field.labels ?? []), ''];
  }

  updateLabel(field: TemplateField, labelIndex: number, value: string): void {
    if (!field.labels) return;
    field.labels[labelIndex] = value;
  }

  removeLabel(field: TemplateField, labelIndex: number): void {
    if (!field.labels) return;
    field.labels = field.labels.filter((_, i) => i !== labelIndex);
  }

  submit(): void {
    if (this.form.invalid) return;
    const raw = this.form.value;
    this.templateService.create({
      loreId: this.loreId,
      name: raw.name,
      description: raw.description,
      defaultNodeId: raw.defaultNodeId,
      fields: cleanFieldLabels(this.fields)
    }).subscribe({
      next: (created) => this.navigateBack(created.id ?? null),
      error: () => console.error('Erreur lors de la création du template')
    });
  }

  cancel(): void {
    this.navigateBack(null);
  }

  /**
   * Redirige vers l'écran d'origine en dépilant le premier élément du query-param
   * `returnTo` (pile de retours séparés par des virgules, ex : `page-create` ou
   * `template-create,page-create`). Sinon retombe sur la page détail du Lore.
   *
   * Si `createdTemplateId` est fourni (cas submit), on l'embarque dans le
   * query-param `selectTemplateId` pour que page-create puisse pre-selectionner
   * le template fraichement cree.
   */
  private navigateBack(createdTemplateId: string | null): void {
    const { next, rest } = popReturnTo(this.route.snapshot.queryParamMap.get('returnTo'));
    if (next === 'page-create') {
      const queryParams: Record<string, string> = {};
      if (rest) queryParams['returnTo'] = rest;
      if (createdTemplateId) queryParams['selectTemplateId'] = createdTemplateId;
      this.router.navigate(['/lore', this.loreId, 'pages', 'create'], { queryParams });
      return;
    }
    this.router.navigate(['/lore', this.loreId]);
  }

  ngOnDestroy(): void {
    // Volontairement vide : la sidebar reste prise en charge par le composant
    // suivant (autre sous-route ou le composant detail parent) qui appellera
    // show(). Eviter d'appeler hide() ici previent le clignotement / la
    // disparition de la sidebar lors des navigations internes a la section.
  }
}
