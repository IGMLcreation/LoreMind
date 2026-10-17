export interface CampaignContentHit {
  kind: 'arc' | 'chapter' | 'scene' | 'quest';
  id: string;
  name: string;
  campaignId: string;
  campaignName: string;
  arcId: string | null;
  arcName: string | null;
  chapterId: string | null;
  chapterName: string | null;
}

export function campaignContentRoute(hit: CampaignContentHit): string[] {
  const root = ['/campaigns', hit.campaignId];
  if (hit.kind === 'quest') return [...root, 'quests', hit.id];
  if (hit.kind === 'arc') return [...root, 'arcs', hit.id];
  if (!hit.arcId) return [];
  const arc = [...root, 'arcs', hit.arcId];
  if (hit.kind === 'chapter') return [...arc, 'chapters', hit.id];
  return hit.chapterId ? [...arc, 'chapters', hit.chapterId, 'scenes', hit.id] : [];
}

export function campaignContentContext(hit: CampaignContentHit): string {
  return [hit.campaignName, hit.kind !== 'arc' ? hit.arcName : null,
    hit.kind === 'scene' ? hit.chapterName : null].filter(Boolean).join(' › ');
}
