import { Arc, Chapter } from '../services/campaign.model';

/**
 * Mode « plat » (Niveau 0) : une campagne d'un seul arc linéaire et d'un seul chapitre
 * présente directement ses scènes, sans les niveaux arc et chapitre. Seulement si ces deux
 * conteneurs sont VIDES (créés d'office pour poser une première scène) : dès que l'auteur y
 * a écrit quelque chose — à la main ou via une méthode guidée (titre, accroche, vérité
 * cachée, plan du méchant…) — on les montre, sinon ce contenu deviendrait introuvable.
 * Même règle dans le volet, sur la page de la campagne et dans le livret PDF (core).
 */
export function flatContainer(arcs: Arc[], chaptersByArc: Record<string, Chapter[]>): { arc: Arc; chapter: Chapter } | null {
  const visible = arcs.filter(a => a.type !== 'SYSTEM');
  if (visible.length !== 1) return null;
  const arc = visible[0];
  if (arc.type === 'HUB') return null; // un arc HUB présente des quêtes, pas une simple liste de scènes
  const chapters = chaptersByArc[arc.id!] ?? [];
  if (chapters.length !== 1) return null;
  const chapter = chapters[0];
  return isBareArc(arc) && isBareChapter(chapter) ? { arc, chapter } : null;
}

/** Aucun contenu d'écriture dans l'arc (le nom ne compte pas : « Arc principal » est créé d'office). */
export function isBareArc(arc: Arc): boolean {
  return [arc.description, arc.themes, arc.stakes, arc.rewards, arc.resolution, arc.gmNotes, arc.hiddenTruth, arc.hooks]
    .every(blank) && !(arc.illustrationImageIds?.length);
}

/** Aucun contenu d'écriture dans le chapitre (le nom ne compte pas : « Chapitre 1 » est créé d'office). */
export function isBareChapter(chapter: Chapter): boolean {
  return [chapter.description, chapter.playerObjectives, chapter.narrativeStakes, chapter.gmNotes]
    .every(blank) && !(chapter.illustrationImageIds?.length);
}

function blank(value: string | null | undefined): boolean {
  return !value || !value.trim();
}
