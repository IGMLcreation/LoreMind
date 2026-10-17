import { describe, expect, it } from 'vitest';
import { campaignBreadcrumbs, isWithinRoute } from './navigation-path';
import type { SecondarySidebarConfig } from '../services/layout.service';

const root = '/campaigns/1';
const arc = root + '/arcs/2';
const chapter = arc + '/chapters/3';
const scene = chapter + '/scenes/4';
const config: SecondarySidebarConfig = {
  title: 'Les Ombres', titleRoute: root, globalItems: [], globalBackLabel: '', globalBackRoute: '/campaigns',
  createActions: [{ id: 'arc', label: 'Nouvel arc', variant: 'primary', route: root + '/arcs/create' }],
  items: [{ id: 'arc', label: 'Le port', route: arc, children: [
    { id: 'chapter', label: 'La nuit', route: chapter, children: [
      { id: 'scene', label: 'La taverne', route: scene }
    ], createActions: [{ id: 'scene', label: 'Nouvelle scène', route: chapter + '/scenes/create' }] }
  ] }]
};

describe('navigation context', () => {
  it('keeps the current campaign selected without matching a different numeric ID', () => {
    expect(isWithinRoute(scene + '?tab=notes#title', root)).toBe(true);
    expect(isWithinRoute('/campaigns/10/arcs/2', root)).toBe(false);
    expect(isWithinRoute('/lore/1', root)).toBe(false);
  });
  it('allows returning from a scene to each named ancestor', () => {
    expect(campaignBreadcrumbs(scene, config, 'Modifier')).toEqual([
      { label: 'Les Ombres', route: root }, { label: 'Le port', route: arc },
      { label: 'La nuit', route: chapter }, { label: 'La taverne', route: scene }
    ]);
  });
  it('links back to the scene while editing and to the parent while creating', () => {
    expect(campaignBreadcrumbs(scene + '/edit', config, 'Modifier').at(-1)).toEqual({ label: 'Modifier' });
    expect(campaignBreadcrumbs(chapter + '/scenes/create', config, 'Modifier').map(i => i.label))
      .toEqual(['Les Ombres', 'Le port', 'La nuit', 'Nouvelle scène']);
    expect(campaignBreadcrumbs(root + '/arcs/create', config, 'Modifier').map(i => i.label))
      .toEqual(['Les Ombres', 'Nouvel arc']);
  });
  it('preserves the visible quest hierarchy instead of revealing its system arc', () => {
    const questConfig = { ...config, items: [{ id: 'quests', label: 'Quêtes', children: [
      { id: 'quest', label: 'Le témoin', route: root + '/quests/5', children: [
        { id: 'scene', label: 'La taverne', route: scene }
      ] }
    ] }] };
    expect(campaignBreadcrumbs(scene, questConfig, 'Modifier').map(i => i.label))
      .toEqual(['Les Ombres', 'Quêtes', 'Le témoin', 'La taverne']);
  });
  it('uses the scenes graph as parent for a flattened campaign', () => {
    const flat = { ...config, items: [{ id: 'chapter', label: 'Scènes', route: chapter + '/graph', children: [
      { id: 'scene', label: 'La taverne', route: scene }
    ] }] };
    expect(campaignBreadcrumbs(scene, flat, 'Modifier')[1]).toEqual({ label: 'Scènes', route: chapter + '/graph' });
  });
  it('does not show stale context or invent a title for an unknown page', () => {
    for (const path of [root, '/campaigns/10/arcs/2', root + '/npcs/9', '/lore/1']) {
      expect(campaignBreadcrumbs(path, config, 'Modifier')).toEqual([]);
    }
  });
});
