import { Observable, forkJoin, of } from 'rxjs';
import { switchMap, map } from 'rxjs/operators';
import { CampaignService } from '../services/campaign.service';
import { TreeItem } from '../services/layout.service';
import { Arc, Chapter, Scene } from '../services/campaign.model';

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
}

export function loadCampaignTreeData(
  service: CampaignService,
  campaignId: string
): Observable<CampaignTreeData> {
  return service.getArcs(campaignId).pipe(
    switchMap(arcs => {
      if (arcs.length === 0) {
        return of({ arcs, chaptersByArc: {}, scenesByChapter: {} });
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
            return of({ arcs, chaptersByArc, scenesByChapter: {} });
          }
          const sceneCalls = allChapters.map(c =>
            service.getScenes(c.id!).pipe(map(scenes => ({ chapterId: c.id!, scenes })))
          );
          return forkJoin(sceneCalls).pipe(
            map(sceneResults => {
              const scenesByChapter: Record<string, Scene[]> = {};
              sceneResults.forEach(r => { scenesByChapter[r.chapterId] = r.scenes; });
              return { arcs, chaptersByArc, scenesByChapter };
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
  const sortedArcs = [...data.arcs].sort(byName);

  return sortedArcs.map(arc => {
    const sortedChapters = [...(data.chaptersByArc[arc.id!] ?? [])].sort(byName);

    const chapterItems: TreeItem[] = sortedChapters.map(ch => {
      const sortedScenes = [...(data.scenesByChapter[ch.id!] ?? [])].sort(byName);

      const sceneItems: TreeItem[] = sortedScenes.map(sc => ({
        id: `scene-${sc.id}`,
        label: sc.name,
        route: `/campaigns/${campaignId}/arcs/${arc.id}/chapters/${ch.id}/scenes/${sc.id}`
      }));
      sceneItems.push({
        id: `new-scene-${ch.id}`,
        label: '+ Nouvelle scène',
        isAction: true,
        route: `/campaigns/${campaignId}/arcs/${arc.id}/chapters/${ch.id}/scenes/create`
      });
      return {
        id: `chapter-${ch.id}`,
        label: ch.name,
        children: sceneItems,
        route: `/campaigns/${campaignId}/arcs/${arc.id}/chapters/${ch.id}`
      };
    });
    chapterItems.push({
      id: `new-chapter-${arc.id}`,
      label: '+ Nouveau chapitre',
      isAction: true,
      route: `/campaigns/${campaignId}/arcs/${arc.id}/chapters/create`
    });
    return {
      id: `arc-${arc.id}`,
      label: arc.name,
      children: chapterItems,
      route: `/campaigns/${campaignId}/arcs/${arc.id}`
    };
  });
}
