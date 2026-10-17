import { Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { LayoutService, SecondarySidebarConfig } from './layout.service';
import { campaignBreadcrumbs, isWithinRoute } from '../shared/navigation-path';
import { NavigationEntry, NavigationHistory, contentUrl, readHistory, rememberVisit, toggleFavorite } from '../shared/navigation-history';

/** Shortcuts belong to this browser and origin; unavailable storage is non-fatal. */
@Injectable({ providedIn: 'root' })
export class NavigationHistoryService {
  private readonly key = 'loremind.navigation.v1';
  private readonly state = signal<NavigationHistory>(this.load());
  readonly history = this.state.asReadonly();
  private context: SecondarySidebarConfig | null = null;

  constructor(layout: LayoutService, router: Router) {
    layout.secondarySidebar$.pipe(takeUntilDestroyed()).subscribe(context => {
      this.context = context;
      const current = this.state().recent.find(item => item.url === contentUrl(router.url));
      if (current) this.visit(current.url, current.title);
    });
  }

  private subtitleFor(url: string): string {
    const path = campaignBreadcrumbs(url, this.context, '');
    if (path.length > 1) return path.slice(0, -1).map(item => item.label).join(' › ');
    const parent = this.context?.globalItems.find(item => item.route !== url && isWithinRoute(url, item.route));
    return parent?.name ?? '';
  }

  private load(): NavigationHistory {
    try { return readHistory(localStorage.getItem(this.key)); }
    catch { return { recent: [], favorites: [] }; }
  }

  private save(history: NavigationHistory): void {
    this.state.set(history);
    try { localStorage.setItem(this.key, JSON.stringify(history)); } catch { /* Keep shortcuts in memory. */ }
  }

  visit(url: string, title: string, subtitle = ''): void {
    const path = contentUrl(url);
    if (path && title.trim()) {
      const existing = this.state().recent.find(item => item.url === path);
      this.save(rememberVisit(this.state(), { url: path, title, subtitle: subtitle || this.subtitleFor(path) || existing?.subtitle || '' }));
    }
  }

  isFavorite(url: string): boolean { return this.state().favorites.some(item => item.url === url); }

  toggle(entry: NavigationEntry): void {
    if (contentUrl(entry.url) === entry.url) this.save(toggleFavorite(this.state(), entry));
  }

  clearRecent(): void { this.save({ ...this.state(), recent: [] }); }
}
