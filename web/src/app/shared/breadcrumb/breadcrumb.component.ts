import { Component, Input } from '@angular/core';

import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

/**
 * Un niveau dans le fil d'Ariane.
 * Si `route` est défini, l'item est cliquable (navigation).
 * Sinon, c'est la position courante (dernier niveau, non-cliquable).
 */
export interface BreadcrumbItem {
  label: string;
  route?: string | any[];
}

/**
 * Composant réutilisable de fil d'Ariane.
 * Utilisation type :
 *   <app-breadcrumb [items]="[
 *     { label: 'Mon Univers', route: ['/lore', loreId] },
 *     { label: 'PNJ',         route: ['/lore', loreId, 'folders', nodeId] },
 *     { label: 'Aldric' }
 *   ]"></app-breadcrumb>
 */
@Component({
    selector: 'app-breadcrumb',
    imports: [RouterLink, TranslatePipe],
    templateUrl: './breadcrumb.component.html',
    styleUrls: ['./breadcrumb.component.scss']
})
export class BreadcrumbComponent {
  @Input() items: BreadcrumbItem[] = [];

}
