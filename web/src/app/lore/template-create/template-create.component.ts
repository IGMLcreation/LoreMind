import { Component, OnInit } from '@angular/core';

import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { LoreService } from '../../services/lore.service';
import { TemplateService } from '../../services/template.service';
import { PageService } from '../../services/page.service';
import { LayoutService } from '../../services/layout.service';
import { LoreNode } from '../../services/lore.model';
import { TemplateField, cleanFieldLabels } from '../../services/template.model';
import { loadLoreSidebarData, buildLoreSidebarConfig } from '../lore-sidebar.helper';
import { BlockGridBuilderComponent } from '../block-grid-builder/block-grid-builder.component';
import { popReturnTo } from '../return-stack.helper';

/**
 * Écran de création d'un Template (gabarit de Page).
 * - Champs principaux : nom, description, noeud par défaut.
 * - Agencement des blocs délégué à {@link BlockGridBuilderComponent} (grille 12 col).
 */
@Component({
    selector: 'app-template-create',
    imports: [ReactiveFormsModule, RouterModule, TranslatePipe, BlockGridBuilderComponent],
    templateUrl: './template-create.component.html',
    styleUrls: ['./template-create.component.scss']
})
export class TemplateCreateComponent implements OnInit {
  form: FormGroup;
  loreId = '';
  nodes: LoreNode[] = [];
  /**
   * Champs dynamiques par défaut d'un nouveau template. Le builder leur affecte
   * id stable + placement (pos) à l'affichage.
   */
  fields: TemplateField[] = [
    { name: 'Nom', type: 'TEXT' },
    { name: 'Description', type: 'TEXT' },
    { name: 'Illustration', type: 'IMAGE', layout: 'GALLERY' }
  ];

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
    let raw: string | null;
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
}
