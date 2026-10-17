import { GuidedMethod } from './guided-method.model';
import { FIVE_ROOM_METHOD } from './methods/five-room.method';
import { INVESTIGATION_METHOD } from './methods/investigation.method';
import { VILLAIN_PLAN_METHOD } from './methods/villain-plan.method';

/** Méthodes guidées disponibles (à venir : préparation minimale, casse, bac à sable…). */
export const GUIDED_METHODS: GuidedMethod[] = [FIVE_ROOM_METHOD, INVESTIGATION_METHOD, VILLAIN_PLAN_METHOD];

export function findGuidedMethod(id: string | null): GuidedMethod | undefined {
  return GUIDED_METHODS.find(m => m.id === id);
}
