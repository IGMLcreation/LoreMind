import { Playthrough } from '../../../services/campaign.model';
import { Session } from '../../../services/session.model';

/**
 * Ce que propose le bloc « Jouer » du hub campagne :
 * - resume : une (ou plusieurs) séance(s) tourne(nt) sur une Partie de la campagne ;
 * - start  : aucune séance en cours → démarrer sur une Partie (la plus récente par défaut) ;
 * - create : aucune Partie → en créer une d'abord.
 */
export type PlayTarget =
  | { kind: 'resume'; sessions: { session: Session; playthrough: Playthrough }[] }
  | { kind: 'start'; playthroughs: Playthrough[]; defaultPlaythroughId: string }
  | { kind: 'create' };

const timeOf = (p: Playthrough): number => {
  const t = Date.parse(p.createdAt ?? p.updatedAt ?? '');
  return Number.isNaN(t) ? 0 : t;
};

/** Parties de la plus récente à la plus ancienne (création ; à défaut, ordre d'origine). */
export function sortPlaythroughsRecentFirst(playthroughs: Playthrough[]): Playthrough[] {
  return playthroughs
    .map((p, i) => ({ p, i }))
    .sort((a, b) => (timeOf(b.p) - timeOf(a.p)) || (b.i - a.i))
    .map(x => x.p);
}

/**
 * Décide du bloc « Jouer » à partir des Parties de la campagne et des séances en cours
 * de TOUTE l'appli (filtrées ici sur les Parties de la campagne, plus récente d'abord).
 */
export function computePlayTarget(playthroughs: Playthrough[], activeSessions: Session[]): PlayTarget {
  const withId = playthroughs.filter((p): p is Playthrough & { id: string } => !!p.id);
  if (withId.length === 0) return { kind: 'create' };

  const byId = new Map(withId.map(p => [p.id, p]));
  const running = activeSessions
    .filter(s => s.active && byId.has(s.playthroughId))
    .sort((a, b) => Date.parse(b.startedAt) - Date.parse(a.startedAt))
    .map(session => ({ session, playthrough: byId.get(session.playthroughId)! }));
  if (running.length > 0) return { kind: 'resume', sessions: running };

  const sorted = sortPlaythroughsRecentFirst(withId);
  return { kind: 'start', playthroughs: sorted, defaultPlaythroughId: sorted[0].id! };
}
