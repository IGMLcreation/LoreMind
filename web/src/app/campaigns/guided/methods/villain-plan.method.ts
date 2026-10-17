import { SceneType } from '../../../services/campaign.model';
import { GuidedAnswers, GuidedField, GuidedMethod, GuidedStep, answer } from '../guided-method.model';

/**
 * « Plan du méchant » (textes écrits pour LoreMind). L'adversaire ne reste pas assis à
 * attendre les héros : il poursuit un plan en six étapes, trois discrètes pour se préparer
 * puis trois visibles pour agir. Chaque étape le rapproche de son but, frappe plus fort que
 * la précédente et laisse aux joueurs une occasion de réagir.
 *
 * Produit 1 chapitre (le plan complet en notes MJ) + 6 scènes d'événement que le MJ
 * déclenche au fil des séances (non chaînées : c'est l'adversaire qui avance, pas les
 * joueurs), et, au choix, une horloge de 6 segments dans chaque partie de la campagne.
 */

const STEPS = [1, 2, 3, 4, 5, 6];

const NATURE_TO_TYPE: Record<string, SceneType> = {
  event: 'EVENT',
  npc: 'NPC',
  location: 'LOCATION',
  encounter: 'ENCOUNTER'
};

/** Nature proposée en premier pour chaque étape : un rythme varié par défaut. */
const FIRST_NATURE: Record<number, string> = { 1: 'event', 2: 'npc', 3: 'location', 4: 'event', 5: 'npc', 6: 'encounter' };

function natures(step: number): string[] {
  const first = FIRST_NATURE[step];
  return [first, ...Object.keys(NATURE_TO_TYPE).filter(n => n !== first)];
}

function planStep(n: number): GuidedStep {
  const fields: GuidedField[] = [
    { key: `step${n}.name`, kind: 'text', required: true, prefilled: true },
    { key: `step${n}.nature`, kind: 'choice', options: natures(n) },
    { key: `step${n}.action`, kind: 'textarea', coachTopic: 'scene.description' },
    { key: `step${n}.sign`, kind: 'textarea', coachTopic: 'scene.choices' }
  ];
  return { id: `step${n}`, kind: 'form', fields };
}

export const VILLAIN_PLAN_METHOD: GuidedMethod = {
  id: 'villain-plan',
  i18nKey: 'villainPlan',
  steps: [
    { id: 'placement', kind: 'placement' },
    {
      id: 'villain', kind: 'form', fields: [
        { key: 'title', kind: 'text', required: true },
        { key: 'villain', kind: 'text', required: true },
        { key: 'goal', kind: 'textarea', coachTopic: 'arc.synopsis' },
        { key: 'stakes', kind: 'textarea', coachTopic: 'arc.stakes' },
        { key: 'objectives', kind: 'textarea', coachTopic: 'chapter.objectives' }
      ]
    },
    ...STEPS.map(planStep),
    {
      id: 'clock', kind: 'form', fields: [
        { key: 'clockWanted', kind: 'choice', options: ['yes', 'no'] },
        { key: 'clockName', kind: 'text', prefilled: true }
      ]
    },
    { id: 'review', kind: 'review' }
  ],

  toDraft(answers: GuidedAnswers) {
    const villain = answer(answers, 'villain');
    const goal = answer(answers, 'goal');
    const stakes = answer(answers, 'stakes');
    const plan = STEPS
      .map(n => [answer(answers, `step${n}.name`), answer(answers, `step${n}.action`)])
      .filter(([name]) => !!name)
      .map(([name, action], i) => `${i + 1}. ${name}${action ? ' — ' + action : ''}`);
    const description = [villain, goal].filter(Boolean).join('\n\n') || null;
    const wantsClock = answers['clockWanted'] !== 'no';
    return {
      chapter: {
        name: answer(answers, 'title') ?? '',
        description,
        playerObjectives: answer(answers, 'objectives'),
        narrativeStakes: stakes,
        gmNotes: plan.length ? plan.join('\n') : null
      },
      scenes: STEPS.map(n => ({
        name: answer(answers, `step${n}.name`) ?? '',
        description: answer(answers, `step${n}.action`),
        type: NATURE_TO_TYPE[answers[`step${n}.nature`]] ?? NATURE_TO_TYPE[FIRST_NATURE[n]],
        choicesConsequences: answer(answers, `step${n}.sign`)
      })),
      noChain: true,
      clock: wantsClock
        ? { name: answer(answers, 'clockName') ?? answer(answers, 'title') ?? '', description: stakes, segments: STEPS.length }
        : undefined
    };
  }
};
