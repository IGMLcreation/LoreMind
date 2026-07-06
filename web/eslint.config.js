// @ts-check
const eslint = require("@eslint/js");
const { defineConfig } = require("eslint/config");
const tseslint = require("typescript-eslint");
const angular = require("angular-eslint");
// Règles qualité "à la Sonar" (bugs, code smells, complexité) portées dans
// ESLint — remplace l'analyse SonarQube côté TypeScript.
const sonarjs = require("eslint-plugin-sonarjs");

module.exports = defineConfig([
  {
    files: ["**/*.ts"],
    extends: [
      eslint.configs.recommended,
      tseslint.configs.recommended,
      tseslint.configs.stylistic,
      angular.configs.tsRecommended,
      sonarjs.configs.recommended,
    ],
    processor: angular.processInlineTemplates,
    rules: {
      "@angular-eslint/directive-selector": [
        "error",
        {
          type: "attribute",
          prefix: "app",
          style: "camelCase",
        },
      ],
      "@angular-eslint/component-selector": [
        "error",
        {
          type: "element",
          prefix: "app",
          style: "kebab-case",
        },
      ],
      // Modernisation constructeur → inject() : 567 occurrences dans l'existant.
      // Pas un bug — chantier one-shot à mener via la migration officielle
      // `ng generate @angular/core:inject`, puis repasser la règle en "error".
      "@angular-eslint/prefer-inject": "off",
      // Math.random() est légitime ici : tables aléatoires / jets de dés = le
      // domaine métier. Aucun usage cryptographique côté front.
      "sonarjs/pseudo-random": "off",
      // 9 fonctions historiques dépassent le seuil de 15 (jusqu'à 39 sur les
      // graphes campagne/chapitre). Les refactorer "pour le lint" sans filet de
      // tests serait plus risqué qu'utile → warn (visible, non bloquant).
      // Backlog : campaign-graph (×3), chapter-graph (×2), campaign-import,
      // settings, persona-view, sse.util. Repasser en "error" une fois résorbé.
      "sonarjs/cognitive-complexity": "warn",
      // Convention TS standard : un préfixe `_` marque un paramètre/variable
      // volontairement inutilisé (ex. paramètres conservés pour ne pas casser
      // les appelants — cf. campaign-tree.helper.ts).
      "@typescript-eslint/no-unused-vars": [
        "error",
        {
          argsIgnorePattern: "^_",
          varsIgnorePattern: "^_",
          caughtErrorsIgnorePattern: "^_",
        },
      ],
    },
  },
  {
    files: ["**/*.html"],
    extends: [
      angular.configs.templateRecommended,
      angular.configs.templateAccessibility,
    ],
    rules: {
      // Accessibilité : ~127 findings sur l'existant. En "warn" (visibles,
      // non bloquants) le temps de résorber le backlog — repasser en "error"
      // règle par règle au fil des corrections.
      "@angular-eslint/template/label-has-associated-control": "warn",
      "@angular-eslint/template/click-events-have-key-events": "warn",
      "@angular-eslint/template/interactive-supports-focus": "warn",
      "@angular-eslint/template/no-autofocus": "warn",
    },
  }
]);
