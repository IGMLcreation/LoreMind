import { Component, ElementRef, HostListener, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { BehaviorSubject, Subject, forkJoin, of } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, switchMap, takeUntil } from 'rxjs/operators';
import { LucideAngularModule, Search, BookOpen, Folder, Users, FileText, Scroll } from 'lucide-angular';
import { GlobalSearchService } from '../../services/global-search.service';
import { LoreService } from '../../services/lore.service';
import { PageService } from '../../services/page.service';
import { TemplateService } from '../../services/template.service';
import { CampaignService } from '../../services/campaign.service';

type ResultKind = 'lore' | 'node' | 'template' | 'page' | 'campaign';

interface SearchResult {
  id: string;
  kind: ResultKind;
  title: string;
  subtitle: string;
  /** Tag affiché sous le titre (ex: "Lore", "Dossier", "Template", "Page"). */
  tag: string;
  /** Route Angular (array pour router.navigate). */
  route: any[];
}

/**
 * Command palette globale (Ctrl+K / Cmd+K).
 * Agrège 4 endpoints search (Lore, LoreNode, Template, Page) côté frontend.
 * Navigation clavier : ↑↓ ↵ Esc.
 */
@Component({
  selector: 'app-global-search',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideAngularModule],
  templateUrl: './global-search.component.html',
  styleUrls: ['./global-search.component.scss']
})
export class GlobalSearchComponent implements OnInit, OnDestroy {
  readonly Search = Search;
  readonly BookOpen = BookOpen;
  readonly Folder = Folder;
  readonly Users = Users;
  readonly FileText = FileText;
  readonly Scroll = Scroll;

  @ViewChild('searchInput') searchInput?: ElementRef<HTMLInputElement>;

  open = false;
  query = '';
  loading = false;
  results: SearchResult[] = [];
  activeIndex = 0;

  private readonly queryChanges$ = new BehaviorSubject<string>('');
  private readonly destroy$ = new Subject<void>();

  constructor(
    private globalSearch: GlobalSearchService,
    private router: Router,
    private loreService: LoreService,
    private pageService: PageService,
    private templateService: TemplateService,
    private campaignService: CampaignService
  ) {}

  ngOnInit(): void {
    this.globalSearch.open$.pipe(takeUntil(this.destroy$)).subscribe(open => {
      this.open = open;
      if (open) {
        this.query = '';
        this.results = [];
        this.activeIndex = 0;
        // focus après le tick de rendu (ngIf du template)
        setTimeout(() => this.searchInput?.nativeElement.focus(), 0);
      }
    });

    this.queryChanges$.pipe(
      debounceTime(200),
      distinctUntilChanged(),
      switchMap(q => {
        const trimmed = q.trim();
        if (trimmed.length < 2) {
          this.loading = false;
          return of<SearchResult[]>([]);
        }
        this.loading = true;
        return forkJoin({
          lores:     this.loreService.searchLores(trimmed).pipe(catchError(() => of([]))),
          nodes:     this.loreService.searchLoreNodes(trimmed).pipe(catchError(() => of([]))),
          templates: this.templateService.search(trimmed).pipe(catchError(() => of([]))),
          pages:     this.pageService.search(trimmed).pipe(catchError(() => of([]))),
          campaigns: this.campaignService.search(trimmed).pipe(catchError(() => of([])))
        }).pipe(
          switchMap(({ lores, nodes, templates, pages, campaigns }) => of(this.buildResults(lores, nodes, templates, pages, campaigns))),
          catchError(() => of<SearchResult[]>([]))
        );
      }),
      takeUntil(this.destroy$)
    ).subscribe(results => {
      this.results = results;
      this.activeIndex = 0;
      this.loading = false;
    });
  }

  onQueryChange(value: string): void {
    this.query = value;
    this.queryChanges$.next(value);
  }

  /**
   * Construit la liste unifiée. Ordre d'affichage : pages d'abord (le plus recherché),
   * puis noeuds, templates, campagnes, et lores — les entités racines apparaissent en dernier.
   */
  private buildResults(
    lores: any[], nodes: any[], templates: any[], pages: any[], campaigns: any[]
  ): SearchResult[] {
    const pageResults: SearchResult[] = pages.map(p => ({
      id: p.id,
      kind: 'page' as ResultKind,
      title: p.title,
      subtitle: p.notes ? this.firstLine(p.notes) : '',
      tag: 'Page',
      route: ['/lore', p.loreId, 'pages', p.id]
    }));
    const nodeResults: SearchResult[] = nodes.map(n => ({
      id: n.id,
      kind: 'node' as ResultKind,
      title: n.name,
      subtitle: '',
      tag: 'Dossier',
      route: ['/lore', n.loreId, 'folders', n.id]
    }));
    const templateResults: SearchResult[] = templates.map(t => ({
      id: t.id,
      kind: 'template' as ResultKind,
      title: t.name,
      subtitle: t.description ?? '',
      tag: 'Template',
      route: ['/lore', t.loreId, 'templates', t.id]
    }));
    const loreResults: SearchResult[] = lores.map(l => ({
      id: l.id,
      kind: 'lore' as ResultKind,
      title: l.name,
      subtitle: l.description ?? '',
      tag: 'Lore',
      route: ['/lore', l.id]
    }));
    const campaignResults: SearchResult[] = campaigns.map(c => ({
      id: c.id,
      kind: 'campaign' as ResultKind,
      title: c.name,
      subtitle: c.description ?? '',
      tag: 'Campagne',
      route: ['/campaigns', c.id]
    }));
    return [...pageResults, ...nodeResults, ...templateResults, ...campaignResults, ...loreResults];
  }

  private firstLine(text: string): string {
    const line = text.split('\n')[0] ?? '';
    return line.length > 120 ? line.slice(0, 117) + '…' : line;
  }

  select(result: SearchResult): void {
    this.router.navigate(result.route);
    this.globalSearch.close();
  }

  close(): void {
    this.globalSearch.close();
  }

  /** Raccourcis clavier globaux — actifs uniquement quand la modale est ouverte. */
  @HostListener('document:keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    if (!this.open) return;
    if (event.key === 'Escape') {
      event.preventDefault();
      this.close();
    } else if (event.key === 'ArrowDown') {
      event.preventDefault();
      if (this.results.length > 0) {
        this.activeIndex = (this.activeIndex + 1) % this.results.length;
      }
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      if (this.results.length > 0) {
        this.activeIndex = (this.activeIndex - 1 + this.results.length) % this.results.length;
      }
    } else if (event.key === 'Enter') {
      event.preventDefault();
      const r = this.results[this.activeIndex];
      if (r) this.select(r);
    }
  }

  iconFor(kind: ResultKind) {
    switch (kind) {
      case 'lore': return this.BookOpen;
      case 'node': return this.Folder;
      case 'template': return this.Users;
      case 'page': return this.FileText;
      case 'campaign': return this.Scroll;
      default: return this.FileText;
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
