import { GuidedAnswers, GuidedField, GuidedMethod, GuidedStep } from './guided-method.model';

/**
 * IA des méthodes guidées (logique pure, testée sans Angular) : questions envoyées au modèle,
 * champs que l'IA a le droit de remplir, et fusion des réponses proposées.
 *
 * Règle d'or : l'IA ne remplace JAMAIS ce que le MJ a écrit. Elle ne remplit qu'un champ
 * vide, ou resté à sa valeur d'office (nom pré-rempli « Première piste », premier choix
 * d'une question à choix) tant que le MJ n'y a pas touché.
 */

/** Clé de réponse réservée : l'idée de départ du MJ (gardée dans le brouillon). */
export const IDEA_KEY = 'idea';
/** Clé de réponse réservée : champs modifiés à la main par le MJ (séparés par des virgules). */
export const TOUCHED_KEY = 'touched';

/** Traduction d'une clé i18n (injectée : TranslateService.instant côté composant). */
export type Translate = (key: string) => string;

export interface GuidedQuestion {
  key: string;
  label: string;
  hint: string;
  step: string;
  options: { value: string; label: string }[];
}

export interface GuidedSuggestRequest {
  methodName: string;
  principle: string;
  idea: string;
  questions: GuidedQuestion[];
  existing: Record<string, string>;
}

/** Préfixe i18n de la méthode (`guided.<i18nKey>.`). */
function k(method: GuidedMethod, path: string): string {
  return `guided.${method.i18nKey}.${path}`;
}

/** Valeur mise d'office par l'assistant (sans intervention du MJ), ou null. */
export function defaultValue(method: GuidedMethod, field: GuidedField, t: Translate): string | null {
  if (field.kind === 'choice') return field.options?.[0] ?? null;
  if (field.prefilled) return t(k(method, `defaults.${field.key}`));
  return null;
}

export function touchedKeys(answers: GuidedAnswers): Set<string> {
  return new Set((answers[TOUCHED_KEY] ?? '').split(',').filter(Boolean));
}

/** Marque un champ comme modifié à la main (l'IA n'y touchera plus). */
export function markTouched(answers: GuidedAnswers, key: string): GuidedAnswers {
  const touched = touchedKeys(answers);
  if (touched.has(key)) return answers;
  touched.add(key);
  return { ...answers, [TOUCHED_KEY]: [...touched].join(',') };
}

/** Le champ est-il encore libre pour l'IA ? Vide, ou à sa valeur d'office et jamais touché. */
export function isFillable(method: GuidedMethod, field: GuidedField, answers: GuidedAnswers, t: Translate): boolean {
  const value = (answers[field.key] ?? '').trim();
  if (!value) return true;
  return !touchedKeys(answers).has(field.key) && value === defaultValue(method, field, t);
}

function formSteps(method: GuidedMethod, stepIds?: string[]): GuidedStep[] {
  return method.steps.filter(s => s.kind === 'form' && (!stepIds || stepIds.includes(s.id)));
}

/**
 * La méthode telle que le MJ la suit à la main : son principe, puis le rôle de chaque étape
 * (le « pourquoi » affiché dans l'assistant). Sans ces consignes, l'IA ne voyait que les
 * libellés des champs et pouvait, par exemple, régler l'épreuve de la salle 2 par un combat.
 * Les exemples ne sont pas transmis : l'IA les recopierait.
 */
export function methodGuide(method: GuidedMethod, t: Translate): string {
  const steps = formSteps(method).map(s => {
    const title = t(k(method, 'steps.' + s.id + '.title'));
    const why = t(k(method, 'steps.' + s.id + '.why'));
    return `- ${title} : ${why}`;
  });
  return [t(k(method, 'tagline')), '', ...steps].join('\n');
}

/** L'IA part toujours de l'idée du MJ : pas de proposition « de but en blanc ». */
export function hasIdea(answers: GuidedAnswers): boolean {
  return (answers[IDEA_KEY] ?? '').trim().length > 0;
}

/**
 * Requête de propositions pour les étapes demandées (toutes si absent) : seuls les champs
 * encore libres sont demandés ; tout ce que le MJ a déjà écrit est transmis comme contexte.
 */
export function buildSuggestRequest(method: GuidedMethod, answers: GuidedAnswers, t: Translate,
                                    stepIds?: string[]): GuidedSuggestRequest {
  const questions: GuidedQuestion[] = [];
  const existing: Record<string, string> = {};
  for (const step of formSteps(method)) {
    for (const field of step.fields ?? []) {
      if (!isFillable(method, field, answers, t)) {
        existing[field.key] = answers[field.key];
        continue;
      }
      if (stepIds && !stepIds.includes(step.id)) continue;
      questions.push({
        key: field.key,
        label: t(k(method, `fields.${field.key}.label`)),
        hint: field.kind === 'choice' ? '' : t(k(method, `fields.${field.key}.placeholder`)),
        step: t(k(method, `steps.${step.id}.title`)),
        options: (field.options ?? []).map(o => ({ value: o, label: t(k(method, `options.${field.key}.${o}`)) }))
      });
    }
  }
  return {
    methodName: t(k(method, 'name')),
    principle: methodGuide(method, t),
    idea: (answers[IDEA_KEY] ?? '').trim(),
    questions,
    existing
  };
}

/** Applique les propositions sur les seuls champs encore libres (jamais sur un champ du MJ). */
export function applySuggestions(method: GuidedMethod, answers: GuidedAnswers, suggestions: Record<string, string>,
                                 t: Translate): { answers: GuidedAnswers; filled: number } {
  const next = { ...answers };
  let filled = 0;
  for (const step of formSteps(method)) {
    for (const field of step.fields ?? []) {
      const proposal = suggestions[field.key]?.trim();
      if (!proposal || !isFillable(method, field, answers, t)) continue;
      if (field.kind === 'choice' && !field.options?.includes(proposal)) continue;
      next[field.key] = proposal;
      filled++;
    }
  }
  return { answers: next, filled };
}
