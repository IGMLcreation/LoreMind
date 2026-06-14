import { Component, OnInit } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { LucideAngularModule, ArrowLeft, Save, Plus, Trash2, Sparkles } from 'lucide-angular';
import { ItemCatalogService } from '../../../services/item-catalog.service';
import { CampaignSidebarService } from '../../../services/campaign-sidebar.service';
import { ItemCatalog, CatalogItem, ItemCatalogCreate } from '../../../services/item-catalog.model';
import { TranslatePipe } from '@ngx-translate/core';

/**
 * Création/édition d'un catalogue d'objets (boutique, butin…).
 * Routes : /campaigns/:campaignId/item-catalogs/create
 *          /campaigns/:campaignId/item-catalogs/:catalogId/edit
 */
@Component({
    selector: 'app-item-catalog-edit',
    imports: [FormsModule, LucideAngularModule, TranslatePipe],
    templateUrl: './item-catalog-edit.component.html',
    styleUrls: ['./item-catalog-edit.component.scss']
})
export class ItemCatalogEditComponent implements OnInit {
  readonly ArrowLeft = ArrowLeft;
  readonly Save = Save;
  readonly Plus = Plus;
  readonly Trash2 = Trash2;
  readonly Sparkles = Sparkles;

  campaignId: string | null = null;
  catalogId: string | null = null;

  name = '';
  description = '';
  items: CatalogItem[] = [];

  saving = false;
  errorMessage = '';

  aiPrompt = '';
  generating = false;
  aiError = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private service: ItemCatalogService,
    private campaignSidebar: CampaignSidebarService,
    private translate: TranslateService
  ) {}

  ngOnInit(): void {
    const params = this.route.snapshot.paramMap;
    this.campaignId = params.get('campaignId');
    this.catalogId = params.get('catalogId');
    if (this.campaignId) this.campaignSidebar.show(this.campaignId);

    if (this.catalogId) {
      this.service.getById(this.catalogId).subscribe({
        next: (c: ItemCatalog) => {
          this.name = c.name;
          this.description = c.description ?? '';
          this.items = c.items.map(i => ({ ...i }));
        },
        error: () => this.back()
      });
    } else {
      this.addItem();
    }
  }

  addItem(): void {
    this.items.push({ name: '', price: '', category: '', description: '' });
  }

  removeItem(i: number): void {
    this.items.splice(i, 1);
  }

  generateWithAI(): void {
    if (!this.campaignId) return;
    if (!this.aiPrompt.trim()) { this.aiError = this.translate.instant('itemCatalogEdit.aiPromptRequired'); return; }
    this.generating = true;
    this.aiError = '';
    this.service.generate(this.campaignId, this.aiPrompt.trim()).subscribe({
      next: (c) => {
        this.generating = false;
        if (c.name && !this.name.trim()) this.name = c.name;
        if (c.description) this.description = c.description;
        this.items = (c.items ?? []).map(i => ({ ...i }));
      },
      error: (err) => {
        this.generating = false;
        this.aiError = err?.error?.message || this.translate.instant('itemCatalogEdit.aiError');
      }
    });
  }

  save(): void {
    if (!this.campaignId) return;
    if (!this.name.trim()) { this.errorMessage = this.translate.instant('itemCatalogEdit.nameRequired'); return; }
    this.saving = true;
    this.errorMessage = '';

    const cleanItems = this.items
      .filter(i => i.name.trim())
      .map(i => ({
        name: i.name.trim(),
        price: i.price?.trim() || undefined,
        category: i.category?.trim() || undefined,
        description: i.description?.trim() || undefined
      }));

    if (this.catalogId) {
      const payload: ItemCatalog = {
        id: this.catalogId,
        name: this.name.trim(),
        description: this.description.trim() || undefined,
        campaignId: this.campaignId,
        items: cleanItems
      };
      this.service.update(this.catalogId, payload).subscribe({
        next: () => this.goToView(this.catalogId!),
        error: (e) => this.fail(e)
      });
    } else {
      const payload: ItemCatalogCreate = {
        name: this.name.trim(),
        description: this.description.trim() || undefined,
        campaignId: this.campaignId,
        items: cleanItems
      };
      this.service.create(payload).subscribe({
        next: (c) => this.goToView(c.id!),
        error: (e) => this.fail(e)
      });
    }
  }

  private goToView(id: string): void {
    this.saving = false;
    this.router.navigate(['/campaigns', this.campaignId, 'item-catalogs', id]);
  }

  private fail(err: unknown): void {
    this.saving = false;
    this.errorMessage = this.translate.instant('itemCatalogEdit.saveError');
    console.error('ItemCatalog save failed', err);
  }

  back(): void {
    this.router.navigate(this.campaignId ? ['/campaigns', this.campaignId] : ['/campaigns']);
  }
}
