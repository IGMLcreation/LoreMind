import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import {
  LucideAngularModule, Search, X, Sparkles, ChevronDown, ChevronRight, Pin, PinOff, BookOpen, ExternalLink
} from 'lucide-angular';
import DOMPurify from 'dompurify';
import { marked } from 'marked';
import { Subscription, of } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { CampaignService } from '../../services/campaign.service';
import { GameSystemService } from '../../services/game-system.service';
import { GameSystem } from '../../services/game-system.model';
import {
  RuleMatch, RuleSection, blocksToMarkdown, filterRuleSections, highlightHtml, parseRuleSections, searchTerms
} from './rules-reference.util';

type RulesState = 'loading' | 'noSystem' | 'noRules' | 'ready';

/**
 * Onglet « Règles » du mode jeu : les règles du système de la campagne sous la main,
 * lisibles et cherchables INSTANTANÉMENT (sans IA, hors ligne).
 *
 * <p>Sommaire des sections H2, sections repliables, recherche qui filtre jusqu'au
 * paragraphe et surligne, sections épinglées (par système, dans le navigateur).
 * « Demander à l'IA » bascule sur l'onglet IA avec la question préremplie.</p>
 */
@Component({
  selector: 'app-session-rules-panel',
  imports: [FormsModule, LucideAngularModule, TranslatePipe],
  templateUrl: './session-rules-panel.component.html',
  styleUrls: ['./session-rules-panel.component.scss']
})
export class SessionRulesPanelComponent implements OnChanges, OnDestroy {
  readonly Search = Search;
  readonly X = X;
  readonly Sparkles = Sparkles;
  readonly ChevronDown = ChevronDown;
  readonly ChevronRight = ChevronRight;
  readonly Pin = Pin;
  readonly PinOff = PinOff;
  readonly BookOpen = BookOpen;
  readonly ExternalLink = ExternalLink;

  @Input() campaignId = '';
  /** Question (éventuellement vide) à poser dans l'onglet IA. */
  @Output() askAi = new EventEmitter<string>();

  state: RulesState = 'loading';
  gameSystem: GameSystem | null = null;
  sections: RuleSection[] = [];
  matches: RuleMatch[] = [];

  query = '';
  terms: string[] = [];
  tocOpen = true;

  /** Sections dépliées hors recherche (repliées par défaut, sauf les épinglées). */
  private opened = new Set<string>();
  /** Sections repliées À LA MAIN pendant une recherche (sinon tout résultat est déplié). */
  private collapsedInSearch = new Set<string>();
  /** Titres épinglés (le titre survit aux réimports, pas l'index). */
  private pinned = new Set<string>();
  private htmlCache = new Map<string, SafeHtml>();
  private searchTimer: ReturnType<typeof setTimeout> | null = null;
  private loadSub: Subscription | null = null;

  constructor(
    private campaignService: CampaignService,
    private gameSystemService: GameSystemService,
    private translate: TranslateService,
    private sanitizer: DomSanitizer
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['campaignId']) this.load();
  }

  ngOnDestroy(): void {
    this.loadSub?.unsubscribe();
    if (this.searchTimer) clearTimeout(this.searchTimer);
  }

  private load(): void {
    this.loadSub?.unsubscribe();
    this.state = 'loading';
    this.gameSystem = null;
    this.sections = [];
    this.matches = [];
    if (!this.campaignId) return;
    this.loadSub = this.campaignService.getCampaignById(this.campaignId).pipe(
      switchMap(c => c.gameSystemId
        ? this.gameSystemService.getById(c.gameSystemId).pipe(catchError(() => of(null)))
        : of(null)),
      catchError(() => of(null))
    ).subscribe(gs => this.applySystem(gs));
  }

  private applySystem(gs: GameSystem | null): void {
    this.gameSystem = gs;
    if (!gs) { this.state = 'noSystem'; return; }
    // Règles dans la langue de l'interface, avec repli sur l'autre version si elle est vide.
    const english = this.translate.getCurrentLang() === 'en';
    const preferred = english ? gs.rulesMarkdownEn : gs.rulesMarkdown;
    const fallback = english ? gs.rulesMarkdown : gs.rulesMarkdownEn;
    const rules = preferred?.trim() ? preferred : fallback;
    this.sections = parseRuleSections(rules, this.translate.instant('sessionRules.preamble'));
    if (this.sections.length === 0) { this.state = 'noRules'; return; }
    this.pinned = this.readPins();
    this.opened = new Set(this.sections.filter(s => this.pinned.has(s.title)).map(s => s.id));
    this.htmlCache.clear();
    this.state = 'ready';
    this.recompute();
  }

  // ─────────────── Recherche ───────────────

  onQueryChange(value: string): void {
    this.query = value;
    if (this.searchTimer) clearTimeout(this.searchTimer);
    // Petit délai : un gros livre de règles reste fluide pendant la frappe.
    this.searchTimer = setTimeout(() => this.applyQuery(), 120);
  }

  clearQuery(): void {
    this.query = '';
    this.applyQuery();
  }

  private applyQuery(): void {
    this.searchTimer = null;
    const terms = searchTerms(this.query);
    if (terms.join(' ') === this.terms.join(' ')) return;
    this.terms = terms;
    this.collapsedInSearch.clear();
    this.htmlCache.clear();
    this.recompute();
  }

  private recompute(): void {
    const matches = filterRuleSections(this.sections, this.terms);
    // Hors recherche : épinglées en tête (ordre du livre conservé dans chaque groupe).
    this.matches = this.terms.length
      ? matches
      : [...matches.filter(m => this.isPinned(m)), ...matches.filter(m => !this.isPinned(m))];
  }

  get searching(): boolean {
    return this.terms.length > 0;
  }

  get pinnedCount(): number {
    return this.matches.filter(m => this.isPinned(m)).length;
  }

  // ─────────────── Sections ───────────────

  isOpen(m: RuleMatch): boolean {
    return this.searching ? !this.collapsedInSearch.has(m.section.id) : this.opened.has(m.section.id);
  }

  toggle(m: RuleMatch): void {
    const set = this.searching ? this.collapsedInSearch : this.opened;
    const id = m.section.id;
    if (set.has(id)) set.delete(id);
    else set.add(id);
  }

  /** Sommaire : déplie la section et la fait défiler en haut du panneau. */
  jumpTo(m: RuleMatch): void {
    if (!this.isOpen(m)) this.toggle(m);
    setTimeout(() => document.getElementById(m.section.id)?.scrollIntoView({ block: 'start', behavior: 'smooth' }));
  }

  /** HTML de la section (blocs retenus), assaini puis surligné ; mis en cache par recherche. */
  html(m: RuleMatch): SafeHtml {
    const key = `${m.section.id}|${m.blocks.length}`;
    const cached = this.htmlCache.get(key);
    if (cached) return cached;
    const markdown = blocksToMarkdown(m.blocks);
    const raw = marked.parse(markdown, { async: false, gfm: true, breaks: true }) as string;
    const clean = DOMPurify.sanitize(raw);
    // Revue sécurité : le bypass est sûr ICI — le HTML sort de DOMPurify et le surlignage
    // n'ajoute que des <mark> autour de texte ré-échappé (cf. highlightHtml).
    // eslint-disable-next-line sonarjs/no-angular-bypass-sanitization
    const safe = this.sanitizer.bypassSecurityTrustHtml(highlightHtml(clean, this.terms));
    this.htmlCache.set(key, safe);
    return safe;
  }

  // ─────────────── Épingles (localStorage, par système) ───────────────

  isPinned(m: RuleMatch): boolean {
    return this.pinned.has(m.section.title);
  }

  togglePin(m: RuleMatch): void {
    const title = m.section.title;
    if (this.pinned.has(title)) this.pinned.delete(title);
    else this.pinned.add(title);
    this.writePins();
    if (!this.searching) this.recompute();
  }

  private pinsKey(): string | null {
    return this.gameSystem?.id ? `loremind.sessionRules.pinned.${this.gameSystem.id}` : null;
  }

  private readPins(): Set<string> {
    const key = this.pinsKey();
    if (!key) return new Set();
    try {
      const parsed: unknown = JSON.parse(localStorage.getItem(key) ?? '[]');
      return new Set(Array.isArray(parsed) ? parsed.filter((x): x is string => typeof x === 'string') : []);
    } catch {
      return new Set();
    }
  }

  private writePins(): void {
    const key = this.pinsKey();
    if (!key) return;
    try {
      localStorage.setItem(key, JSON.stringify([...this.pinned]));
    } catch {
      // Stockage indisponible (navigation privée…) : l'épingle vaut pour cet affichage seulement.
    }
  }

  // ─────────────── IA & liens ───────────────

  askAiAboutQuery(): void {
    const q = this.query.trim();
    this.askAi.emit(q ? this.translate.instant('sessionRules.askTemplate', { q }) : '');
  }

  askAiAboutSection(m: RuleMatch): void {
    this.askAi.emit(this.translate.instant('sessionRules.askSectionTemplate', { title: m.section.title }));
  }

  /** Nouvel onglet : on ne quitte pas l'écran de séance (même règle que les fiches). */
  openInNewTab(path: string): void {
    window.open(path, '_blank', 'noopener');
  }

  get chooseSystemUrl(): string {
    return `/campaigns/${this.campaignId}?edit=system`;
  }

  get editRulesUrl(): string {
    return `/game-systems/${this.gameSystem?.id}/edit`;
  }
}
