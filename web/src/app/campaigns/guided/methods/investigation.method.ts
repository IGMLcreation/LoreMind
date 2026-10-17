import { SceneType } from '../../../services/campaign.model';
import { GuidedSceneSpec } from '../../../services/guided-skeleton.model';
import { GuidedAnswers, GuidedDraftLink, GuidedField, GuidedMethod, answer } from '../guided-method.model';

/**
 * « Enquête en nœuds » (principe des scénarios d'enquête non linéaires ; textes écrits
 * pour LoreMind). Au lieu d'une chaîne d'indices où un seul raté bloque tout, l'enquête
 * est un réseau : une accroche ouvre trois pistes qui se renvoient les unes aux autres et
 * mènent toutes à la conclusion, plus une scène que le MJ déclenche si la table piétine.
 *
 * Produit 1 chapitre (vérité cachée en notes MJ) + 6 scènes typées et reliées :
 *   accroche → pistes 1, 2, 3 (pistes) ; 1 → 2 → 3 → 1 (pistes) ;
 *   pistes 1, 2, 3 → conclusion (indices) ; relance → pistes 1, 2, 3.
 * La conclusion est donc atteinte par trois chemins (règle des 3 indices) et aucune
 * scène n'est un passage obligé.
 */

const HOOK = 0;
const NODES = [1, 2, 3];
const CONCLUSION = 4;
const PROACTIVE = 5;

const NATURE_TO_TYPE: Record<string, SceneType> = {
  location: 'LOCATION',
  npc: 'NPC',
  event: 'EVENT',
  encounter: 'ENCOUNTER',
  revelation: 'REVELATION'
};

/** Natures proposées pour chaque piste, dans un ordre différent : des sources variées par défaut. */
const NODE_NATURES: Record<number, string[]> = {
  1: ['location', 'npc', 'event'],
  2: ['npc', 'location', 'event'],
  3: ['event', 'location', 'npc']
};

function nodeFields(n: number): GuidedField[] {
  return [
    { key: `node${n}.name`, kind: 'text', required: true, prefilled: true },
    { key: `node${n}.nature`, kind: 'choice', options: NODE_NATURES[n] },
    { key: `node${n}.description`, kind: 'textarea', coachTopic: 'scene.description' },
    { key: `node${n}.clues`, kind: 'textarea', coachTopic: 'scene.branches' },
    { key: `node${n}.secret`, kind: 'textarea', coachTopic: 'scene.gmNotes' }
  ];
}

/** Réseau de liens entre les six scènes (indices dans l'ordre de `scenes`). */
function investigationLinks(): GuidedDraftLink[] {
  const links: GuidedDraftLink[] = [];
  for (const n of NODES) links.push({ from: HOOK, to: n, kind: 'LEAD', labelKey: 'lead' });
  links.push({ from: 1, to: 2, kind: 'LEAD', labelKey: 'lead' });
  links.push({ from: 2, to: 3, kind: 'LEAD', labelKey: 'lead' });
  links.push({ from: 3, to: 1, kind: 'LEAD', labelKey: 'lead' });
  for (const n of NODES) links.push({ from: n, to: CONCLUSION, kind: 'CLUE', labelKey: 'clue' });
  for (const n of NODES) links.push({ from: PROACTIVE, to: n, kind: 'LEAD', labelKey: 'proactive' });
  return links;
}

export const INVESTIGATION_METHOD: GuidedMethod = {
  id: 'investigation',
  i18nKey: 'investigation',
  steps: [
    { id: 'placement', kind: 'placement' },
    {
      id: 'mystery', kind: 'form', fields: [
        { key: 'title', kind: 'text', required: true },
        { key: 'truth', kind: 'textarea', coachTopic: 'publication.gmBackground' },
        { key: 'stakes', kind: 'textarea', coachTopic: 'chapter.stakes' }
      ]
    },
    {
      id: 'hook', kind: 'form', fields: [
        { key: 'hook.name', kind: 'text', required: true, prefilled: true },
        { key: 'hook.nature', kind: 'choice', options: ['event', 'npc'] },
        { key: 'hook.description', kind: 'textarea', coachTopic: 'chapter.synopsis' },
        { key: 'hook.clues', kind: 'textarea', coachTopic: 'scene.branches' }
      ]
    },
    { id: 'node1', kind: 'form', fields: nodeFields(1) },
    { id: 'node2', kind: 'form', fields: nodeFields(2) },
    { id: 'node3', kind: 'form', fields: nodeFields(3) },
    {
      id: 'conclusion', kind: 'form', fields: [
        { key: 'conclusion.name', kind: 'text', required: true, prefilled: true },
        { key: 'conclusion.nature', kind: 'choice', options: ['revelation', 'encounter'] },
        { key: 'conclusion.description', kind: 'textarea', coachTopic: 'scene.description' },
        { key: 'conclusion.choices', kind: 'textarea', coachTopic: 'scene.choices' }
      ]
    },
    {
      id: 'proactive', kind: 'form', fields: [
        { key: 'proactive.name', kind: 'text', required: true, prefilled: true },
        { key: 'proactive.description', kind: 'textarea', coachTopic: 'scene.description' },
        { key: 'proactive.clues', kind: 'textarea', coachTopic: 'scene.branches' }
      ]
    },
    { id: 'review', kind: 'review' }
  ],

  toDraft(answers: GuidedAnswers) {
    const scene = (prefix: string, type: SceneType, choicesKey = 'clues', secretKey?: string): GuidedSceneSpec => ({
      name: answer(answers, `${prefix}.name`) ?? '',
      description: answer(answers, `${prefix}.description`),
      type,
      choicesConsequences: answer(answers, `${prefix}.${choicesKey}`),
      gmSecretNotes: secretKey ? answer(answers, `${prefix}.${secretKey}`) : null
    });
    const nature = (key: string, fallback: SceneType): SceneType => NATURE_TO_TYPE[answers[key]] ?? fallback;
    return {
      chapter: {
        name: answer(answers, 'title') ?? '',
        description: answer(answers, 'hook.description'),
        narrativeStakes: answer(answers, 'stakes'),
        gmNotes: answer(answers, 'truth')
      },
      scenes: [
        scene('hook', nature('hook.nature', 'EVENT')),
        ...NODES.map(n => scene(`node${n}`, nature(`node${n}.nature`, 'LOCATION'), 'clues', 'secret')),
        scene('conclusion', nature('conclusion.nature', 'REVELATION'), 'choices'),
        scene('proactive', 'EVENT')
      ],
      links: investigationLinks()
    };
  }
};
