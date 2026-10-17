import { SceneType } from '../../../services/campaign.model';
import { GuidedSceneSpec } from '../../../services/guided-skeleton.model';
import { GuidedAnswers, GuidedField, GuidedMethod, answer } from '../guided-method.model';

/**
 * « Donjon en 5 salles » (méthode popularisée par Johnn Four ; textes écrits pour
 * LoreMind). Une aventure courte en cinq temps : un gardien à l'entrée, une épreuve
 * qui ne se règle pas à l'épée, un revers, le climax, puis la récompense ou la
 * révélation. Le « donjon » peut être n'importe quel lieu : manoir, navire, forêt…
 *
 * Produit 1 chapitre (accroche, objectifs, enjeux) + 5 scènes typées dans le même lieu.
 */

/** Champs communs à chaque salle : nom (pré-rempli), ce qui s'y passe, choix, secret du MJ. */
function roomFields(room: number, extra: GuidedField[] = []): GuidedField[] {
  return [
    { key: `room${room}.name`, kind: 'text', required: true, prefilled: true },
    ...extra.filter(f => f.kind === 'choice'),
    { key: `room${room}.description`, kind: 'textarea', coachTopic: 'scene.description' },
    ...extra.filter(f => f.kind !== 'choice'),
    { key: `room${room}.choices`, kind: 'textarea', coachTopic: 'scene.choices' },
    { key: `room${room}.secret`, kind: 'textarea', coachTopic: 'scene.gmNotes' }
  ];
}

/** Nature de la salle (choix du MJ) → type de scène, pour que la relecture juge le rythme. */
const NATURE_TO_TYPE: Record<string, SceneType> = {
  combat: 'ENCOUNTER',
  social: 'NPC',
  obstacle: 'LOCATION',
  puzzle: 'LOCATION',
  exploration: 'LOCATION'
};

export const FIVE_ROOM_METHOD: GuidedMethod = {
  id: 'five-room',
  i18nKey: 'fiveRoom',
  steps: [
    { id: 'placement', kind: 'placement' },
    {
      id: 'frame', kind: 'form', fields: [
        { key: 'title', kind: 'text', required: true },
        { key: 'location', kind: 'text', coachTopic: 'scene.setting' },
        { key: 'hook', kind: 'textarea', coachTopic: 'chapter.synopsis' }
      ]
    },
    {
      id: 'stakes', kind: 'form', fields: [
        { key: 'objectives', kind: 'textarea', coachTopic: 'chapter.objectives' },
        { key: 'stakes', kind: 'textarea', coachTopic: 'chapter.stakes' }
      ]
    },
    {
      id: 'room1', kind: 'form', fields: roomFields(1, [
        { key: 'room1.nature', kind: 'choice', options: ['combat', 'social', 'obstacle'] },
        { key: 'room1.enemies', kind: 'text' }
      ])
    },
    {
      id: 'room2', kind: 'form', fields: roomFields(2, [
        { key: 'room2.nature', kind: 'choice', options: ['puzzle', 'social', 'exploration'] }
      ])
    },
    { id: 'room3', kind: 'form', fields: roomFields(3) },
    {
      id: 'room4', kind: 'form', fields: roomFields(4, [
        { key: 'room4.enemies', kind: 'text', coachTopic: 'scene.combat' }
      ])
    },
    { id: 'room5', kind: 'form', fields: roomFields(5) },
    { id: 'review', kind: 'review' }
  ],

  toDraft(answers: GuidedAnswers) {
    const location = answer(answers, 'location');
    const room = (n: number, type: SceneType): GuidedSceneSpec => ({
      name: answer(answers, `room${n}.name`) ?? '',
      description: answer(answers, `room${n}.description`),
      type,
      location,
      choicesConsequences: answer(answers, `room${n}.choices`),
      gmSecretNotes: answer(answers, `room${n}.secret`),
      enemies: answer(answers, `room${n}.enemies`)
    });
    return {
      chapter: {
        name: answer(answers, 'title') ?? '',
        description: answer(answers, 'hook'),
        playerObjectives: answer(answers, 'objectives'),
        narrativeStakes: answer(answers, 'stakes')
      },
      scenes: [
        room(1, NATURE_TO_TYPE[answers['room1.nature']] ?? 'ENCOUNTER'),
        room(2, NATURE_TO_TYPE[answers['room2.nature']] ?? 'LOCATION'),
        room(3, 'EVENT'),
        room(4, 'ENCOUNTER'),
        room(5, 'REVELATION')
      ]
    };
  }
};
