import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';

import { TranslatePipe } from '@ngx-translate/core';
import { LucideAngularModule, Package, BookmarkPlus, ChevronLeft } from 'lucide-angular';
import { catchError, of } from 'rxjs';
import { ItemCatalogService } from '../../services/item-catalog.service';
import { ItemCatalog, CatalogItem } from '../../services/item-catalog.model';

interface ItemGroup { category: string; items: CatalogItem[]; }

/**
 * Panneau « Objets » du mode jeu : consulter les catalogues (boutiques) de la
 * campagne et consigner un objet au journal (ex. « le joueur achète X »).
 */
@Component({
    selector: 'app-session-item-catalogs-panel',
    imports: [LucideAngularModule, TranslatePipe],
    templateUrl: './session-item-catalogs-panel.component.html',
    styleUrls: ['./session-item-catalogs-panel.component.scss']
})
export class SessionItemCatalogsPanelComponent implements OnInit {
  readonly Package = Package;
  readonly BookmarkPlus = BookmarkPlus;
  readonly ChevronLeft = ChevronLeft;

  @Input() campaignId!: string;
  @Input() canAddToJournal = true;
  /** Émis pour consigner un objet au journal (entrée NOTE). */
  @Output() noteToJournal = new EventEmitter<string>();

  catalogs: ItemCatalog[] = [];
  loading = false;
  selected: ItemCatalog | null = null;

  constructor(private service: ItemCatalogService) {}

  ngOnInit(): void {
    if (!this.campaignId) return;
    this.loading = true;
    this.service.getByCampaign(this.campaignId).pipe(catchError(() => of([] as ItemCatalog[])))
      .subscribe(list => { this.catalogs = list; this.loading = false; });
  }

  select(c: ItemCatalog): void { this.selected = c; }
  backToList(): void { this.selected = null; }

  get groups(): ItemGroup[] {
    const map = new Map<string, CatalogItem[]>();
    for (const it of this.selected?.items ?? []) {
      const cat = (it.category ?? '').trim() || '—';
      if (!map.has(cat)) map.set(cat, []);
      map.get(cat)!.push(it);
    }
    return [...map.entries()]
      .sort(([a], [b]) => {
        if (a === '—') return 1;   // catégorie "sans catégorie" toujours en dernier
        if (b === '—') return -1;
        return a.localeCompare(b, 'fr');
      })
      .map(([category, items]) => ({ category, items }));
  }

  note(it: CatalogItem): void {
    if (!this.canAddToJournal || !this.selected) return;
    const price = it.price ? ` (${it.price})` : '';
    this.noteToJournal.emit(`🛒 ${this.selected.name} — ${it.name}${price}`);
  }
}
