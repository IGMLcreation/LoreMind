import { Observable, forkJoin, of } from 'rxjs';
import { switchMap, map } from 'rxjs/operators';
import { CampaignService } from '../services/campaign.service';
import { CharacterService } from '../services/character.service';
import { NpcService } from '../services/npc.service';
import { RandomTableService } from '../services/random-table.service';
import { TreeItem, SecondarySidebarConfig, GlobalItem } from '../services/layout.service';
import { Arc, Chapter, Scene, Campaign } from '../services/campaign.model';
import { Character } from '../services/character.model';
import { Npc } from '../services/npc.model';
import { RandomTable } from '../services/random-table.model';
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
  characters: Character[];
  npcs: Npc[];
  randomTables: RandomTable[];
}

export function loadCampaignTreeData(
  service: CampaignService,
  campaignId: string,
  characterService: CharacterService,
  npcService: NpcService,
  // Optionnel pour ne pas casser les ~15 appelants existants : si fourni, les
  // tables aléatoires sont chargées et apparaissent dans la sidebar.
  randomTableService?: RandomTableService
): Observable<CampaignTreeData> {
  // Note refonte Playthrough : les PJ appartiennent désormais à une Partie,
  // pas à la campagne — on ne les charge plus ici (les vues qui les affichent
  // doivent passer par PlaythroughService et appeler characterService.getByPlaythrough).
  return forkJoin({
    arcs: service.getArcs(campaignId),
    characters: of([] as Character[]),
    npcs: npcService.getByCampaign(campaignId),
    randomTables: randomTableService
      ? randomTableService.getByCampaign(campaignId).pipe(catchError(() => of([] as RandomTable[])))
      : of([] as RandomTable[])
  }).pipe(
    switchMap(({ arcs, characters, npcs, randomTables }) => {
      if (arcs.length === 0) {
        return of({ arcs, chaptersByArc: {}, scenesByChapter: {}, characters, npcs, randomTables });
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
            return of({ arcs, chaptersByArc, scenesByChapter: {}, characters, npcs, randomTables });
          }
          const sceneCalls = allChapters.map(c =>
            service.getScenes(c.id!).pipe(map(scenes => ({ chapterId: c.id!, scenes })))
          );
          return forkJoin(sceneCalls).pipe(
            map(sceneResults => {
              const scenesByChapter: Record<string, Scene[]> = {};
              sceneResults.forEach(r => { scenesByChapter[r.chapterId] = r.scenes; });
              return { arcs, chaptersByArc, scenesByChapter, characters, npcs, randomTables };
            })
          );
        })
      );
    })
  );
}

export function buildCampaignTree(campaignId: string, data: CampaignTreeData): TreeItem[] {
  // Tri FR avec `numeric: true` pour que "1. Intro", "2. Voyage", "10. Final" soient
  // classés 1, 2, 10 (et pas 1, 10, 2). `sensitivity: 'base'` ignore la casse.
  const byName = (a: { name: string }, b: { name: string }) =>
    a.name.localeCompare(b.name, 'fr', { numeric: true, sensitivity: 'base' });

  // IDs préfixés par type pour éviter les collisions dans LayoutService.expanded
  // (chaque entité a sa propre séquence IDENTITY en base → arc.id=1 et chapter.id=1
  // peuvent coexister et se marchaient sur les pieds dans le Set<string> global).
  // Note refonte Playthrough : les PJ ne sont plus rattachés à la campagne mais
  // à une Partie (Playthrough). On ne les affiche donc plus dans la sidebar de
  // campagne — seuls les PNJ (donnée de scénario) restent sous "Personnages".
  const sortedNpcs = [...data.npcs].sort(byName);
  const npcItems: TreeItem[] = sortedNpcs.map(n => ({
    id: `npc-${n.id}`,
    label: n.name,
    route: `/campaigns/${campaignId}/npcs/${n.id}`
  }));

  const npcsNode: TreeItem = {
    id: 'npcs-root',
    label: 'PNJ',
    iconKey: 'c-drama',
    children: npcItems,
    meta: npcItems.length ? String(npcItems.length) : undefined,
    // Porte le header de section "Personnages" (les PJ ayant migré vers la Partie).
    // Le filet au-dessus est masqué par CSS si c'est le tout premier item de la sidebar.
    sectionHeaderBefore: 'Personnages',
    createActions: [{
      id: 'new-npc',
      label: 'Nouveau PNJ',
      route: `/campaigns/${campaignId}/npcs/create`,
      actionIcon: 'plus'
    }]
  };

  const sortedArcs = [...data.arcs].sort(byName);

  const arcNodes: TreeItem[] = sortedArcs.map((arc, idx) => {
    const sortedChapters = [...(data.chaptersByArc[arc.id!] ?? [])].sort(byName);

    const chapterItems: TreeItem[] = sortedChapters.map(ch => {
      const sortedScenes = [...(data.scenesByChapter[ch.id!] ?? [])].sort(byName);

      const sceneItems: TreeItem[] = sortedScenes.map(sc => ({
        id: `scene-${sc.id}`,
        label: sc.name,
        iconKey: sc.icon ?? undefined,
        route: `/campaigns/${campaignId}/arcs/${arc.id}/chapters/${ch.id}/scenes/${sc.id}`
      }));
      return {
        id: `chapter-${ch.id}`,
        label: ch.name,
        iconKey: ch.icon ?? undefined,
        // Cadenas si le chapitre porte des conditions de déblocage (hub ou linéaire).
        meta: (ch.prerequisites?.length ?? 0) > 0 ? '🔒' : undefined,
        children: sceneItems,
        route: `/campaigns/${campaignId}/arcs/${arc.id}/chapters/${ch.id}`,
        createActions: [{
          id: `new-scene-${ch.id}`,
          label: 'Nouvelle scène',
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
      route: `/campaigns/${campaignId}/arcs/${arc.id}`,
      sectionHeaderBefore: idx === 0 ? 'Narration' : undefined,

      createActions: [{
        id: `new-chapter-${arc.id}`,
        // Dans un arc hub, un "chapitre" est présenté comme une "quête".
        label: arc.type === 'HUB' ? 'Nouvelle quête' : 'Nouveau chapitre',
        route: `/campaigns/${campaignId}/arcs/${arc.id}/chapters/create`,
        actionIcon: 'plus'
      }]
    };
  });

  const sortedTables = [...(data.randomTables ?? [])].sort(byName);
  const tableItems: TreeItem[] = sortedTables.map(t => ({
    id: `random-table-${t.id}`,
    label: t.name,
    iconKey: t.icon ?? 'dice',
    route: `/campaigns/${campaignId}/random-tables/${t.id}`
  }));

  const tablesNode: TreeItem = {
    id: 'random-tables-root',
    label: 'Tables aléatoires',
    iconKey: 'dice',
    children: tableItems,
    meta: tableItems.length ? String(tableItems.length) : undefined,
    sectionHeaderBefore: 'Outils',
    createActions: [{
      id: 'new-random-table',
      label: 'Nouvelle table',
      route: `/campaigns/${campaignId}/random-tables/create`,
      actionIcon: 'plus'
    }]
  };

  // Lien simple vers les ateliers (la liste se charge sur sa page — pas de fetch ici).
  const notebooksNode: TreeItem = {
    id: 'notebooks-root',
    label: 'Ateliers (IA + PDF)',
    iconKey: 'book-open',
    route: `/campaigns/${campaignId}/notebooks`
  };

  // Importer un PDF de campagne → arborescence (outil, comme tables & ateliers).
  const importNode: TreeItem = {
    id: 'import-pdf-root',
    label: 'Importer un PDF',
    iconKey: 'file-up',
    route: `/campaigns/${campaignId}/import`
  };

  return [...arcNodes, npcsNode, tablesNode, notebooksNode, importNode];
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
  campaignId: string
): SecondarySidebarConfig {
  const globalItems: GlobalItem[] = allCampaigns.map(c => ({
    id: c.id!, name: c.name, route: `/campaigns/${c.id}`
  }));
  return {
    title: campaign.name,
    // Titre cliquable → accueil de la campagne (raccourci depuis n'importe quelle sous-page).
    titleRoute: `/campaigns/${campaignId}`,
    items: buildCampaignTree(campaignId, treeData),
    footerLabel: 'Toutes les campagnes',
    createActions: [
      { id: 'create-arc', label: '+ Nouvel arc', variant: 'primary', route: `/campaigns/${campaignId}/arcs/create` }
    ],
    globalItems,
    globalBackLabel: 'Toutes les campagnes',
    globalBackRoute: '/campaigns'
  };
}
