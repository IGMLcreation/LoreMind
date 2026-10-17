import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { remarkAction } from '../../campaigns/writing-remark-action.helper';
import { WritingRemark } from '../../services/writing-review.model';

/**
 * Garde-fou de la relecture d'éditeur : chaque règle émise par le Core
 * (WritingReviewService.java) doit avoir ses 3 textes en fr ET en en, sinon la
 * carte afficherait des clés brutes.
 */
const SERVICE = join(process.cwd(), '../core/src/main/java/com/loremind/application/campaigncontext/WritingReviewService.java');
const FRAGMENTS_DIR = join(process.cwd(), 'src/assets/i18n/fragments');

function emittedRules(): string[] {
  const java = readFileSync(SERVICE, 'utf-8');
  return [...new Set([...java.matchAll(/"(WRITE-\d{3}-[A-Z-]+)"/g)].map(m => m[1]))].sort();
}

function rulesOf(lang: 'fr' | 'en'): Record<string, Record<string, string>> {
  return JSON.parse(readFileSync(join(FRAGMENTS_DIR, `writing-review.${lang}.json`), 'utf-8')).writingReview.rules;
}

describe('writing review content', () => {
  const rules = emittedRules();

  it('finds the rules emitted by the core', () => {
    expect(rules).toHaveLength(17);
  });

  for (const lang of ['fr', 'en'] as const) {
    it.each(rules)(`${lang}: rule %s has its three texts`, ruleId => {
      const texts = rulesOf(lang)[ruleId];
      expect(texts, `${ruleId} manquant (${lang})`).toBeDefined();
      for (const part of ['observation', 'why', 'question']) {
        expect(texts[part]?.trim().length ?? 0, `${ruleId}.${part} vide (${lang})`).toBeGreaterThan(0);
      }
    });
  }

  it('has no orphan rule text', () => {
    expect(Object.keys(rulesOf('fr')).sort()).toEqual(rules);
    expect(Object.keys(rulesOf('en')).sort()).toEqual(rules);
  });
});

describe('remarkAction', () => {
  const base: WritingRemark = {
    entityType: 'SCENE', entityId: 's1', ruleId: 'WRITE-007-NARRATION-DECIDES',
    category: 'NARRATION', params: {}, arcId: 'a1', chapterId: 'c1'
  };

  it('opens the scene editor on the faulty section', () => {
    expect(remarkAction(base, 'camp')).toEqual({
      link: ['/campaigns', 'camp', 'arcs', 'a1', 'chapters', 'c1', 'scenes', 's1', 'edit'],
      queryParams: { focus: 'narration' }
    });
  });

  it('opens the chapter view for a corridor, the chapter editor for missing stakes', () => {
    const chapter = { ...base, entityType: 'CHAPTER' as const, entityId: 'c1' };
    expect(remarkAction({ ...chapter, ruleId: 'WRITE-001-CORRIDOR' }, 'camp').link)
      .toEqual(['/campaigns', 'camp', 'arcs', 'a1', 'chapters', 'c1']);
    expect(remarkAction({ ...chapter, ruleId: 'WRITE-005-NO-STAKES' }, 'camp')).toEqual({
      link: ['/campaigns', 'camp', 'arcs', 'a1', 'chapters', 'c1', 'edit'],
      queryParams: { focus: 'stakes' }
    });
  });

  it('falls back to the campaign hub for campaign-wide remarks', () => {
    const r = { ...base, entityType: 'CAMPAIGN' as const, entityId: 'camp', ruleId: 'WRITE-010-UNTYPED-SCENES' };
    expect(remarkAction(r, 'camp').link).toEqual(['/campaigns', 'camp']);
  });
});

describe('remarkAction for NPCs and hooks', () => {
  const base: WritingRemark = {
    entityType: 'NPC', entityId: 'n1', ruleId: 'WRITE-016-NPC-NO-MOTIVATION',
    category: 'STAKES', params: { role: 'OPPONENT' }, arcId: null, chapterId: null
  };

  it('opens the NPC editor', () => {
    expect(remarkAction(base, 'camp')).toEqual({ link: ['/campaigns', 'camp', 'npcs', 'n1', 'edit'] });
  });

  it('opens the arc editor on the hooks section', () => {
    expect(remarkAction({ ...base, entityType: 'ARC', entityId: 'a1', ruleId: 'WRITE-017-SINGLE-HOOK' }, 'camp'))
      .toEqual({ link: ['/campaigns', 'camp', 'arcs', 'a1', 'edit'], queryParams: { focus: 'hooks' } });
  });
});
