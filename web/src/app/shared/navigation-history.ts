export interface NavigationEntry {
  url: string;
  title: string;
  subtitle: string;
}

export interface NavigationHistory {
  recent: NavigationEntry[];
  favorites: NavigationEntry[];
}

/** Only saved content pages, never forms, query parameters or external URLs. */
export function contentUrl(url: string): string | null {
  const path = url.split(/[?#]/)[0];
  if (!/^\/(campaigns|lore|sessions)\/[a-zA-Z0-9_-]+(?:\/[a-zA-Z0-9_-]+)*$/.test(path)) return null;
  if (/(?:^|\/)(create|edit|import|guided|publication|flags)(?:\/|$)/.test(path)) return null;
  return path;
}

export function readHistory(raw: string | null): NavigationHistory {
  const empty: NavigationHistory = { recent: [], favorites: [] };
  try {
    const data: unknown = JSON.parse(raw ?? 'null');
    if (!data || typeof data !== 'object') return empty;
    const clean = (value: unknown): NavigationEntry[] => {
      if (!Array.isArray(value)) return [];
      const seen = new Set<string>();
      return value.filter((entry): entry is NavigationEntry => {
        if (!entry || typeof entry.url !== 'string' || contentUrl(entry.url) !== entry.url ||
          typeof entry.title !== 'string' || !entry.title.trim() || typeof entry.subtitle !== 'string' || seen.has(entry.url)) return false;
        seen.add(entry.url);
        return true;
      });
    };
    const saved = data as Partial<NavigationHistory>;
    return { recent: clean(saved.recent).slice(0, 12), favorites: clean(saved.favorites) };
  } catch { return empty; }
}

export function rememberVisit(history: NavigationHistory, entry: NavigationEntry): NavigationHistory {
  return {
    recent: [entry, ...history.recent.filter(item => item.url !== entry.url)].slice(0, 12),
    favorites: history.favorites.map(item => item.url === entry.url ? entry : item)
  };
}

export function toggleFavorite(history: NavigationHistory, entry: NavigationEntry): NavigationHistory {
  const exists = history.favorites.some(item => item.url === entry.url);
  return { ...history, favorites: exists
    ? history.favorites.filter(item => item.url !== entry.url)
    : [...history.favorites, entry] };
}
