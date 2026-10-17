import { describe, it, expect } from 'vitest';
import { computePlayTarget, sortPlaythroughsRecentFirst } from './play-target.util';
import { Playthrough } from '../../../services/campaign.model';
import { Session } from '../../../services/session.model';

const pt = (id: string, createdAt?: string): Playthrough => ({ id, campaignId: 'c1', name: `P${id}`, createdAt });
const session = (id: string, playthroughId: string, startedAt: string, active = true): Session => ({
  id, name: `S${id}`, playthroughId, startedAt, endedAt: active ? null : startedAt,
  createdAt: startedAt, updatedAt: startedAt, active
});

describe('sortPlaythroughsRecentFirst', () => {
  it('trie par date de création décroissante', () => {
    const r = sortPlaythroughsRecentFirst([pt('1', '2026-01-01'), pt('2', '2026-03-01'), pt('3', '2026-02-01')]);
    expect(r.map(p => p.id)).toEqual(['2', '3', '1']);
  });

  it('sans dates : la dernière de la liste est la plus récente', () => {
    expect(sortPlaythroughsRecentFirst([pt('1'), pt('2')]).map(p => p.id)).toEqual(['2', '1']);
  });
});

describe('computePlayTarget', () => {
  it('aucune partie → create', () => {
    expect(computePlayTarget([], [])).toEqual({ kind: 'create' });
  });

  it('pas de séance en cours → start sur la partie la plus récente', () => {
    const t = computePlayTarget([pt('1', '2026-01-01'), pt('2', '2026-05-01')], []);
    expect(t.kind).toBe('start');
    if (t.kind === 'start') {
      expect(t.defaultPlaythroughId).toBe('2');
      expect(t.playthroughs.map(p => p.id)).toEqual(['2', '1']);
    }
  });

  it('ignore les séances des autres campagnes et les séances terminées', () => {
    const t = computePlayTarget([pt('1')], [session('a', 'other', '2026-01-01'), session('b', '1', '2026-01-01', false)]);
    expect(t.kind).toBe('start');
  });

  it('séance(s) en cours sur la campagne → resume, la plus récente d\'abord', () => {
    const t = computePlayTarget([pt('1'), pt('2')], [
      session('a', '1', '2026-01-01T10:00:00'),
      session('b', '2', '2026-01-02T10:00:00'),
      session('c', 'other', '2026-01-03T10:00:00')
    ]);
    expect(t.kind).toBe('resume');
    if (t.kind === 'resume') {
      expect(t.sessions.map(s => s.session.id)).toEqual(['b', 'a']);
      expect(t.sessions[0].playthrough.id).toBe('2');
    }
  });
});
