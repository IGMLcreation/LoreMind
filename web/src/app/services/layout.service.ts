import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export interface TreeItem {
  id: string;
  label: string;
  children?: TreeItem[];
  route?: string;      // si défini, cliquer navigue au lieu de toggler
  isAction?: boolean;  // style "action" (ex: "+ Nouveau chapitre")
  /** Clé d'icône optionnelle (ex: "users"). Résolue par le composant via `resolveIcon`. */
  iconKey?: string;
  /** Petit badge affiché à droite (ex: "3" pour compter les pages d'un dossier). */
  meta?: string;
}

export interface GlobalItem {
  id: string;
  name: string;
  route: string;
}

export interface SidebarAction {
  id: string;
  label: string;
  variant: 'primary' | 'secondary' | 'success';
  route?: string;
}

/**
 * Item affiché dans un panneau secondaire (ex: liste des Templates).
 * Plus simple qu'un TreeItem : pas de récursion, juste label + meta + route.
 */
export interface BottomPanelItem {
  id: string;
  label: string;
  meta?: string;      // petit badge à droite (ex: "8 champs")
  route?: string;
  isAction?: boolean; // style "action" (ex: "+ Nouveau template")
}

/**
 * Panneau secondaire collapsible en bas de la sidebar (sous l'arbre).
 * Utilisé notamment pour le panneau "Templates" côté Lore.
 */
export interface BottomPanel {
  id: string;         // identifiant pour mémoriser l'état ouvert/fermé
  title: string;
  items: BottomPanelItem[];
  initiallyOpen?: boolean;
}

export interface SecondarySidebarConfig {
  title: string;
  items: TreeItem[];
  createActions: SidebarAction[];
  globalItems: GlobalItem[];
  globalBackLabel: string;
  globalBackRoute: string;
  bottomPanel?: BottomPanel;   // optionnel : présent côté Lore (Templates)
  /** @deprecated Remplacé par bottomPanel. Gardé pour compat des callers campagne. */
  footerLabel?: string;
}

/**
 * Service de layout — contrôle l'affichage de la secondary sidebar
 * et les données contextuelles de la sidebar globale.
 *
 * L'état d'expansion des items de l'arbre est maintenu au niveau du service
 * (et non dans le composant secondary-sidebar), pour survivre aux
 * destructions/recréations du composant lors des navigations (le *ngIf dans
 * app.component.html détruit la sidebar à chaque `hide()`).
 */
@Injectable({ providedIn: 'root' })
export class LayoutService {
  private config$ = new BehaviorSubject<SecondarySidebarConfig | null>(null);
  private readonly expanded = new Set<string>();

  readonly secondarySidebar$ = this.config$.asObservable();

  show(config: SecondarySidebarConfig): void {
    this.config$.next(config);
  }

  hide(): void {
    this.config$.next(null);
  }

  isExpanded(id: string): boolean {
    return this.expanded.has(id);
  }

  toggleExpanded(id: string): void {
    if (this.expanded.has(id)) this.expanded.delete(id);
    else this.expanded.add(id);
  }

  setExpanded(id: string, state: boolean): void {
    if (state) this.expanded.add(id);
    else this.expanded.delete(id);
  }
}
