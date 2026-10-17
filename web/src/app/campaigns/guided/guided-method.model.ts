import { GuidedChapterSpec, GuidedSceneSpec } from '../../services/guided-skeleton.model';

/**
 * Définition d'une méthode guidée (Mentor d'écriture, phases M3/M5). L'assistant
 * `guided-wizard` est générique : il affiche les étapes d'une méthode et lui délègue
 * la construction du squelette. Ajouter une méthode = un fichier de définition + ses
 * textes i18n (`guided.<i18nKey>.*`) + son entrée dans `GUIDED_METHODS`.
 *
 * Conventions i18n (préfixe `guided.<i18nKey>.`) :
 *  - `name`, `tagline`                          : présentation de la méthode ;
 *  - `steps.<stepId>.title|why|example`         : chaque étape (pourquoi + exemple, posture mentor) ;
 *  - `fields.<fieldKey>.label|placeholder`      : chaque champ (`fieldKey` peut contenir des points) ;
 *  - `options.<fieldKey>.<option>`              : libellés des choix ;
 *  - `defaults.<fieldKey>`                      : valeur proposée d'office (ex. nom d'une salle).
 */

/** Réponses du MJ, par clé de champ. */
export type GuidedAnswers = Record<string, string>;

export interface GuidedField {
  key: string;
  kind: 'text' | 'textarea' | 'choice';
  required?: boolean;
  /** Valeurs possibles d'un champ `choice` (la 1re est sélectionnée par défaut). */
  options?: string[];
  /** Pré-remplir avec `defaults.<key>` (traduit) à la première visite. */
  prefilled?: boolean;
  /** Sujet du coach d'écriture (💡) associé, ex. `scene.choices`. */
  coachTopic?: string;
}

export interface GuidedStep {
  id: string;
  /** `placement` : où ranger l'aventure ; `form` : questions ; `review` : récapitulatif. */
  kind: 'placement' | 'form' | 'review';
  fields?: GuidedField[];
}

/** Ce que la méthode produit à partir des réponses (le placement est ajouté par l'assistant). */
export interface GuidedDraft {
  chapter: GuidedChapterSpec;
  scenes: GuidedSceneSpec[];
}

export interface GuidedMethod {
  /** Identifiant d'URL (`/campaigns/:id/guided/:method`). */
  id: string;
  /** Préfixe i18n sous `guided.`. */
  i18nKey: string;
  steps: GuidedStep[];
  toDraft(answers: GuidedAnswers): GuidedDraft;
}

/** Valeur nettoyée d'une réponse (chaîne vide → null). */
export function answer(answers: GuidedAnswers, key: string): string | null {
  const value = answers[key]?.trim();
  return value ? value : null;
}
