/** Déclencheur d'avancement auto d'une horloge (co-MJ). Miroir de l'enum Java ClockTrigger. */
export type ClockTrigger = 'NONE' | 'FLAG_SET' | 'QUEST_COMPLETED' | 'SESSION_ENDED';

/**
 * Horloge de progression (Clock) — état dynamique d'une Partie. Miroir du domaine Java.
 */
export interface Clock {
  id: string;
  playthroughId: string;
  name: string;
  description?: string;
  segments: number;
  filled: number;
  order: number;
  /** Déclencheur auto (co-MJ). Absent => NONE. */
  triggerType?: ClockTrigger;
  /** Cible : nom du fait (FLAG_SET) ou id de quête (QUEST_COMPLETED). */
  triggerRef?: string;
  /** Front (menace) auquel l'horloge appartient, ou undefined (libre). */
  frontId?: string;
  /** Read-only : filled >= segments. */
  complete: boolean;
}

export interface ClockCreate {
  name: string;
  description?: string;
  segments: number;
  triggerType?: ClockTrigger;
  triggerRef?: string;
  frontId?: string;
}
