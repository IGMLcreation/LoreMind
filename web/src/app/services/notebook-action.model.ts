/**
 * Protocole d'« actions » proposées par l'IA dans le chat d'un atelier : des blocs
 * ```loremind-action {json} ``` que le front transforme en cartes « Créer dans la
 * campagne ». Ce module définit les types + le parseur (extraction + texte nettoyé).
 */

export interface NotebookActionEntry {
  minRoll: number;
  maxRoll: number;
  label: string;
  detail?: string;
}

export interface NotebookAction {
  type: 'npc' | 'scene' | 'chapter' | 'arc' | 'table';
  name: string;
  description?: string;
  /** Legacy (anciens messages) : déroulé d'une scène → repli sur playerNarration. */
  content?: string;
  arcType?: 'LINEAR' | 'HUB';
  diceFormula?: string;
  entries?: NotebookActionEntry[];

  // --- PNJ : valeurs des champs TEXT de la fiche (clés = template du système). ---
  values?: Record<string, string>;

  // --- Scène : champs narratifs enrichis (miroir de SceneCreate). ---
  location?: string;
  timing?: string;
  atmosphere?: string;
  playerNarration?: string;
  gmSecretNotes?: string;
  choicesConsequences?: string;
  combatDifficulty?: string;
  enemies?: string;

  // --- Chapitre. ---
  playerObjectives?: string;
  narrativeStakes?: string;

  // --- Chapitre + Arc. ---
  gmNotes?: string;

  // --- Arc. ---
  themes?: string;
  stakes?: string;
  rewards?: string;
  resolution?: string;
}

const ACTION_RE = /```loremind-action\s*([\s\S]*?)```/g;
const VALID_TYPES = new Set(['npc', 'scene', 'chapter', 'arc', 'table']);

/**
 * Extrait les blocs d'action COMPLETS d'un message et renvoie le texte sans ces
 * blocs. Les blocs encore en cours de streaming (clôture absente) ne matchent pas
 * → ils restent affichés tels quels brièvement, puis deviennent une carte.
 */
export function parseNotebookActions(content: string): { text: string; actions: NotebookAction[] } {
  const actions: NotebookAction[] = [];
  if (!content) return { text: '', actions };
  let match: RegExpExecArray | null;
  ACTION_RE.lastIndex = 0;
  while ((match = ACTION_RE.exec(content)) !== null) {
    try {
      const obj = JSON.parse(match[1].trim());
      if (obj && typeof obj.type === 'string' && VALID_TYPES.has(obj.type) && typeof obj.name === 'string') {
        actions.push(obj as NotebookAction);
      }
    } catch {
      /* bloc JSON invalide : ignoré */
    }
  }
  const text = content.replace(ACTION_RE, '').trim();
  return { text, actions };
}
