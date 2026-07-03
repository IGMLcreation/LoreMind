/**
 * Front (menace regroupant des horloges) d'une Partie. Miroir du domaine Java.
 */
export interface Front {
  id: string;
  playthroughId: string;
  name: string;
  description?: string;
  order: number;
}

export interface FrontCreate {
  name: string;
  description?: string;
}
