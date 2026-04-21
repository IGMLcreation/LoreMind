import { Component, HostListener } from '@angular/core';
import { AsyncPipe, NgIf } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { SidebarComponent } from './sidebar/sidebar.component';
import { SecondarySidebarComponent } from './shared/secondary-sidebar/secondary-sidebar.component';
import { GlobalSearchComponent } from './shared/global-search/global-search.component';
import { LayoutService } from './services/layout.service';
import { GlobalSearchService } from './services/global-search.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, SidebarComponent, SecondarySidebarComponent, GlobalSearchComponent, AsyncPipe, NgIf],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})
export class AppComponent {
  readonly sidebarConfig$ = this.layoutService.secondarySidebar$;

  constructor(
    private layoutService: LayoutService,
    private globalSearch: GlobalSearchService
  ) {}

  @HostListener('document:keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    // Ctrl+K (Windows/Linux) ou Cmd+K (macOS)
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      this.globalSearch.toggle();
    }
  }
}
