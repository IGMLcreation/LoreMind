/**
 * Modèle Session côté Frontend.
 * Miroir du SessionDTO Java exposé par /api/sessions.
 */
export interface Session {
  id: string;
  name: string;
  campaignId: string;
  startedAt: string;
  /** Null/undefined = session en cours. */
  endedAt: string | null;
  createdAt: string;
  updatedAt: string;
  active: boolean;
}
