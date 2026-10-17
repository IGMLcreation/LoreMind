import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { GuidedField, GuidedStep } from './guided-method.model';
import { GUIDED_METHODS } from './guided-methods';
import { FIVE_ROOM_METHOD } from './methods/five-room.method';

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
  return GUIDED_METHODS.flatMap(m => [`${m.i18nKey}.name`, ...m.steps.flatMap(s => stepKeys(m.i18nKey, s))]);
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
