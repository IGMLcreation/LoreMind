import { Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { filter } from 'rxjs';
import { LayoutService, SecondarySidebarConfig } from '../../services/layout.service';
import { BreadcrumbComponent } from './breadcrumb.component';
import { campaignBreadcrumbs } from '../navigation-path';

@Component({
  selector: 'app-campaign-breadcrumb',
  imports: [BreadcrumbComponent],
  template: '@if (items.length) { <div class="campaign-path"><app-breadcrumb [items]="items"></app-breadcrumb></div> }',
  styles: ['.campaign-path { padding-top: 1rem; overflow-wrap: anywhere; }']
})
export class CampaignBreadcrumbComponent {
  private readonly router = inject(Router);
  private readonly translate = inject(TranslateService);
  private config: SecondarySidebarConfig | null = null;
  private url = this.router.url;

  constructor() {
    const destroyRef = inject(DestroyRef);
    inject(LayoutService).secondarySidebar$.pipe(takeUntilDestroyed(destroyRef))
      .subscribe(config => this.config = config);
    this.router.events.pipe(filter(e => e instanceof NavigationEnd), takeUntilDestroyed(destroyRef))
      .subscribe(e => this.url = e.urlAfterRedirects);
  }

  get items() {
    return campaignBreadcrumbs(this.url, this.config, this.translate.instant('common.edit'));
  }
}
