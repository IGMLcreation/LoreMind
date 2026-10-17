import type { SecondarySidebarConfig, TreeItem } from '../services/layout.service';
import type { BreadcrumbItem } from './breadcrumb/breadcrumb.component';

/** Segment boundaries prevent campaign 1 from matching campaign 10. */
export function isWithinRoute(url: string, route: string): boolean {
  const path = url.split(/[?#]/)[0];
  return path === route || path.startsWith(route + '/');
}

/** Follow the visible tree, including flattened campaigns and quest containers. */
export function campaignBreadcrumbs(
  url: string, config: SecondarySidebarConfig | null, editLabel: string
): BreadcrumbItem[] {
  const root = config?.titleRoute;
  if (!config || !root?.startsWith('/campaigns/') || !isWithinRoute(url, root)) return [];
  const path = url.split(/[?#]/)[0];
  if (path === root) return [];
  let best: BreadcrumbItem[] = [];
  let matchedRoute = '';
  const visit = (items: TreeItem[], parents: BreadcrumbItem[]) => {
    for (const item of items) {
      const trail = item.route === root ? parents : [...parents, { label: item.label, route: item.route }];
      if (item.route && item.route !== root && isWithinRoute(path, item.route) && item.route.length > matchedRoute.length) {
        best = trail;
        matchedRoute = item.route;
      }
      for (const action of item.createActions ?? []) {
        if (path === action.route) {
          best = [...trail, { label: action.label }];
          matchedRoute = action.route;
        }
      }
      visit(item.children ?? [], trail);
    }
  };
  visit(config.items, []);
  for (const action of config.createActions) {
    if (action.route === path) {
      best = [{ label: action.label }];
      matchedRoute = path;
    }
  }
  if (!matchedRoute || (path !== matchedRoute && path !== matchedRoute + '/edit')) return [];
  if (path.endsWith('/edit')) best.push({ label: editLabel });
  return [{ label: config.title, route: root }, ...best];
}
