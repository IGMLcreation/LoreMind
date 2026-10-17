import { GuidedMethod } from './guided-method.model';
import { FIVE_ROOM_METHOD } from './methods/five-room.method';

/** Méthodes guidées disponibles (M5 : préparation minimale, enquête, casse, bac à sable…). */
export const GUIDED_METHODS: GuidedMethod[] = [FIVE_ROOM_METHOD];

export function findGuidedMethod(id: string | null): GuidedMethod | undefined {
  return GUIDED_METHODS.find(m => m.id === id);
}
