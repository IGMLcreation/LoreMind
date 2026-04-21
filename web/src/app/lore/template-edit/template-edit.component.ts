import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { LucideAngularModule, Plus, X, Trash2, Type, Image as ImageIcon } from 'lucide-angular';
import { LoreService } from '../../services/lore.service';
import { TemplateService } from '../../services/template.service';
import { PageService } from '../../services/page.service';
import { LayoutService } from '../../services/layout.service';
import { PageTitleService } from '../../services/page-title.service';
import { LoreNode } from '../../services/lore.model';
import { FieldType, Template, TemplateField } from '../../services/template.model';
import { loadLoreSidebarData, buildLoreSidebarConfig } from '../lore-sidebar.helper';

/**
 * Écran d'édition d'un Template existant.
 * Mêmes champs que la création + bouton Supprimer.
 */
@Component({
  selector: 'app-template-edit',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, LucideAngularModule],
  templateUrl: './template-edit.component.html',
  styleUrls: ['./template-edit.component.scss']
})
export class TemplateEditComponent implements OnInit, OnDestroy {
  readonly Plus = Plus;
  readonly X = X;
  readonly Trash2 = Trash2;
  readonly Type = Type;
  readonly ImageIcon = ImageIcon;

  form: FormGroup;
  loreId = '';
  templateId = '';
  template: Template | null = null;
  nodes: LoreNode[] = [];
  fields: TemplateField[] = [];
  newFieldName = '';
  newFieldType: FieldType = 'TEXT';

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
      name:          ['', Validators.required],
      description:   [''],
      defaultNodeId: ['']
    });
  }

  ngOnInit(): void {
    this.loreId = this.route.snapshot.paramMap.get('loreId')!;
    this.templateId = this.route.snapshot.paramMap.get('templateId')!;

    forkJoin({
      sidebar: loadLoreSidebarData(this.loreId, this.loreService, this.templateService, this.pageService),
      template: this.templateService.getById(this.templateId)
    }).subscribe(({ sidebar, template }) => {
      this.nodes = sidebar.nodes;
      this.layoutService.show(buildLoreSidebarConfig(sidebar));
      this.hydrate(template);
    });
  }

  private hydrate(template: Template): void {
    this.template = template;
    // Copie defensive + normalisation du type (defaut TEXT si inconnu/manquant,
    // utile pour les templates legacy cote frontend meme si le backend le fait aussi).
    this.fields = (template.fields ?? []).map(f => ({
      name: f.name,
      type: f.type === 'IMAGE' ? 'IMAGE' : 'TEXT'
    }));
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
    this.fields = [...this.fields, { name, type: this.newFieldType }];
    this.newFieldName = '';
  }

  removeField(index: number): void {
    this.fields = this.fields.filter((_, i) => i !== index);
  }

  /** Bascule le type d'un champ (TEXT <-> IMAGE). */
  toggleFieldType(index: number): void {
    const field = this.fields[index];
    if (!field) return;
    const nextType: FieldType = field.type === 'TEXT' ? 'IMAGE' : 'TEXT';
    this.fields = this.fields.map((f, i) => i === index ? { ...f, type: nextType } : f);
  }

  save(): void {
    if (this.form.invalid || !this.template) return;
    const raw = this.form.value;
    this.templateService.update(this.templateId, {
      ...this.template,
      name: raw.name,
      description: raw.description,
      defaultNodeId: raw.defaultNodeId || null,
      fields: this.fields
    }).subscribe({
      next: () => this.router.navigate(['/lore', this.loreId]),
      error: () => console.error('Erreur lors de la sauvegarde du template')
    });
  }

  delete(): void {
    if (!confirm(`Supprimer le template "${this.template?.name}" ?`)) return;
    this.templateService.delete(this.templateId).subscribe({
      next: () => this.router.navigate(['/lore', this.loreId]),
      error: () => console.error('Erreur lors de la suppression du template')
    });
  }

  ngOnDestroy(): void {
    this.layoutService.hide();
  }
}
