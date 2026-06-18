import { describe, it, expect } from 'vitest';
import { DiceUtils } from './dice.utils';

describe('DiceUtils.parse', () => {
  it('parse une formule complète', () => {
    expect(DiceUtils.parse('2d6')).toEqual({ count: 2, faces: 6 });
  });

  it('compte par défaut = 1 ("d20")', () => {
    expect(DiceUtils.parse('d20')).toEqual({ count: 1, faces: 20 });
  });

  it('tolère espaces et casse', () => {
    expect(DiceUtils.parse('  1 D 100 ')).toEqual({ count: 1, faces: 100 });
  });

  it('rejette les formules invalides ou hors bornes', () => {
    for (const f of ['', null, undefined, 'abc', '2x6', '0d6', '101d6', '1d1', '1d20000']) {
      expect(DiceUtils.parse(f as string)).toBeNull();
    }
  });
});

describe('DiceUtils.roll', () => {
  it('renvoie des jets dans la plage et un total = somme', () => {
    const r = DiceUtils.roll('3d6');
    expect(r).not.toBeNull();
    expect(r!.rolls).toHaveLength(3);
    for (const v of r!.rolls) {
      expect(v).toBeGreaterThanOrEqual(1);
      expect(v).toBeLessThanOrEqual(6);
    }
    expect(r!.total).toBe(r!.rolls.reduce((a, b) => a + b, 0));
    expect(r!.total).toBeGreaterThanOrEqual(3);
    expect(r!.total).toBeLessThanOrEqual(18);
  });

  it('null si formule invalide', () => {
    expect(DiceUtils.roll('nope')).toBeNull();
  });
});

describe('DiceUtils.totalRange', () => {
  it('2d6 -> {min:2, max:12}', () => {
    expect(DiceUtils.totalRange('2d6')).toEqual({ min: 2, max: 12 });
  });

  it('null si invalide', () => {
    expect(DiceUtils.totalRange('x')).toBeNull();
  });
});

describe('DiceUtils.randomInt', () => {
  it('reste dans [min, max] sur de nombreux tirages', () => {
    for (let i = 0; i < 200; i++) {
      const v = DiceUtils.randomInt(1, 6);
      expect(v).toBeGreaterThanOrEqual(1);
      expect(v).toBeLessThanOrEqual(6);
    }
  });
});
