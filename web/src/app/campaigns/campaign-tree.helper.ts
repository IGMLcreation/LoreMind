import { Observable, forkJoin, of } from 'rxjs';
import { switchMap, map } from 'rxjs/operators';
import { CampaignService } from '../services/campaign.service';
import { CharacterService } from '../services/character.service';
import { TreeItem } from '../services/layout.service';
import { Arc, Chapter, Scene } from '../services/campaign.model';
import { Character } from '../services/character.model';

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
}

export function loadCampaignTreeData(
  service: CampaignService,
  campaignId: string,
  characterService: CharacterService
): Observable<CampaignTreeData> {
  return forkJoin({
    arcs: service.getArcs(campaignId),
    characters: characterService.getByCampaign(campaignId)
  }).pipe(
    switchMap(({ arcs, characters }) => {
      if (arcs.length === 0) {
        return of({ arcs, chaptersByArc: {}, scenesByChapter: {}, characters });
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
            return of({ arcs, chaptersByArc, scenesByChapter: {}, characters });
          }
          const sceneCalls = allChapters.map(c =>
            service.getScenes(c.id!).pipe(map(scenes => ({ chapterId: c.id!, scenes })))
          );
          return forkJoin(sceneCalls).pipe(
            map(sceneResults => {
              const scenesByChapter: Record<string, Scene[]> = {};
              sceneResults.forEach(r => { scenesByChapter[r.chapterId] = r.scenes; });
              return { arcs, chaptersByArc, scenesByChapter, characters };
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
  const sortedCharacters = [...data.characters].sort(byName);
  const characterItems: TreeItem[] = sortedCharacters.map(ch => ({
    id: `character-${ch.id}`,
    label: ch.name,
    route: `/campaigns/${campaignId}/characters/${ch.id}/edit`
  }));

  const charactersNode: TreeItem = {
    id: 'characters-root',
    label: 'Personnages',
    iconKey: 'users',
    children: characterItems,
    meta: characterItems.length ? String(characterItems.length) : undefined,
    sectionHeaderBefore: 'Personnages',
    // Note : si pas d'arcs, le filet au-dessus de "Personnages" est masqué par CSS
    // (:first-child), ce qui est voulu — on ne veut pas de ligne seule en haut.
    createActions: [{
      id: 'new-character',
      label: 'Nouveau PJ',
      route: `/campaigns/${campaignId}/characters/create`,
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
        label: 'Nouveau chapitre',
        route: `/campaigns/${campaignId}/arcs/${arc.id}/chapters/create`,
        actionIcon: 'plus'
      }]
    };
  });

  return [...arcNodes, charactersNode];
}
