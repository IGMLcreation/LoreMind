import { Component, OnInit, OnDestroy } from '@angular/core';

import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin, Subject } from 'rxjs';
import { switchMap, takeUntil } from 'rxjs/operators';
import { LucideAngularModule, Plus, Trash2, Type, Image as ImageIcon, ChevronUp, ChevronDown, ListOrdered, Table as TableIcon, X } from 'lucide-angular';
import { LoreService } from '../../services/lore.service';
import { TemplateService } from '../../services/template.service';
import { PageService } from '../../services/page.service';
import { LayoutService } from '../../services/layout.service';
import { PageTitleService } from '../../services/page-title.service';
import { LoreNode } from '../../services/lore.model';
import { FieldType, ImageLayout, Template, TemplateField, buildLoreTemplateField, cleanFieldLabels } from '../../services/template.model';
import { loadLoreSidebarData, buildLoreSidebarConfig } from '../lore-sidebar.helper';
import { ConfirmDialogService } from '../../shared/confirm-dialog/confirm-dialog.service';

/**
 * Écran d'édition d'un Template existant.
 * Mêmes champs que la création + bouton Supprimer.
 */
@Component({
    selector: 'app-template-edit',
    imports: [FormsModule, ReactiveFormsModule, LucideAngularModule],
    templateUrl: './template-edit.component.html',
    styleUrls: ['./template-edit.component.scss']
})
export class TemplateEditComponent implements OnInit, OnDestroy {
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
  templateId = '';
  template: Template | null = null;
  nodes: LoreNode[] = [];
  fields: TemplateField[] = [];
  newFieldName = '';
  newFieldType: FieldType = 'TEXT';
  /**
   * Noms des champs chargés depuis le backend — utilisés pour discriminer
   * visuellement les champs existants (orange) des champs ajoutés dans cette
   * session d'édition (vert). Non muté ensuite.
   */
  private originalFieldNames = new Set<string>();

  private destroy$ = new Subject<void>();

  /** True si le champ est présent depuis le chargement du template. */
  isExistingField(field: TemplateField): boolean {
    return this.originalFieldNames.has(field.name);
  }

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private loreService: LoreService,
    private templateService: TemplateService,
    private pageService: PageService,
    private layoutService: LayoutService,
    private pageTitleService: PageTitleService,
    private confirmDialog: ConfirmDialogService
  ) {
    this.form = this.fb.group({
      name:          ['', Validators.required],
      description:   [''],
      defaultNodeId: ['']
    });
  }

  ngOnInit(): void {
    // switchMap pour annuler le chargement precedent si l'utilisateur change
    // de template avant la fin de la requete (Angular reutilise l'instance du
    // composant entre /templates/T1 et /templates/T2, donc ngOnInit ne refire
    // pas et il faut reagir aux changements de params nous-memes).
    this.route.paramMap.pipe(
      switchMap(params => {
        this.loreId = params.get('loreId')!;
        this.templateId = params.get('templateId')!;
        return forkJoin({
          sidebar: loadLoreSidebarData(this.loreId, this.loreService, this.templateService, this.pageService),
          template: this.templateService.getById(this.templateId)
        });
      }),
      takeUntil(this.destroy$)
    ).subscribe(({ sidebar, template }) => {
      this.nodes = sidebar.nodes;
      this.layoutService.show(buildLoreSidebarConfig(sidebar));
      this.hydrate(template);
    });
  }

  private hydrate(template: Template): void {
    this.template = template;
    // Copie defensive + normalisation du type (defaut TEXT si inconnu/manquant,
    // utile pour les templates legacy cote frontend meme si le backend le fait aussi).
    this.fields = (template.fields ?? []).map(f => {
      const type: FieldType =
        f.type === 'IMAGE' || f.type === 'KEY_VALUE_LIST' || f.type === 'TABLE' ? f.type : 'TEXT';
      return buildLoreTemplateField(f.name, type, f);
    });
    this.originalFieldNames = new Set(this.fields.map(f => f.name));
    this.form.patchValue({
      name: template.name,
      description: template.description,
      defaultNodeId: template.defaultNodeId ?? ''
    });
    this.pageTitleService.set(template.name);
  }

  addField(): void {
    const name = this.newFieldName.trim();
    if (!name) return;
    if (this.fields.some(f => f.name === name)) return;
    this.fields = [...this.fields, buildLoreTemplateField(name, this.newFieldType)];
    this.newFieldName = '';
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

  save(): void {
    if (this.form.invalid || !this.template) return;
    const raw = this.form.value;
    this.templateService.update(this.templateId, {
      ...this.template,
      name: raw.name,
      description: raw.description,
      defaultNodeId: raw.defaultNodeId || null,
      fields: cleanFieldLabels(this.fields)
    }).subscribe({
      next: () => this.router.navigate(['/lore', this.loreId]),
      error: () => console.error('Erreur lors de la sauvegarde du template')
    });
  }

  delete(): void {
    this.confirmDialog.confirm({
      title: 'Supprimer le template',
      message: `Supprimer le template "${this.template?.name}" ?`,
      confirmLabel: 'Supprimer',
      variant: 'danger'
    }).then(ok => {
      if (!ok) return;
      this.templateService.delete(this.templateId).subscribe({
        next: () => this.router.navigate(['/lore', this.loreId]),
        error: () => console.error('Erreur lors de la suppression du template')
      });
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    // hide() volontairement retire : la sidebar reste prise en charge par le
    // composant suivant (sous-route ou detail parent) afin d'eviter qu'elle
    // disparaisse lors des navigations internes a la section.
  }
}
