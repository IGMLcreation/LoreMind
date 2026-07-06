/**
 * Utilitaires de dés partagés (jets aléatoires cryptographiques).
 *
 * Centralise le parsing de formules ("1d20", "2d6", "d100") et le jet, utilisé
 * par les tables aléatoires (et réutilisable ailleurs). Pas de Math.random pour
 * le tirage : on privilégie crypto.getRandomValues quand disponible.
 */

export interface ParsedDice {
  /** Nombre de dés (ex. 2 pour "2d6"). */
  count: number;
  /** Nombre de faces (ex. 6 pour "2d6"). */
  faces: number;
}

export interface DiceRoll {
  /** Formule normalisée utilisée pour le jet. */
  formula: string;
  /** Jets individuels de chaque dé. */
  rolls: number[];
  /** Somme des jets (la valeur qui désigne l'entrée d'une table). */
  total: number;
}

export class DiceUtils {

  /** Parse une formule simple `[N]dM`. Renvoie null si invalide. */
  static parse(formula: string | null | undefined): ParsedDice | null {
    // trim() préalable au lieu de \s* aux extrémités : `\s*(\d*)\s*` était
    // ambigu (quadratique) quand \d* est vide. Même langage accepté.
    const m = /^(\d*)\s*[dD]\s*(\d+)$/.exec((formula ?? '').trim());
    if (!m) return null;
    const count = m[1] ? parseInt(m[1], 10) : 1;
    const faces = parseInt(m[2], 10);
    if (count < 1 || count > 100 || faces < 2 || faces > 10000) return null;
    return { count, faces };
  }

  /** Entier aléatoire dans [min, max] inclus (crypto si dispo). */
  static randomInt(min: number, max: number): number {
    const span = max - min + 1;
    if (typeof crypto !== 'undefined' && crypto.getRandomValues) {
      const buf = new Uint32Array(1);
      crypto.getRandomValues(buf);
      return min + (buf[0] % span);
    }
    return min + Math.floor(Math.random() * span);
  }

  /** Lance la formule. Renvoie null si la formule est invalide. */
  static roll(formula: string): DiceRoll | null {
    const parsed = this.parse(formula);
    if (!parsed) return null;
    const rolls: number[] = [];
    for (let i = 0; i < parsed.count; i++) {
      rolls.push(this.randomInt(1, parsed.faces));
    }
    const total = rolls.reduce((a, b) => a + b, 0);
    return { formula, rolls, total };
  }

  /** Plage de totaux possibles d'une formule (ex. "2d6" → {min:2, max:12}). */
  static totalRange(formula: string): { min: number; max: number } | null {
    const parsed = this.parse(formula);
    if (!parsed) return null;
    return { min: parsed.count, max: parsed.count * parsed.faces };
  }
}
