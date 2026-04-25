import { Component, OnInit } from '@angular/core';
import { AsyncPipe, NgIf, NgFor } from '@angular/common';
import { Router } from '@angular/router';
import { LucideAngularModule, Search, Download, Settings, ArrowLeft, Dices } from 'lucide-angular';
import { LayoutService } from '../services/layout.service';
import { GlobalSearchService } from '../services/global-search.service';
import { ConfigService } from '../services/config.service';
import { UpdatesService } from '../services/updates.service';
// Single source of truth pour la version affichée dans le footer :
// on lit directement package.json à la compilation (resolveJsonModule).
import packageJson from '../../../package.json';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [AsyncPipe, NgIf, NgFor, LucideAngularModule],
  templateUrl: './sidebar.component.html',
  styleUrls: ['./sidebar.component.scss']
})
export class SidebarComponent implements OnInit {
  currentRoute = '';

  readonly Search = Search;
  readonly Download = Download;
  readonly Settings = Settings;
  readonly ArrowLeft = ArrowLeft;
  readonly Dices = Dices;

  readonly layoutConfig$ = this.layoutService.secondarySidebar$;
  readonly appVersion = packageJson.version;
  readonly updateAvailable$ = this.updates.updateAvailable$;

  constructor(
    private router: Router,
    private layoutService: LayoutService,
    private globalSearch: GlobalSearchService,
    public config: ConfigService,
    private updates: UpdatesService
  ) {
    this.router.events.subscribe(() => {
      this.currentRoute = this.router.url;
    });
  }

  ngOnInit(): void {
    // Premier check au boot uniquement si la feature est activee + mode non-demo.
    // L'erreur 401 (admin non auth) est silencieusement ignoree par le service —
    // le badge ne s'affichera que si l'utilisateur est passe par /settings et a
    // saisi ses credentials HTTP Basic. Comportement attendu mono-utilisateur.
    if (this.config.updateCheckEnabled && !this.config.demoMode) {
      this.updates.checkNow().subscribe();
    }
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
