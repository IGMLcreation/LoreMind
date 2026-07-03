import { Observable, of } from 'rxjs';
import { map, catchError } from 'rxjs/operators';
import { TranslateService } from '@ngx-translate/core';
import { CampaignService } from '../services/campaign.service';
import { NpcService } from '../services/npc.service';
import { RandomTableService } from '../services/random-table.service';
import { EnemyService } from '../services/enemy.service';
import { TreeItem, TreeCreateAction, SecondarySidebarConfig, GlobalItem, ReorderKind } from '../services/layout.service';
import { groupByFolder, byOrder } from '../shared/folder-grouping.util';
import { Arc, Chapter, Scene, Campaign, Quest } from '../services/campaign.model';
import { Npc } from '../services/npc.model';
import { RandomTable } from '../services/random-table.model';
import { Enemy } from '../services/enemy.model';
import { CampaignReadinessAssessment, ReadinessGap, ReadinessSeverity, ReadinessEntityType } from '../services/readiness.model';

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
  /** Manques de readiness (Pilier B) — alimentent les pastilles de l'arbre. Optionnel. */
  gaps?: ReadinessGap[];
  /** Quêtes de la campagne — pour afficher celles rattachées à un arc HUB. Optionnel. */
  quests?: Quest[];
  /** Bilan de readiness complet — évite un fetch séparé pour le panneau du hub. */
  readiness?: CampaignReadinessAssessment | null;
}

export function loadCampaignTreeData(
  service: CampaignService,
  campaignId: string,
  // Paramètres CONSERVÉS pour ne pas toucher les ~15 appelants, mais devenus inutiles :
  // l'arbre complet (structure + PNJ + tables + ennemis + quêtes + readiness) arrive
  // désormais en UNE requête via GET /api/campaigns/{id}/tree — la rafale de 15-20
  // appels HTTP par navigation était ce qui rendait la sidebar lente.
  _npcService?: NpcService,
  _randomTableService?: RandomTableService,
  _enemyService?: EnemyService
): Observable<CampaignTreeData> {
  return service.getTree(campaignId).pipe(
    map(tree => ({
      arcs: tree.arcs,
      chaptersByArc: tree.chaptersByArc ?? {},
      scenesByChapter: tree.scenesByChapter ?? {},
      npcs: tree.npcs ?? [],
      randomTables: tree.randomTables ?? [],
      enemies: tree.enemies ?? [],
      quests: tree.quests ?? [],
      gaps: tree.readiness?.gaps ?? [],
      readiness: tree.readiness ?? null
    } as CampaignTreeData)),
    // Dégradation gracieuse : sidebar vide plutôt qu'écran cassé si le back est indisponible.
    catchError(() => of({
      arcs: [], chaptersByArc: {}, scenesByChapter: {},
      npcs: [], randomTables: [], enemies: [], quests: [], gaps: [], readiness: null
    } as CampaignTreeData))
  );
}

export function buildCampaignTree(
  campaignId: string,
  data: CampaignTreeData,
  translate: TranslateService,
  readinessGaps: ReadinessGap[] = []
): TreeItem[] {
  // Tri par ORDRE manuel (réagencé par glisser-déposer, persisté en base ; `byOrder`
  // et `groupByFolder` sont mutualisés avec les vues cartes). Repli sur 0 sinon.

  // --- Readiness (Pilier B — guidage) : pastille par nœud = pire sévérité de
  // l'entité ET de ses descendants, avec au survol LE(S) message(s) réel(s) du manque
  // (fini le tooltip générique). Purement dérivé des gaps (aucune persistance).
  // Clé TYPE|ID obligatoire : chaque table a sa propre séquence IDENTITY (une quête
  // id 6 et une scène id 6 coexistent) — sans le type, un manque de quête allumerait
  // à tort la pastille d'une scène/PNJ portant le même numéro.
  const sevRank = (s: ReadinessSeverity): number => (s === 'BLOCKING' ? 2 : s === 'RECOMMENDED' ? 1 : 0);
  const gapsByKey = new Map<string, ReadinessGap[]>();
  for (const g of readinessGaps) {
    const key = `${g.entityType}|${g.entityId}`;
    const list = gapsByKey.get(key) ?? [];
    list.push(g);
    gapsByKey.set(key, list);
  }
  const gapsOf = (type: ReadinessEntityType, id?: string | null): ReadinessGap[] =>
    (id ? (gapsByKey.get(`${type}|${id}`) ?? []) : []);
  const quests: Quest[] = data.quests ?? [];
  const questsByArc = (arcId: string): Quest[] => quests.filter(q => q.arcId === arcId);
  const chapterGaps = (ch: Chapter): ReadinessGap[] => [
    ...gapsOf('CHAPTER', ch.id),
    ...(data.scenesByChapter[ch.id!] ?? []).flatMap(s => gapsOf('SCENE', s.id))
  ];
  const arcGaps = (arc: Arc): ReadinessGap[] => [
    ...gapsOf('ARC', arc.id),
    ...(data.chaptersByArc[arc.id!] ?? []).flatMap(chapterGaps),
    ...(arc.type === 'HUB' ? questsByArc(arc.id!).flatMap(q => gapsOf('QUEST', q.id)) : [])
  ];
  /** Pastille + tooltip : les pires manques en clair (2 max, puis « + N autres »). */
  const dotProps = (gaps: ReadinessGap[]): Pick<TreeItem, 'statusDot' | 'statusDotTitle'> => {
    const worst = Math.max(0, ...gaps.map(g => sevRank(g.severity)));
    if (worst < 1) return {};
    const sorted = [...gaps].sort((a, b) => sevRank(b.severity) - sevRank(a.severity));
    const shown = sorted.slice(0, 2).map(g => g.message);
    if (sorted.length > shown.length) {
      shown.push(translate.instant('readiness.dot.more', { n: sorted.length - shown.length }));
    }
    return { statusDot: worst >= 2 ? 'blocking' : 'recommended', statusDotTitle: shown.join('\n') };
  };

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
  const npcGaps = sortedNpcs.flatMap(n => gapsOf('NPC', n.id));

  const npcsNode: TreeItem = {
    id: 'npcs-root',
    label: translate.instant('campaignTree.npcs'),
    iconKey: 'c-drama',
    children: npcChildren,
    ...dotProps(npcGaps),
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
  const enemyGaps = sortedEnemies.flatMap(e => gapsOf('ENEMY', e.id));
  const enemyItem = (e: Enemy): TreeItem => ({
    id: `enemy-${e.id}`,
    label: e.name,
    route: `/campaigns/${campaignId}/enemies/${e.id}`,
    meta: e.level ? `Niv. ${e.level}` : undefined,
    dragKind: 'enemy', dragId: e.id
  });
  const enemyChildren = folderChildren(sortedEnemies, 'enemy', enemyItem, 'enemy');

  // L'arc SYSTEM (« Quêtes libres ») est de la plomberie : ses chapitres sont les
  // conteneurs des quêtes libres, affichés sous le nœud « Quêtes » — jamais en narration.
  const sortedArcs = [...data.arcs].sort(byOrder).filter(a => a.type !== 'SYSTEM');

  const arcNodes: TreeItem[] = sortedArcs.map((arc, idx) => {
    const sortedChapters = [...(data.chaptersByArc[arc.id!] ?? [])].sort(byOrder);
    const isHub = arc.type === 'HUB';

    const sceneItemsOf = (ch: Chapter): TreeItem[] =>
      [...(data.scenesByChapter[ch.id!] ?? [])].sort(byOrder).map(sc => ({
        id: `scene-${sc.id}`,
        label: sc.name,
        iconKey: sc.icon ?? undefined,
        route: `/campaigns/${campaignId}/arcs/${arc.id}/chapters/${ch.id}/scenes/${sc.id}`,
        dragKind: 'scene' as ReorderKind, dragId: sc.id,
        ...dotProps(gapsOf('SCENE', sc.id))
      }));

    const newSceneAction = (ch: Chapter) => ({
      id: `new-scene-${ch.id}`,
      label: translate.instant('campaignTree.newScene'),
      route: `/campaigns/${campaignId}/arcs/${arc.id}/chapters/${ch.id}/scenes/create`,
      actionIcon: 'plus' as const
    });

    const chapterNode = (ch: Chapter): TreeItem => ({
      id: `chapter-${ch.id}`,
      label: ch.name,
      iconKey: ch.icon ?? undefined,
      children: sceneItemsOf(ch),
      dragKind: 'chapter', dragId: ch.id, dropKinds: ['scene'], dropParentId: ch.id,
      route: `/campaigns/${campaignId}/arcs/${arc.id}/chapters/${ch.id}`,
      ...dotProps(chapterGaps(ch)),
      createActions: [newSceneAction(ch)]
    });

    let children: TreeItem[];
    if (isHub) {
      // Arc HUB = quêtes. Une quête FUSIONNE avec son chapitre jumeau (héritage V10 :
      // même contenu, la quête référence le chapitre en nœud) : UN SEUL nœud d'arbre —
      // la quête — portant les scènes du jumeau. Évite les doublons quête/chapitre qui
      // rendaient l'arbre illisible sur les campagnes migrées.
      const attached = [...questsByArc(arc.id!)].sort(byOrder);
      const consumed = new Set<string>();
      const questNodes: TreeItem[] = attached.map(q => {
        const twinIds = new Set((q.nodes ?? [])
          .filter(n => n.nodeType === 'CHAPTER').map(n => n.nodeId));
        const twins = sortedChapters.filter(ch => twinIds.has(ch.id!));
        twins.forEach(ch => consumed.add(ch.id!));
        const questNodeGaps = [...gapsOf('QUEST', q.id), ...twins.flatMap(chapterGaps)];
        const container = twins[0];
        return {
          id: `quest-${q.id}`,
          label: q.name,
          iconKey: q.icon ?? 'flag',
          route: `/campaigns/${campaignId}/quests/${q.id}`,
          ...dotProps(questNodeGaps),
          children: twins.flatMap(sceneItemsOf),
          // Le premier jumeau reste le conteneur des scènes (ajout + réordonnancement).
          ...(container ? {
            dropKinds: ['scene'] as ReorderKind[],
            dropParentId: container.id,
            createActions: [newSceneAction(container)]
          } : {})
        };
      });
      // Chapitres non « consommés » par une quête (contenu mixte / historique) : affichés tels quels.
      const plainChapters = sortedChapters.filter(ch => !consumed.has(ch.id!)).map(chapterNode);
      children = [...questNodes, ...plainChapters];
    } else {
      children = sortedChapters.map(chapterNode);
    }

    // HUB → on ne crée QUE des quêtes ; LINÉAIRE → que des chapitres.
    const arcCreateActions: TreeCreateAction[] = isHub
      ? [{
          id: `new-quest-${arc.id}`,
          label: translate.instant('campaignTree.newQuest'),
          route: `/campaigns/${campaignId}/quests/create`,
          queryParams: { arcId: arc.id! },
          actionIcon: 'plus'
        }]
      : [{
          id: `new-chapter-${arc.id}`,
          label: translate.instant('campaignTree.newChapter'),
          route: `/campaigns/${campaignId}/arcs/${arc.id}/chapters/create`,
          actionIcon: 'plus'
        }];

    return {
      id: `arc-${arc.id}`,
      label: arc.name,
      iconKey: arc.icon ?? undefined,
      children,
      dragKind: 'arc', dragId: arc.id, dropKinds: ['chapter'], dropParentId: arc.id,
      route: `/campaigns/${campaignId}/arcs/${arc.id}`,
      ...dotProps(arcGaps(arc)),
      sectionHeaderBefore: idx === 0 ? translate.instant('campaignTree.sectionNarration') : undefined,
      createActions: arcCreateActions
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
    ...dotProps(enemyGaps),
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

  // Mode "plat" (Niveau 0) : une campagne avec un seul arc d'un seul chapitre n'a
  // pas besoin de la hiérarchie arc > chapitre. On masque ces deux niveaux et on
  // présente directement les scènes sous un nœud neutre "Scènes". Le drag & drop
  // des scènes et l'ajout restent identiques (c'est le nœud chapitre, relabellisé
  // et remonté à la racine). Dès qu'un 2e arc OU un 2e chapitre existe, la
  // structure complète réapparaît automatiquement.
  const onlyArc = sortedArcs.length === 1 ? sortedArcs[0] : null;
  const onlyArcChapters = onlyArc ? (data.chaptersByArc[onlyArc.id!] ?? []) : [];
  let narrationNodes: TreeItem[] = arcNodes;
  // Le mode plat ne s'applique PAS à un arc HUB (qui présente des quêtes, pas juste des scènes).
  if (onlyArc && onlyArc.type !== 'HUB' && onlyArcChapters.length === 1) {
    const ch = onlyArcChapters[0];
    const sortedScenes = [...(data.scenesByChapter[ch.id!] ?? [])].sort(byOrder);
    const sceneItems: TreeItem[] = sortedScenes.map(sc => ({
      id: `scene-${sc.id}`,
      label: sc.name,
      iconKey: sc.icon ?? undefined,
      route: `/campaigns/${campaignId}/arcs/${onlyArc.id}/chapters/${ch.id}/scenes/${sc.id}`,
      dragKind: 'scene', dragId: sc.id,
      ...dotProps(gapsOf('SCENE', sc.id))
    }));
    narrationNodes = [{
      id: `chapter-${ch.id}`,
      label: translate.instant('campaignTree.scenesGroup'),
      iconKey: 'book-open',
      children: sceneItems,
      ...dotProps(chapterGaps(ch)),
      // Réordonnancement des scènes inchangé : ce nœud EST le chapitre (caché).
      dropKinds: ['scene'], dropParentId: ch.id,
      // En mode plat le chapitre est masqué : le libellé « Scènes » ouvre la CARTE des
      // scènes (graphe) — cohérent avec le label, et garde le graphe accessible — plutôt
      // que la vue du chapitre caché (titrée « Chapitre 1 », sans liste de scènes : déroutant).
      // Le chevron, lui, déplie la liste des scènes ci-dessous.
      route: `/campaigns/${campaignId}/arcs/${onlyArc.id}/chapters/${ch.id}/graph`,
      sectionHeaderBefore: translate.instant('campaignTree.sectionNarration'),
      createActions: [{
        id: `new-scene-${ch.id}`,
        label: translate.instant('campaignTree.newScene'),
        route: `/campaigns/${campaignId}/arcs/${onlyArc.id}/chapters/${ch.id}/scenes/create`,
        actionIcon: 'plus'
      }]
    }];
  }

  // Quêtes LIBRES (hors arc) : même fusion quête/conteneur que dans les hubs — le
  // conteneur vit dans l'arc SYSTEM (chargé avec l'arbre), la quête s'affiche avec
  // ses scènes dessous et son « + scène ». Recherche des conteneurs TOUS arcs confondus.
  const chapterById = new Map<string, Chapter>(
    Object.values(data.chaptersByArc).flat().map(ch => [ch.id!, ch]));
  const questsFolderGaps: ReadinessGap[] = [];
  const freeQuestNodes: TreeItem[] = quests.filter(q => !q.arcId).sort(byOrder).map(q => {
    const twins = (q.nodes ?? [])
      .filter(n => n.nodeType === 'CHAPTER')
      .map(n => chapterById.get(n.nodeId))
      .filter((c): c is Chapter => !!c);
    const questGaps = [...gapsOf('QUEST', q.id), ...twins.flatMap(chapterGaps)];
    questsFolderGaps.push(...questGaps);
    const container = twins[0];
    const sceneItems: TreeItem[] = twins.flatMap(ch =>
      [...(data.scenesByChapter[ch.id!] ?? [])].sort(byOrder).map(sc => ({
        id: `scene-${sc.id}`,
        label: sc.name,
        iconKey: sc.icon ?? undefined,
        route: `/campaigns/${campaignId}/arcs/${ch.arcId}/chapters/${ch.id}/scenes/${sc.id}`,
        dragKind: 'scene' as ReorderKind, dragId: sc.id,
        ...dotProps(gapsOf('SCENE', sc.id))
      })));
    return {
      id: `quest-${q.id}`,
      label: q.name,
      iconKey: q.icon ?? 'flag',
      route: `/campaigns/${campaignId}/quests/${q.id}`,
      ...dotProps(questGaps),
      children: sceneItems,
      ...(container ? {
        dropKinds: ['scene'] as ReorderKind[],
        dropParentId: container.id,
        createActions: [{
          id: `new-scene-${container.id}`,
          label: translate.instant('campaignTree.newScene'),
          route: `/campaigns/${campaignId}/arcs/${container.arcId}/chapters/${container.id}/scenes/create`,
          actionIcon: 'plus' as const
        }]
      } : {})
    } as TreeItem;
  });

  const questsNode: TreeItem = {
    id: 'quests-root',
    label: translate.instant('campaignTree.quests'),
    iconKey: 'flag',
    route: `/campaigns/${campaignId}/quests`,
    children: freeQuestNodes,
    meta: freeQuestNodes.length ? String(freeQuestNodes.length) : undefined,
    ...dotProps(questsFolderGaps),
    createActions: [{
      id: 'new-quest-node',
      label: translate.instant('campaignTree.newQuest'),
      route: `/campaigns/${campaignId}/quests/create`,
      actionIcon: 'plus'
    }]
  };

  return [...narrationNodes, questsNode, npcsNode, enemiesNode, tablesNode, notebooksNode, catalogsNode, importNode];
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
  translate: TranslateService,
  readinessGaps: ReadinessGap[] = []
): SecondarySidebarConfig {
  const globalItems: GlobalItem[] = allCampaigns.map(c => ({
    id: c.id!, name: c.name, route: `/campaigns/${c.id}`
  }));
  return {
    title: campaign.name,
    // Titre cliquable → accueil de la campagne (raccourci depuis n'importe quelle sous-page).
    titleRoute: `/campaigns/${campaignId}`,
    // Priorité aux gaps passés explicitement (hub) ; sinon ceux embarqués dans treeData
    // (chargés par loadCampaignTreeData) → pastilles présentes sur TOUTES les pages.
    items: buildCampaignTree(campaignId, treeData, translate, readinessGaps.length ? readinessGaps : (treeData.gaps ?? [])),
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
