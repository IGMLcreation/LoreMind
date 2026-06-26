import { Component, OnInit, OnDestroy } from '@angular/core';

import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin, Subject } from 'rxjs';
import { switchMap, takeUntil } from 'rxjs/operators';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LoreService } from '../../services/lore.service';
import { TemplateService } from '../../services/template.service';
import { PageService } from '../../services/page.service';
import { LayoutService } from '../../services/layout.service';
import { PageTitleService } from '../../services/page-title.service';
import { LoreNode } from '../../services/lore.model';
import { FieldType, Template, TemplateField, buildLoreTemplateField, cleanFieldLabels } from '../../services/template.model';
import { loadLoreSidebarData, buildLoreSidebarConfig } from '../lore-sidebar.helper';
import { BlockGridBuilderComponent } from '../block-grid-builder/block-grid-builder.component';
import { ConfirmDialogService } from '../../shared/confirm-dialog/confirm-dialog.service';

/**
 * Écran d'édition d'un Template existant.
 * Mêmes champs que la création + bouton Supprimer.
 * L'agencement des blocs est délégué à {@link BlockGridBuilderComponent}.
 */
@Component({
    selector: 'app-template-edit',
    imports: [ReactiveFormsModule, TranslatePipe, BlockGridBuilderComponent],
    templateUrl: './template-edit.component.html',
    styleUrls: ['./template-edit.component.scss']
})
export class TemplateEditComponent implements OnInit, OnDestroy {
  form: FormGroup;
  loreId = '';
  templateId = '';
  template: Template | null = null;
  nodes: LoreNode[] = [];
  fields: TemplateField[] = [];
  /**
   * Noms des champs chargés depuis le backend — passés au builder pour
   * discriminer visuellement les champs existants des champs ajoutés dans
   * cette session d'édition. Non muté ensuite.
   */
  originalFieldNames = new Set<string>();

  private destroy$ = new Subject<void>();

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private loreService: LoreService,
    private templateService: TemplateService,
    private pageService: PageService,
    private layoutService: LayoutService,
    private pageTitleService: PageTitleService,
    private confirmDialog: ConfirmDialogService,
    private translate: TranslateService
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
    // On PRESERVE id et pos : le builder en a besoin pour recharger l'agencement.
    this.fields = (template.fields ?? []).map(f => {
      const type: FieldType =
        f.type === 'IMAGE' || f.type === 'KEY_VALUE_LIST' || f.type === 'TABLE' ? f.type : 'TEXT';
      return { ...buildLoreTemplateField(f.name, type, f), id: f.id, pos: f.pos };
    });
    this.originalFieldNames = new Set(this.fields.map(f => f.name));
    this.form.patchValue({
      name: template.name,
      description: template.description,
      defaultNodeId: template.defaultNodeId ?? ''
    });
    this.pageTitleService.set(template.name);
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
      title: this.translate.instant('templateEdit.deleteTitle'),
      message: this.translate.instant('templateEdit.deleteMessage', { name: this.template?.name }),
      confirmLabel: this.translate.instant('common.delete'),
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
