import { Component, ElementRef, HostListener, OnDestroy, OnInit, ViewChild } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { BehaviorSubject, Subject, forkJoin, of } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, switchMap, takeUntil } from 'rxjs/operators';
import { LucideAngularModule, Search, BookOpen, Folder, Users, FileText, Scroll, Drama, User, Dices, Package, Skull } from 'lucide-angular';
import { GlobalSearchService } from '../../services/global-search.service';
import { LoreService } from '../../services/lore.service';
import { PageService } from '../../services/page.service';
import { TemplateService } from '../../services/template.service';
import { CampaignService } from '../../services/campaign.service';
import { NpcService } from '../../services/npc.service';
import { CharacterService } from '../../services/character.service';
import { RandomTableService } from '../../services/random-table.service';
import { ItemCatalogService } from '../../services/item-catalog.service';
import { EnemyService } from '../../services/enemy.service';

type ResultKind =
  | 'lore' | 'node' | 'template' | 'page' | 'campaign'
  | 'npc' | 'character' | 'random-table' | 'item-catalog' | 'enemy';

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
 * Agrège les endpoints search de toutes les entités nommées : lore (lores,
 * dossiers, templates, pages) ET campagne (campagnes, PNJ, PJ, ennemis, tables
 * aléatoires, catalogues d'objets). Navigation clavier : ↑↓ ↵ Esc.
 */
@Component({
    selector: 'app-global-search',
    imports: [FormsModule, LucideAngularModule],
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
  readonly Drama = Drama;
  readonly User = User;
  readonly Dices = Dices;
  readonly Package = Package;
  readonly Skull = Skull;

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
    private campaignService: CampaignService,
    private npcService: NpcService,
    private characterService: CharacterService,
    private randomTableService: RandomTableService,
    private itemCatalogService: ItemCatalogService,
    private enemyService: EnemyService
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
          lores:      this.loreService.searchLores(trimmed).pipe(catchError(() => of([]))),
          nodes:      this.loreService.searchLoreNodes(trimmed).pipe(catchError(() => of([]))),
          templates:  this.templateService.search(trimmed).pipe(catchError(() => of([]))),
          pages:      this.pageService.search(trimmed).pipe(catchError(() => of([]))),
          campaigns:  this.campaignService.search(trimmed).pipe(catchError(() => of([]))),
          npcs:       this.npcService.search(trimmed).pipe(catchError(() => of([]))),
          characters: this.characterService.search(trimmed).pipe(catchError(() => of([]))),
          tables:     this.randomTableService.search(trimmed).pipe(catchError(() => of([]))),
          catalogs:   this.itemCatalogService.search(trimmed).pipe(catchError(() => of([]))),
          enemies:    this.enemyService.search(trimmed).pipe(catchError(() => of([])))
        }).pipe(
          switchMap(r => of(this.buildResults(r))),
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
   * puis les entités de campagne (PNJ, PJ, ennemis, tables, catalogues), les
   * noeuds/templates, et enfin les racines (campagnes, lores).
   */
  private buildResults(r: {
    lores: any[]; nodes: any[]; templates: any[]; pages: any[]; campaigns: any[];
    npcs: any[]; characters: any[]; tables: any[]; catalogs: any[]; enemies: any[];
  }): SearchResult[] {
    const { lores, nodes, templates, pages, campaigns, npcs, characters, tables, catalogs, enemies } = r;
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
    const npcResults: SearchResult[] = npcs.map(n => ({
      id: n.id,
      kind: 'npc' as ResultKind,
      title: n.name,
      subtitle: (n.folder ?? '') as string,
      tag: 'PNJ',
      route: ['/campaigns', n.campaignId, 'npcs', n.id]
    }));
    const characterResults: SearchResult[] = characters.map(c => ({
      id: c.id,
      kind: 'character' as ResultKind,
      title: c.name,
      subtitle: '',
      tag: 'PJ',
      route: ['/campaigns', c.campaignId, 'playthroughs', c.playthroughId, 'characters', c.id]
    }));
    const tableResults: SearchResult[] = tables.map(t => ({
      id: t.id,
      kind: 'random-table' as ResultKind,
      title: t.name,
      subtitle: t.description ?? '',
      tag: 'Table aléatoire',
      route: ['/campaigns', t.campaignId, 'random-tables', t.id]
    }));
    const catalogResults: SearchResult[] = catalogs.map(c => ({
      id: c.id,
      kind: 'item-catalog' as ResultKind,
      title: c.name,
      subtitle: c.description ?? '',
      tag: 'Catalogue d\'objets',
      route: ['/campaigns', c.campaignId, 'item-catalogs', c.id]
    }));
    const enemyResults: SearchResult[] = enemies.map(e => ({
      id: e.id,
      kind: 'enemy' as ResultKind,
      title: e.name,
      subtitle: [e.level ? `Niv. ${e.level}` : '', e.folder ?? ''].filter(Boolean).join(' · '),
      tag: 'Ennemi',
      route: ['/campaigns', e.campaignId, 'enemies', e.id]
    }));
    return [
      ...pageResults,
      ...npcResults, ...characterResults, ...enemyResults, ...tableResults, ...catalogResults,
      ...nodeResults, ...templateResults,
      ...campaignResults, ...loreResults
    ];
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
      case 'npc': return this.Drama;
      case 'character': return this.User;
      case 'random-table': return this.Dices;
      case 'item-catalog': return this.Package;
      case 'enemy': return this.Skull;
      default: return this.FileText;
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
