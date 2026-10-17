import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { GuidedField, GuidedStep } from './guided-method.model';
import { GUIDED_METHODS } from './guided-methods';
import { FIVE_ROOM_METHOD } from './methods/five-room.method';
import { INVESTIGATION_METHOD } from './methods/investigation.method';
import { VILLAIN_PLAN_METHOD } from './methods/villain-plan.method';

/**
 * Garde-fous des méthodes guidées : chaque étape / champ / option / valeur par défaut
 * a son texte en fr ET en en (sinon l'assistant afficherait des clés brutes), et la
 * méthode « 5 salles » produit le bon squelette.
 */
const FRAGMENTS_DIR = join(process.cwd(), 'src/assets/i18n/fragments');

function i18n(lang: 'fr' | 'en'): Record<string, unknown> {
  return JSON.parse(readFileSync(join(FRAGMENTS_DIR, `guided.${lang}.json`), 'utf-8')).guided;
}

function lookup(root: Record<string, unknown>, path: string): unknown {
  return path.split('.').reduce<unknown>((node, part) =>
    node && typeof node === 'object' ? (node as Record<string, unknown>)[part] : undefined, root);
}

function fieldKeys(p: string, f: GuidedField): string[] {
  return [
    `${p}.fields.${f.key}.label`,
    ...(f.kind === 'choice' ? [] : [`${p}.fields.${f.key}.placeholder`]),
    ...(f.options ?? []).map(o => `${p}.options.${f.key}.${o}`),
    ...(f.prefilled ? [`${p}.defaults.${f.key}`] : [])
  ];
}

function stepKeys(p: string, step: GuidedStep): string[] {
  return [
    `${p}.steps.${step.id}.title`,
    `${p}.steps.${step.id}.why`,
    ...(step.kind === 'review' ? [] : [`${p}.steps.${step.id}.example`]),
    ...(step.fields ?? []).flatMap(f => fieldKeys(p, f))
  ];
}

function requiredKeys(): string[] {
  return GUIDED_METHODS.flatMap(m => {
    const draft = m.toDraft({});
    return [
      `${m.i18nKey}.name`, `${m.i18nKey}.tagline`,
      ...m.steps.flatMap(s => stepKeys(m.i18nKey, s)),
      ...(draft.links?.length ? [`${m.i18nKey}.reviewLinks`] : []),
      ...[...new Set((draft.links ?? []).map(l => l.labelKey))].map(k => `${m.i18nKey}.links.${k}`)
    ];
  });
}

describe('guided methods content', () => {
  const keys = requiredKeys();

  for (const lang of ['fr', 'en'] as const) {
    it.each(keys)(`${lang}: guided.%s is a non-empty text`, key => {
      const value = lookup(i18n(lang), key);
      expect(typeof value === 'string' && value.trim().length > 0, `guided.${key} (${lang})`).toBe(true);
    });
  }

  it('has unique method ids and step ids', () => {
    expect(new Set(GUIDED_METHODS.map(m => m.id)).size).toBe(GUIDED_METHODS.length);
    for (const m of GUIDED_METHODS) {
      expect(new Set(m.steps.map(s => s.id)).size).toBe(m.steps.length);
    }
  });
});

describe('five-room method', () => {
  const answers = {
    title: ' Le reliquaire volé ',
    location: 'La crypte',
    hook: 'Le prêtre a disparu',
    objectives: 'Ramener la relique',
    stakes: '',
    'room1.name': "L'entrée gardée",
    'room1.nature': 'social',
    'room1.choices': 'Convaincre ou ruser',
    'room2.name': "L'épreuve",
    'room2.nature': 'puzzle',
    'room3.name': 'Le revers',
    'room4.name': 'Le climax',
    'room4.enemies': "L'envoûteuse",
    'room5.name': 'La récompense',
    'room5.secret': 'Le symbole de la confrérie'
  };

  it('builds the chapter from the frame and stakes answers', () => {
    expect(FIVE_ROOM_METHOD.toDraft(answers).chapter).toEqual({
      name: 'Le reliquaire volé',
      description: 'Le prêtre a disparu',
      playerObjectives: 'Ramener la relique',
      narrativeStakes: null
    });
  });

  it('builds five typed scenes in the same place', () => {
    const scenes = FIVE_ROOM_METHOD.toDraft(answers).scenes;
    expect(scenes.map(s => s.type)).toEqual(['NPC', 'LOCATION', 'EVENT', 'ENCOUNTER', 'REVELATION']);
    expect(scenes.every(s => s.location === 'La crypte')).toBe(true);
    expect(scenes[0].choicesConsequences).toBe('Convaincre ou ruser');
    expect(scenes[3].enemies).toBe("L'envoûteuse");
    expect(scenes[4].gmSecretNotes).toBe('Le symbole de la confrérie');
  });

  it('defaults room 1 to a fight when no nature was chosen', () => {
    expect(FIVE_ROOM_METHOD.toDraft({}).scenes[0].type).toBe('ENCOUNTER');
  });
});

describe('investigation method', () => {
  const draft = INVESTIGATION_METHOD.toDraft({
    title: "L'héritage empoisonné", truth: 'Le notaire est coupable', stakes: 'Le palefrenier sera pendu',
    'hook.name': "L'arrestation", 'hook.description': 'Le palefrenier est arrêté',
    'node1.name': 'Le bureau', 'node1.nature': 'location',
    'node2.name': 'La servante', 'node2.nature': 'npc',
    'node3.name': "L'enterrement", 'node3.nature': 'event',
    'conclusion.name': 'La fuite', 'proactive.name': "L'incendie"
  });

  it('keeps the hidden truth in the GM notes, the hook as the chapter description', () => {
    expect(draft.chapter.gmNotes).toBe('Le notaire est coupable');
    expect(draft.chapter.description).toBe('Le palefrenier est arrêté');
  });

  it('builds six typed scenes: hook, three varied leads, conclusion, fallback event', () => {
    expect(draft.scenes.map(s => s.type)).toEqual(['EVENT', 'LOCATION', 'NPC', 'EVENT', 'REVELATION', 'EVENT']);
  });

  it('links the conclusion from three leads, with no single point of failure', () => {
    const into = (i: number) => draft.links!.filter(l => l.to === i);
    expect(into(4).map(l => l.from).sort()).toEqual([1, 2, 3]);
    expect(into(4).every(l => l.kind === 'CLUE')).toBe(true);
    // Chaque piste est atteinte depuis l'accroche, une autre piste et la relance.
    for (const lead of [1, 2, 3]) expect(new Set(into(lead).map(l => l.from)).size).toBe(3);
    expect(draft.links!.some(l => l.from === l.to)).toBe(false);
    expect(draft.noChain).toBeFalsy();
  });

  it('offers varied lead natures by default', () => {
    const empty = INVESTIGATION_METHOD.steps.filter(s => s.id.startsWith('node'))
      .map(s => s.fields!.find(f => f.kind === 'choice')!.options![0]);
    expect(new Set(empty).size).toBe(3);
  });
});

describe('villain plan method', () => {
  const answers = {
    title: "La régence d'Ysolde", villain: 'Dame Ysolde', goal: 'Le trône du prince',
    stakes: 'La loi martiale', clockName: 'La régence',
    'step1.name': 'Le capitaine remplacé', 'step1.action': 'Elle place un homme à elle',
    'step6.name': 'Le couronnement'
  };

  it('writes the whole plan in the GM notes and the villain in the description', () => {
    const draft = VILLAIN_PLAN_METHOD.toDraft(answers);
    expect(draft.chapter.description).toBe('Dame Ysolde\n\nLe trône du prince');
    expect(draft.chapter.gmNotes).toBe('1. Le capitaine remplacé — Elle place un homme à elle\n2. Le couronnement');
  });

  it('creates six unchained scenes with a varied rhythm by default', () => {
    const draft = VILLAIN_PLAN_METHOD.toDraft(answers);
    expect(draft.scenes).toHaveLength(6);
    expect(draft.noChain).toBe(true);
    expect(draft.links).toBeUndefined();
    expect(draft.scenes.map(s => s.type)).toEqual(['EVENT', 'NPC', 'LOCATION', 'EVENT', 'NPC', 'ENCOUNTER']);
  });

  it('adds a six-segment clock unless the GM declines it', () => {
    expect(VILLAIN_PLAN_METHOD.toDraft(answers).clock).toEqual({ name: 'La régence', description: 'La loi martiale', segments: 6 });
    expect(VILLAIN_PLAN_METHOD.toDraft({ ...answers, clockWanted: 'no' }).clock).toBeUndefined();
  });
});
