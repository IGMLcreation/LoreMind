/** Type d'une entrée de journal de session — miroir de l'enum Java EntryType. */
export type EntryType = 'NOTE' | 'EVENT' | 'DICE_ROLL' | 'PLAYER_ACTION';

/** Entrée du journal d'une Session (note, évènement, jet, action joueur). */
export interface SessionEntry {
  id: string;
  sessionId: string;
  type: EntryType;
  content: string;
  occurredAt: string;
  createdAt: string;
  updatedAt: string;
}

/** Payload de création/édition d'une entrée. */
export interface SessionEntryInput {
  type: EntryType;
  content: string;
  /** Optionnel : si absent, le backend utilisera "maintenant". */
  occurredAt?: string;
}

/** Métadonnées d'affichage par type — utilisées par la timeline. */
export interface EntryTypeMeta {
  label: string;
  icon: 'StickyNote' | 'Sparkles' | 'Dices' | 'UserCheck';
  color: string;
}

export const ENTRY_TYPE_META: Record<EntryType, EntryTypeMeta> = {
  NOTE:          { label: 'Note',          icon: 'StickyNote', color: '#9ca3af' },
  EVENT:         { label: 'Évènement',     icon: 'Sparkles',   color: '#f59e0b' },
  DICE_ROLL:     { label: 'Jet de dés',    icon: 'Dices',      color: '#6c63ff' },
  PLAYER_ACTION: { label: 'Action joueur', icon: 'UserCheck',  color: '#10b981' },
};
