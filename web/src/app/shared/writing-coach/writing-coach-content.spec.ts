import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

/**
 * Garde-fou du contenu du coach d'écriture : chaque sujet référencé dans un
 * template (`topic="…"` ou `coachTopic="…"`) doit exister en fr ET en en, avec
 * ses 4 rubriques renseignées. Sinon le 💡 ouvrirait un panneau vide.
 */
const APP_DIR = join(process.cwd(), 'src/app');
const FRAGMENTS_DIR = join(process.cwd(), 'src/assets/i18n/fragments');

function htmlFiles(dir: string): string[] {
  return readdirSync(dir).flatMap(name => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return htmlFiles(path);
    return name.endsWith('.html') ? [path] : [];
  });
}

function referencedTopics(): string[] {
  const topics = new Set<string>();
  for (const file of htmlFiles(APP_DIR)) {
    const html = readFileSync(file, 'utf-8');
    for (const m of html.matchAll(/<app-writing-coach-trigger[^>]*\stopic="([^"]+)"/g)) topics.add(m[1]);
    for (const m of html.matchAll(/\scoachTopic="([^"]+)"/g)) topics.add(m[1]);
  }
  return [...topics].sort();
}

function topicsOf(lang: 'fr' | 'en'): Record<string, Record<string, Record<string, unknown>>> {
  const json = JSON.parse(readFileSync(join(FRAGMENTS_DIR, `writing-coach.${lang}.json`), 'utf-8'));
  return json.writingCoach.topics;
}

function lookup(lang: 'fr' | 'en', key: string): Record<string, unknown> | undefined {
  const [entity, field] = key.split('.');
  return topicsOf(lang)[entity]?.[field];
}

describe('writing coach content', () => {
  const topics = referencedTopics();

  it('is referenced by the editors', () => {
    expect(topics.length).toBeGreaterThan(0);
  });

  for (const lang of ['fr', 'en'] as const) {
    it.each(topics)(`${lang}: topic %s is complete`, key => {
      const topic = lookup(lang, key);
      expect(topic, `writingCoach.topics.${key} manquant (${lang})`).toBeDefined();
      for (const field of ['title', 'purpose', 'example', 'pitfall']) {
        expect(typeof topic![field] === 'string' && (topic![field] as string).trim().length > 0,
          `${key}.${field} vide (${lang})`).toBe(true);
      }
      const questions = topic!['questions'];
      expect(Array.isArray(questions) && questions.length >= 2, `${key}.questions < 2 (${lang})`).toBe(true);
    });
  }

  it('has the same topics in fr and en', () => {
    const keys = (lang: 'fr' | 'en') => Object.entries(topicsOf(lang))
      .flatMap(([entity, fields]) => Object.keys(fields).map(f => `${entity}.${f}`)).sort();
    expect(keys('en')).toEqual(keys('fr'));
  });
});
