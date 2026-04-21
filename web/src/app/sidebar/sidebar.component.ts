import { Component } from '@angular/core';
import { AsyncPipe, NgIf, NgFor } from '@angular/common';
import { Router } from '@angular/router';
import { LucideAngularModule, Search, Download, Settings, ArrowLeft } from 'lucide-angular';
import { LayoutService } from '../services/layout.service';
import { GlobalSearchService } from '../services/global-search.service';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [AsyncPipe, NgIf, NgFor, LucideAngularModule],
  templateUrl: './sidebar.component.html',
  styleUrls: ['./sidebar.component.scss']
})
export class SidebarComponent {
  currentRoute = '';

  readonly Search = Search;
  readonly Download = Download;
  readonly Settings = Settings;
  readonly ArrowLeft = ArrowLeft;

  readonly layoutConfig$ = this.layoutService.secondarySidebar$;

  constructor(
    private router: Router,
    private layoutService: LayoutService,
    private globalSearch: GlobalSearchService
  ) {
    this.router.events.subscribe(() => {
      this.currentRoute = this.router.url;
    });
  }

  navigateTo(route: string): void {
    this.router.navigate([route]);
  }

  isActive(itemRoute: string): boolean {
    return this.currentRoute === itemRoute;
  }

  openSearch(): void {
    this.globalSearch.open();
  }
}
