import { describe, expect, it } from 'vitest';
import { CampaignContentHit, campaignContentRoute, campaignContentContext } from './campaign-content-search.model';

const hit: CampaignContentHit = { kind: 'scene', id: '4', name: 'Le témoin', campaignId: '1',
  campaignName: 'Les Ombres', arcId: '2', arcName: 'Le port', chapterId: '3', chapterName: 'La nuit' };

describe('campaign search destinations', () => {
  it('resolves each entity even when IDs overlap between types', () => {
    expect(campaignContentRoute(hit)).toEqual(['/campaigns', '1', 'arcs', '2', 'chapters', '3', 'scenes', '4']);
    expect(campaignContentRoute({ ...hit, kind: 'chapter' })).toEqual(['/campaigns', '1', 'arcs', '2', 'chapters', '4']);
    expect(campaignContentRoute({ ...hit, kind: 'arc' })).toEqual(['/campaigns', '1', 'arcs', '4']);
    expect(campaignContentRoute({ ...hit, kind: 'quest', arcId: null })).toEqual(['/campaigns', '1', 'quests', '4']);
  });
  it('keeps a system arc in the destination but out of the visible context', () => {
    expect(campaignContentContext({ ...hit, arcName: null })).toBe('Les Ombres › La nuit');
    expect(campaignContentContext(hit)).toBe('Les Ombres › Le port › La nuit');
    expect(campaignContentRoute({ ...hit, chapterId: null })).toEqual([]);
  });
});
