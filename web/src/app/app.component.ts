import { Component, ElementRef, HostListener, effect, inject, signal, viewChild } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { BreakpointObserver } from '@angular/cdk/layout';
import { NavigationEnd, NavigationSkipped, Router, RouterOutlet } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule, Menu, PanelLeft, Search, X } from 'lucide-angular';
import { filter, map } from 'rxjs';
import { SidebarComponent } from './sidebar/sidebar.component';
import { SecondarySidebarComponent } from './shared/secondary-sidebar/secondary-sidebar.component';
import { GlobalSearchComponent } from './shared/global-search/global-search.component';
import { UpdateBannerComponent } from './shared/update-banner/update-banner.component';
import { ConfirmDialogHostComponent } from './shared/confirm-dialog/confirm-dialog-host.component';
import { LayoutService } from './services/layout.service';
import { GlobalSearchService } from './services/global-search.service';
import { VersionCheckerService } from './services/version-checker.service';

/** Sous cette largeur, les deux sidebars deviennent des tiroirs off-canvas (tablette, téléphone). */
const COMPACT_QUERY = '(max-width: 1023.98px)';

/** Tiroir ouvert en mode compact : menu principal, arborescence secondaire, ou aucun. */
type ShellDrawer = 'main' | 'tree' | null;

const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

@Component({
    selector: 'app-root',
    imports: [
    RouterOutlet,
    LucideAngularModule,
    TranslatePipe,
    SidebarComponent,
    SecondarySidebarComponent,
    GlobalSearchComponent,
    UpdateBannerComponent,
    ConfirmDialogHostComponent,
    AsyncPipe
],
    templateUrl: './app.component.html',
    styleUrls: ['./app.component.scss']
})
export class AppComponent {
  readonly sidebarConfig$ = this.layoutService.secondarySidebar$;

  readonly Menu = Menu;
  readonly PanelLeft = PanelLeft;
  readonly Search = Search;
  readonly X = X;

  private readonly breakpoints = inject(BreakpointObserver);
  private readonly router = inject(Router);

  /** True sous 1024 px : barre haute + tiroirs au lieu des colonnes fixes. */
  readonly isCompact = toSignal(
    this.breakpoints.observe(COMPACT_QUERY).pipe(map(state => state.matches)),
    { initialValue: this.breakpoints.isMatched(COMPACT_QUERY) }
  );

  /** Tiroir ouvert (mode compact uniquement). */
  readonly openDrawer = signal<ShellDrawer>(null);

  private readonly mainDrawerRef = viewChild(SidebarComponent, { read: ElementRef });
  private readonly treeDrawerRef = viewChild(SecondarySidebarComponent, { read: ElementRef });
  private readonly mainToggleRef = viewChild<ElementRef<HTMLButtonElement>>('mainToggle');
  private readonly treeToggleRef = viewChild<ElementRef<HTMLButtonElement>>('treeToggle');
  private readonly mainContentRef = viewChild<ElementRef<HTMLElement>>('mainContent');

  constructor(
    private layoutService: LayoutService,
    private globalSearch: GlobalSearchService,
    versionChecker: VersionCheckerService,
  ) {
    // Demarre la detection de mise a jour en arriere-plan.
    // Si une nouvelle version est deployee pendant la session, l'UpdateBanner
    // s'affichera automatiquement.
    versionChecker.start();

    // Retour en largeur desktop : les tiroirs n'ont plus de sens, on les referme.
    effect(() => {
      if (!this.isCompact()) this.openDrawer.set(null);
    });

    // Une navigation (y compris vers l'URL courante, NavigationSkipped) referme le
    // tiroir : on choisit une page, on veut la voir. Le focus part sur le contenu.
    this.router.events.pipe(
      filter(e => e instanceof NavigationEnd || e instanceof NavigationSkipped),
      takeUntilDestroyed(),
    ).subscribe(() => {
      if (!this.openDrawer()) return;
      this.openDrawer.set(null);
      this.mainContentRef()?.nativeElement.focus({ preventScroll: true });
    });

    // La recherche globale passe au-dessus : inutile de laisser le tiroir ouvert dessous.
    this.globalSearch.open$.pipe(filter(Boolean), takeUntilDestroyed()).subscribe(() => this.openDrawer.set(null));
  }

  toggleDrawer(drawer: Exclude<ShellDrawer, null>): void {
    if (this.openDrawer() === drawer) {
      this.closeDrawer();
      return;
    }
    this.openDrawer.set(drawer);
    // Focus sur le premier élément du tiroir une fois rendu visible.
    setTimeout(() => this.focusables(this.drawerElement())[0]?.focus());
  }

  /** Ferme le tiroir et rend le focus au bouton de la barre haute qui l'a ouvert. */
  closeDrawer(): void {
    const drawer = this.openDrawer();
    if (!drawer) return;
    this.openDrawer.set(null);
    const toggle = drawer === 'main' ? this.mainToggleRef() : this.treeToggleRef();
    toggle?.nativeElement.focus();
  }

  openSearch(): void {
    this.globalSearch.open();
  }

  @HostListener('document:keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    // Ctrl+K (Windows/Linux) ou Cmd+K (macOS)
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      this.globalSearch.toggle();
      return;
    }
    if (!this.openDrawer()) return;
    if (event.key === 'Escape') {
      event.preventDefault();
      this.closeDrawer();
    } else if (event.key === 'Tab') {
      this.trapFocus(event);
    }
  }

  /** Garde le focus clavier dans le tiroir ouvert (comportement de modale). */
  private trapFocus(event: KeyboardEvent): void {
    const drawer = this.drawerElement();
    const items = this.focusables(drawer);
    if (!drawer || !items.length) return;
    const first = items[0];
    const last = items[items.length - 1];
    const active = document.activeElement as HTMLElement | null;
    const inside = !!active && drawer.contains(active);
    if (event.shiftKey && (active === first || !inside)) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && (active === last || !inside)) {
      event.preventDefault();
      first.focus();
    }
  }

  private drawerElement(): HTMLElement | null {
    const drawer = this.openDrawer();
    if (drawer === 'main') return this.mainDrawerRef()?.nativeElement ?? null;
    if (drawer === 'tree') return this.treeDrawerRef()?.nativeElement ?? null;
    return null;
  }

  private focusables(root: HTMLElement | null): HTMLElement[] {
    if (!root) return [];
    return Array.from(root.querySelectorAll<HTMLElement>(FOCUSABLE))
      .filter(el => el.offsetParent !== null || el.getClientRects().length > 0);
  }
}
