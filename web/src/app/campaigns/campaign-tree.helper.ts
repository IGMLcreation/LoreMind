import { Observable, forkJoin, of } from 'rxjs';
import { switchMap, map } from 'rxjs/operators';
import { TranslateService } from '@ngx-translate/core';
import { CampaignService } from '../services/campaign.service';
import { NpcService } from '../services/npc.service';
import { RandomTableService } from '../services/random-table.service';
import { EnemyService } from '../services/enemy.service';
import { TreeItem, SecondarySidebarConfig, GlobalItem, ReorderKind } from '../services/layout.service';
import { groupByFolder, byOrder } from '../shared/folder-grouping.util';
import { Arc, Chapter, Scene, Campaign } from '../services/campaign.model';
import { Npc } from '../services/npc.model';
import { RandomTable } from '../services/random-table.model';
import { Enemy } from '../services/enemy.model';
import { catchError } from 'rxjs/operators';

/**
 * Helper — charge l'arborescence complète d'une campagne (arcs -> chapitres -> scènes)
 * et la transforme en TreeItem[] pour la secondary sidebar.
 *
 * Pourquoi un helper et pas un service ? C'est de la logique de présentation
 * (mapping REST -> ViewModel de la sidebar), pas du domaine métier.
 */

export interface CampaignTreeData {
  arcs: Arc[];
  chaptersByArc: Record<string, Chapter[]>;
  scenesByChapter: Record<string, Scene[]>;
  npcs: Npc[];
  randomTables: RandomTable[];
  enemies: Enemy[];
}

export function loadCampaignTreeData(
  service: CampaignService,
  campaignId: string,
  npcService: NpcService,
  // Optionnel pour ne pas casser les ~15 appelants existants : si fourni, les
  // tables aléatoires sont chargées et apparaissent dans la sidebar.
  randomTableService?: RandomTableService,
  // Optionnel (même principe) : si fourni, les ennemis sont chargés et le nœud
  // « Ennemis » devient dépliable (dossiers → fiches) en plus du lien.
  enemyService?: EnemyService
): Observable<CampaignTreeData> {
  // Note refonte Playthrough : les PJ appartiennent désormais à une Partie,
  // pas à la campagne — on ne les charge plus ici (les vues qui les affichent
  // doivent passer par PlaythroughService et appeler characterService.getByPlaythrough).
  return forkJoin({
    arcs: service.getArcs(campaignId),
    npcs: npcService.getByCampaign(campaignId),
    randomTables: randomTableService
      ? randomTableService.getByCampaign(campaignId).pipe(catchError(() => of([] as RandomTable[])))
      : of([] as RandomTable[]),
    enemies: enemyService
      ? enemyService.getByCampaign(campaignId).pipe(catchError(() => of([] as Enemy[])))
      : of([] as Enemy[])
  }).pipe(
    switchMap(({ arcs, npcs, randomTables, enemies }) => {
      if (arcs.length === 0) {
        return of({ arcs, chaptersByArc: {}, scenesByChapter: {}, npcs, randomTables, enemies });
      }
      const chapterCalls = arcs.map(a =>
        service.getChapters(a.id!).pipe(map(chapters => ({ arcId: a.id!, chapters })))
      );
      return forkJoin(chapterCalls).pipe(
        switchMap(chapterResults => {
          const chaptersByArc: Record<string, Chapter[]> = {};
          const allChapters: Chapter[] = [];
          chapterResults.forEach(r => {
            chaptersByArc[r.arcId] = r.chapters;
            allChapters.push(...r.chapters);
          });

          if (allChapters.length === 0) {
            return of({ arcs, chaptersByArc, scenesByChapter: {}, npcs, randomTables, enemies });
          }
          const sceneCalls = allChapters.map(c =>
            service.getScenes(c.id!).pipe(map(scenes => ({ chapterId: c.id!, scenes })))
          );
          return forkJoin(sceneCalls).pipe(
            map(sceneResults => {
              const scenesByChapter: Record<string, Scene[]> = {};
              sceneResults.forEach(r => { scenesByChapter[r.chapterId] = r.scenes; });
              return { arcs, chaptersByArc, scenesByChapter, npcs, randomTables, enemies };
            })
          );
        })
      );
    })
  );
}

export function buildCampaignTree(campaignId: string, data: CampaignTreeData, translate: TranslateService): TreeItem[] {
  // Tri par ORDRE manuel (réagencé par glisser-déposer, persisté en base ; `byOrder`
  // et `groupByFolder` sont mutualisés avec les vues cartes). Repli sur 0 sinon.

  /**
   * Nœuds-dossiers d'une collection ordonnable : un nœud dépliable par dossier
   * (+ un pseudo-dossier « Sans dossier »), via le regroupement partagé. Tous les
   * dossiers sont FRÈRES (jamais imbriqués) → glisser-déposer inter-dossiers fiable.
   */
  const folderChildren = <T extends { folder?: string | null; order?: number }>(
    items: T[], kind: ReorderKind, toItem: (x: T) => TreeItem, idPrefix: string
  ): TreeItem[] =>
    groupByFolder(items).map(g => ({
      id: g.folder ? `${idPrefix}-folder-${g.folder}` : `${idPrefix}-folder-__none__`,
      label: g.folder || translate.instant('campaignTree.unclassified'),
      iconKey: 'folder',
      children: g.items.map(toItem),
      meta: String(g.items.length),
      dropKinds: [kind], dropParentId: g.folder
    }));

  // IDs préfixés par type pour éviter les collisions dans LayoutService.expanded
  // (chaque entité a sa propre séquence IDENTITY en base → arc.id=1 et chapter.id=1
  // peuvent coexister et se marchaient sur les pieds dans le Set<string> global).
  // Note refonte Playthrough : les PJ ne sont plus rattachés à la campagne mais
  // à une Partie (Playthrough). On ne les affiche donc plus dans la sidebar de
  // campagne — seuls les PNJ (donnée de scénario) restent sous "Personnages".
  const sortedNpcs = [...data.npcs].sort(byOrder);
  const npcItem = (n: Npc): TreeItem => ({
    id: `npc-${n.id}`,
    label: n.name,
    route: `/campaigns/${campaignId}/npcs/${n.id}`,
    dragKind: 'npc', dragId: n.id
  });

  const npcChildren = folderChildren(sortedNpcs, 'npc', npcItem, 'npc');

  const npcsNode: TreeItem = {
    id: 'npcs-root',
    label: translate.instant('campaignTree.npcs'),
    iconKey: 'c-drama',
    children: npcChildren,
    meta: sortedNpcs.length ? String(sortedNpcs.length) : undefined,
    // Cliquer le LIBELLÉ ouvre la page de liste (vue d'ensemble par dossiers) ;
    // cliquer le CHEVRON déplie l'arbre dans la sidebar — les deux coexistent.
    route: `/campaigns/${campaignId}/npcs`,
    // Porte le header de section "Personnages" (les PJ ayant migré vers la Partie).
    // Le filet au-dessus est masqué par CSS si c'est le tout premier item de la sidebar.
    sectionHeaderBefore: translate.instant('campaignTree.sectionCharacters'),
    createActions: [{
      id: 'new-npc',
      label: translate.instant('campaignTree.newNpc'),
      route: `/campaigns/${campaignId}/npcs/create`,
      actionIcon: 'plus'
    }]
  };

  // --- Ennemis (bestiaire) : même structure que les PNJ — dossiers dépliables
  // dans la sidebar + libellé cliquable vers la page de liste.
  const sortedEnemies = [...(data.enemies ?? [])].sort(byOrder);
  const enemyItem = (e: Enemy): TreeItem => ({
    id: `enemy-${e.id}`,
    label: e.name,
    route: `/campaigns/${campaignId}/enemies/${e.id}`,
    meta: e.level ? `Niv. ${e.level}` : undefined,
    dragKind: 'enemy', dragId: e.id
  });
  const enemyChildren = folderChildren(sortedEnemies, 'enemy', enemyItem, 'enemy');

  const sortedArcs = [...data.arcs].sort(byOrder);

  const arcNodes: TreeItem[] = sortedArcs.map((arc, idx) => {
    const sortedChapters = [...(data.chaptersByArc[arc.id!] ?? [])].sort(byOrder);

    const chapterItems: TreeItem[] = sortedChapters.map(ch => {
      const sortedScenes = [...(data.scenesByChapter[ch.id!] ?? [])].sort(byOrder);

      const sceneItems: TreeItem[] = sortedScenes.map(sc => ({
        id: `scene-${sc.id}`,
        label: sc.name,
        iconKey: sc.icon ?? undefined,
        route: `/campaigns/${campaignId}/arcs/${arc.id}/chapters/${ch.id}/scenes/${sc.id}`,
        dragKind: 'scene', dragId: sc.id
      }));
      return {
        id: `chapter-${ch.id}`,
        label: ch.name,
        iconKey: ch.icon ?? undefined,
        // Cadenas si le chapitre porte des conditions de déblocage (hub ou linéaire).
        meta: (ch.prerequisites?.length ?? 0) > 0 ? '🔒' : undefined,
        children: sceneItems,
        dragKind: 'chapter', dragId: ch.id, dropKinds: ['scene'], dropParentId: ch.id,
        route: `/campaigns/${campaignId}/arcs/${arc.id}/chapters/${ch.id}`,
        createActions: [{
          id: `new-scene-${ch.id}`,
          label: translate.instant('campaignTree.newScene'),
          route: `/campaigns/${campaignId}/arcs/${arc.id}/chapters/${ch.id}/scenes/create`,
          actionIcon: 'plus'
        }]
      };
    });
    return {
      id: `arc-${arc.id}`,
      label: arc.name,
      iconKey: arc.icon ?? undefined,
      children: chapterItems,
      dragKind: 'arc', dragId: arc.id, dropKinds: ['chapter'], dropParentId: arc.id,
      route: `/campaigns/${campaignId}/arcs/${arc.id}`,
      sectionHeaderBefore: idx === 0 ? translate.instant('campaignTree.sectionNarration') : undefined,

      createActions: [{
        id: `new-chapter-${arc.id}`,
        // Dans un arc hub, un "chapitre" est présenté comme une "quête".
        label: arc.type === 'HUB' ? translate.instant('campaignTree.newQuest') : translate.instant('campaignTree.newChapter'),
        route: `/campaigns/${campaignId}/arcs/${arc.id}/chapters/create`,
        actionIcon: 'plus'
      }]
    };
  });

  const sortedTables = [...(data.randomTables ?? [])].sort(byOrder);
  const tableItems: TreeItem[] = sortedTables.map(t => ({
    id: `random-table-${t.id}`,
    label: t.name,
    iconKey: t.icon ?? 'dice',
    route: `/campaigns/${campaignId}/random-tables/${t.id}`,
    dragKind: 'table', dragId: t.id
  }));

  const tablesNode: TreeItem = {
    id: 'random-tables-root',
    label: translate.instant('campaignTree.randomTables'),
    iconKey: 'dice',
    children: tableItems,
    dropKinds: ['table'], dropParentId: '',
    meta: tableItems.length ? String(tableItems.length) : undefined,
    sectionHeaderBefore: translate.instant('campaignTree.sectionTools'),
    createActions: [{
      id: 'new-random-table',
      label: translate.instant('campaignTree.newTable'),
      route: `/campaigns/${campaignId}/random-tables/create`,
      actionIcon: 'plus'
    }]
  };

  // Lien simple vers les ateliers (la liste se charge sur sa page — pas de fetch ici).
  const notebooksNode: TreeItem = {
    id: 'notebooks-root',
    label: translate.instant('campaignTree.notebooks'),
    iconKey: 'book-open',
    route: `/campaigns/${campaignId}/notebooks`
  };

  // Catalogues d'objets (boutiques, butins…) → page de liste (outil).
  const catalogsNode: TreeItem = {
    id: 'item-catalogs-root',
    label: translate.instant('campaignTree.itemCatalogs'),
    iconKey: 'package',
    route: `/campaigns/${campaignId}/item-catalogs`
  };

  // Ennemis (bestiaire, fiches pilotées par le template Ennemi du GameSystem,
  // classées par dossier) — rangé avec les PERSONNAGES, comme les PNJ.
  // Libellé → page de liste ; chevron → arbre dépliable (dossiers → fiches).
  const enemiesNode: TreeItem = {
    id: 'enemies-root',
    label: translate.instant('campaignTree.enemies'),
    iconKey: 'skull',
    children: enemyChildren,
    meta: sortedEnemies.length ? String(sortedEnemies.length) : undefined,
    route: `/campaigns/${campaignId}/enemies`,
    createActions: [{
      id: 'new-enemy',
      label: translate.instant('campaignTree.newEnemy'),
      route: `/campaigns/${campaignId}/enemies/create`,
      actionIcon: 'plus'
    }]
  };

  // Importer un PDF de campagne → arborescence (outil, comme tables & ateliers).
  const importNode: TreeItem = {
    id: 'import-pdf-root',
    label: translate.instant('campaignTree.importPdf'),
    iconKey: 'file-up',
    route: `/campaigns/${campaignId}/import`
  };

  return [...arcNodes, npcsNode, enemiesNode, tablesNode, notebooksNode, catalogsNode, importNode];
}

/**
 * Construit la SecondarySidebarConfig complete d'une campagne a partir des
 * donnees deja chargees. A utiliser quand le composant fait deja un forkJoin
 * pour ses propres donnees (arc-view, scene-edit, etc.) et a deja `campaign`,
 * `allCampaigns` et `treeData` en main — evite de refaire les memes HTTP.
 *
 * Pour les composants qui n'ont pas d'autre fetch a faire (character-view,
 * npc-view...), preferer CampaignSidebarService.show(campaignId) qui orchestre
 * le forkJoin et appelle layoutService.show() en une seule ligne.
 */
export function buildCampaignSidebarConfig(
  campaign: Campaign,
  allCampaigns: Campaign[],
  treeData: CampaignTreeData,
  campaignId: string,
  translate: TranslateService
): SecondarySidebarConfig {
  const globalItems: GlobalItem[] = allCampaigns.map(c => ({
    id: c.id!, name: c.name, route: `/campaigns/${c.id}`
  }));
  return {
    title: campaign.name,
    // Titre cliquable → accueil de la campagne (raccourci depuis n'importe quelle sous-page).
    titleRoute: `/campaigns/${campaignId}`,
    items: buildCampaignTree(campaignId, treeData, translate),
    footerLabel: translate.instant('campaignTree.allCampaigns'),
    createActions: [
      { id: 'create-arc', label: translate.instant('campaignTree.newArc'), variant: 'primary', route: `/campaigns/${campaignId}/arcs/create` }
    ],
    globalItems,
    globalBackLabel: translate.instant('campaignTree.allCampaigns'),
    globalBackRoute: '/campaigns',
    // DnD : les arcs se réordonnent à la racine ; rechargement via le contexte campagne.
    rootDropKinds: ['arc'],
    rootDropParentId: campaignId,
    reorderContext: { scope: 'campaign', id: campaignId }
  };
}
