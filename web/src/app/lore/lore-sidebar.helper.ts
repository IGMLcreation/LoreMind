import { forkJoin, Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { LoreService } from '../services/lore.service';
import { TemplateService } from '../services/template.service';
import { PageService } from '../services/page.service';
import { Lore, LoreNode } from '../services/lore.model';
import { Template } from '../services/template.model';
import { Page } from '../services/page.model';
import {
  SecondarySidebarConfig, TreeItem, GlobalItem, BottomPanel
} from '../services/layout.service';

export interface LoreSidebarData {
  lore: Lore;
  allLores: Lore[];
  nodes: LoreNode[];
  templates: Template[];
  pages: Page[];
}

/**
 * Charge toutes les données nécessaires à la sidebar d'un Lore en parallèle.
 * Centralise le pattern commun aux écrans lore-detail, lore-node-create,
 * template-create, template-edit, page-create, page-edit.
 */
export function loadLoreSidebarData(
  loreId: string,
  loreService: LoreService,
  templateService: TemplateService,
  pageService: PageService
): Observable<LoreSidebarData> {
  return forkJoin({
    lore: loreService.getLoreById(loreId),
    allLores: loreService.getAllLores(),
    nodes: loreService.getLoreNodes(loreId),
    templates: templateService.getByLoreId(loreId),
    pages: pageService.getByLoreId(loreId)
  }).pipe(
    map(data => data as LoreSidebarData)
  );
}

/**
 * Construit la config complète de la SecondarySidebar pour un Lore, incluant
 * le panneau "Templates" en bas.
 */
export function buildLoreSidebarConfig(data: LoreSidebarData): SecondarySidebarConfig {
  const { lore, allLores, nodes, templates, pages } = data;

  // Regroupe les pages par nodeId et les sous-dossiers par parentId pour un accès O(1).
  const pagesByNode = new Map<string, Page[]>();
  for (const p of pages) {
    const bucket = pagesByNode.get(p.nodeId) ?? [];
    bucket.push(p);
    pagesByNode.set(p.nodeId, bucket);
  }
  const childrenByParent = new Map<string, LoreNode[]>();
  for (const n of nodes) {
    const parentKey = n.parentId ?? '__root__';
    const bucket = childrenByParent.get(parentKey) ?? [];
    bucket.push(n);
    childrenByParent.set(parentKey, bucket);
  }

  /**
   * Construit récursivement le TreeItem d'un dossier :
   * ses sous-dossiers, puis ses pages, puis les actions "+ Nouveau dossier" et "+ Nouvelle page".
   */
  const buildFolderItem = (node: LoreNode): TreeItem => {
    const subFolders = childrenByParent.get(node.id!) ?? [];
    const nodePages = pagesByNode.get(node.id!) ?? [];
    const children: TreeItem[] = [
      ...subFolders.map(buildFolderItem),
      ...nodePages.map(p => ({
        id: `page-${p.id}`,
        label: p.title,
        route: `/lore/${lore.id}/pages/${p.id}`
      }))
    ];
    // IDs préfixés par type — chaque entité a sa propre séquence IDENTITY en base,
    // donc node.id=1 et page.id=1 peuvent coexister et collisionner dans le
    // Set<string> global de LayoutService.expanded.
    return {
      id: `folder-${node.id}`,
      label: node.name,
      iconKey: node.icon ?? undefined,
      route: `/lore/${lore.id}/folders/${node.id}/edit`,
      meta: nodePages.length > 0 ? String(nodePages.length) : undefined,
      children,
      createActions: [
        {
          id: `create-folder-${node.id}`,
          label: 'Nouveau sous-dossier',
          route: `/lore/${lore.id}/folders/${node.id}/create`,
          actionIcon: 'folder-plus'
        },
        {
          id: `create-page-${node.id}`,
          label: 'Nouvelle page',
          route: `/lore/${lore.id}/nodes/${node.id}/pages/create`,
          actionIcon: 'file-plus'
        }
      ]
    };
  };

  // L'arbre démarre aux dossiers racine (parentId nul ou vide).
  const rootFolders = childrenByParent.get('__root__') ?? [];
  const treeItems: TreeItem[] = rootFolders.map(buildFolderItem);

  const globalItems: GlobalItem[] = allLores.map(l => ({
    id: l.id!, name: l.name, route: `/lore/${l.id}`
  }));

  const templatesPanel: BottomPanel = {
    id: 'templates',
    title: 'Templates',
    initiallyOpen: true,
    items: [
      ...templates.map(t => ({
        id: t.id!,
        label: t.name,
        meta: `${t.fieldCount ?? t.fields.length} champs`,
        route: `/lore/${lore.id}/templates/${t.id}`
      })),
      {
        id: 'create-template',
        label: '+ Nouveau template',
        isAction: true,
        route: `/lore/${lore.id}/templates/create`
      }
    ]
  };

  return {
    title: lore.name,
    items: treeItems,
    createActions: [
      { id: 'create-node', label: '+ Dossier', variant: 'primary',   route: `/lore/${lore.id}/nodes/create` },
      { id: 'create-page', label: '+ Page',  variant: 'secondary', route: `/lore/${lore.id}/pages/create` }
    ],
    globalItems,
    globalBackLabel: 'Tous les lores',
    globalBackRoute: '/lore',
    bottomPanel: templatesPanel
  };
}

/**
 * Retourne l'ensemble des IDs de `rootId` et de tous ses descendants (sous-dossiers).
 * Utilisé pour empêcher de choisir comme parent un dossier qui serait soi-même
 * ou un de ses descendants (ce qui créerait un cycle dans l'arbre).
 */
export function collectDescendantIds(rootId: string, allNodes: LoreNode[]): Set<string> {
  const ids = new Set<string>([rootId]);
  let grew = true;
  while (grew) {
    grew = false;
    for (const n of allNodes) {
      if (n.parentId && ids.has(n.parentId) && !ids.has(n.id!)) {
        ids.add(n.id!);
        grew = true;
      }
    }
  }
  return ids;
}
