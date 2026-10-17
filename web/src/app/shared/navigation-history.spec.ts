import { describe, expect, it } from 'vitest';
import { contentUrl, readHistory, rememberVisit, toggleFavorite } from './navigation-history';

const scene = { url: '/campaigns/1/arcs/2/chapters/3/scenes/4', title: 'La taverne', subtitle: 'Les Ombres' };

describe('navigation shortcuts', () => {
  it('keeps a bounded, deduplicated recent list and refreshes renamed favorites', () => {
    let state = toggleFavorite({ recent: [], favorites: [] }, scene);
    for (let i = 0; i < 20; i++) state = rememberVisit(state, { ...scene, url: '/lore/' + i });
    state = rememberVisit(state, scene);
    state = rememberVisit(state, { ...scene, title: 'La nouvelle taverne' });
    expect(state.recent).toHaveLength(12);
    expect(state.recent[0].title).toBe('La nouvelle taverne');
    expect(state.favorites[0].title).toBe('La nouvelle taverne');
    expect(state.recent.filter(item => item.url === scene.url)).toHaveLength(1);
  });
  it('removes a favorite without removing its recent visit', () => {
    const visited = rememberVisit({ recent: [], favorites: [] }, scene);
    expect(toggleFavorite(toggleFavorite(visited, scene), scene)).toEqual(visited);
  });
  it('ignores broken storage, unsafe URLs and duplicate entries', () => {
    expect(readHistory('{broken')).toEqual({ recent: [], favorites: [] });
    const saved = readHistory(JSON.stringify({ recent: [scene, scene, { ...scene, url: 'https://example.com' }, null], favorites: [scene] }));
    expect(saved).toEqual({ recent: [scene], favorites: [scene] });
  });
  it('never records creation or editing forms and drops query parameters', () => {
    expect(contentUrl(scene.url + '?edit=1#notes')).toBe(scene.url);
    for (const url of [scene.url + '/edit', '/lore/1/pages/create', '/campaigns/1/guided/five-room', '//evil.test', '/settings']) {
      expect(contentUrl(url)).toBeNull();
    }
  });
});
