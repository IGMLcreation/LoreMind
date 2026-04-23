import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { LucideAngularModule, LucideIconData } from 'lucide-angular';
import { LoreService } from '../../services/lore.service';
import { TemplateService } from '../../services/template.service';
import { PageService } from '../../services/page.service';
import { LayoutService } from '../../services/layout.service';
import { LoreNode } from '../../services/lore.model';
import { loadLoreSidebarData, buildLoreSidebarConfig } from '../lore-sidebar.helper';
import { popReturnTo } from '../return-stack.helper';
import { LORE_ICON_OPTIONS, IconOption, resolveIcon } from '../lore-icons';

@Component({
  selector: 'app-lore-node-create',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, LucideAngularModule],
  templateUrl: './lore-node-create.component.html',
  styleUrls: ['./lore-node-create.component.scss']
})
export class LoreNodeCreateComponent implements OnInit, OnDestroy {

  readonly iconOptions: IconOption[] = LORE_ICON_OPTIONS;

  form: FormGroup;
  loreId = '';
  /** parentId optionnel depuis la route — si présent, pré-remplit le champ. */
  preselectedParentId: string | null = null;
  /** Liste des dossiers existants pour le select "Dossier parent". */
  availableParents: LoreNode[] = [];
  selectedIcon = this.iconOptions[0].key;

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
      name:        ['', Validators.required],
      description: [''],
      parentId:    ['']   // '' = racine
    });
  }

  ngOnInit(): void {
    this.loreId = this.route.snapshot.paramMap.get('loreId')!;
    this.preselectedParentId = this.route.snapshot.paramMap.get('parentId');
    this.loadLayout();
  }

  private loadLayout(): void {
    loadLoreSidebarData(this.loreId, this.loreService, this.templateService, this.pageService)
      .subscribe(data => {
        this.availableParents = data.nodes;
        if (this.preselectedParentId) {
          this.form.patchValue({ parentId: this.preselectedParentId });
        }
        this.layoutService.show(buildLoreSidebarConfig(data));
      });
  }

  selectIcon(key: string): void {
    this.selectedIcon = key;
  }

  getIcon(key: string): LucideIconData {
    return this.iconOptions.find(o => o.key === key)!.icon;
  }

  submit(): void {
    if (this.form.invalid) return;
    const raw = this.form.value;
    this.loreService.createLoreNode({
      name: raw.name,
      description: raw.description,
      icon: this.selectedIcon,
      parentId: raw.parentId && raw.parentId !== '' ? raw.parentId : null,
      loreId: this.loreId
    }).subscribe({
      next: () => this.navigateBack(),
      error: () => console.error('Erreur lors de la création du dossier')
    });
  }

  cancel(): void {
    this.navigateBack();
  }

  /**
   * Redirige vers l'écran d'origine en dépilant le premier élément du query-param
   * `returnTo` (pile séparée par des virgules). Supporte `page-create` et
   * `template-create`, en transmettant le reste de la pile à l'écran suivant.
   */
  private navigateBack(): void {
    const { next, rest } = popReturnTo(this.route.snapshot.queryParamMap.get('returnTo'));
    const qp = rest ? { returnTo: rest } : {};
    if (next === 'page-create') {
      this.router.navigate(['/lore', this.loreId, 'pages', 'create'], { queryParams: qp });
      return;
    }
    if (next === 'template-create') {
      this.router.navigate(['/lore', this.loreId, 'templates', 'create'], { queryParams: qp });
      return;
    }
    this.router.navigate(['/lore', this.loreId]);
  }

  ngOnDestroy(): void {
    this.layoutService.hide();
  }
}
