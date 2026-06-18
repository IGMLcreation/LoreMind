import { defineConfig } from 'vitest/config';

/**
 * Runner des tests UNITAIRES de logique PURE (TypeScript sans TestBed Angular) :
 * helpers/utils et services dont on teste les fonctions pures. Exécution en Node
 * (rapide, pas de navigateur). Les specs sont en `*.spec.ts` à côté du code.
 *
 * Lancement : `npm run test:unit` ; couverture : `npm run test:unit:coverage`
 * (rapport HTML dans coverage/ — l'équivalent JaCoCo).
 *
 * Hors du périmètre `ng build` (les .spec.ts sont exclus dans tsconfig.json).
 */
export default defineConfig({
  test: {
    environment: 'node',
    include: ['src/**/*.spec.ts'],
    globals: true,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
      // Périmètre de couverture = les fichiers effectivement couverts par des
      // specs. Ajouter ici chaque module au fur et à mesure qu'on le teste.
      include: [
        'src/app/shared/sse.util.ts',
        'src/app/shared/dice.utils.ts',
        'src/app/campaigns/campaign-tree.helper.ts',
      ],
      // Plancher ANTI-RÉGRESSION (sous les valeurs actuelles : ~75% lignes).
      // campaign-tree.helper tire la moyenne vers le bas (loadCampaignTreeData /
      // buildCampaignSidebarConfig nécessitent des services Angular → non
      // unit-testables ici). À remonter à mesure que la couverture progresse.
      thresholds: {
        statements: 70,
        lines: 70,
        branches: 70,
        functions: 45,
      },
    },
  },
});
