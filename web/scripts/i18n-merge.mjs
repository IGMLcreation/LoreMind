// Fusionne les fragments de traduction par fonctionnalité dans les fichiers
// centraux fr.json / en.json.
//
// Chaque agent de traduction écrit un fragment dédié sous
// src/assets/i18n/fragments/<feature>.<lang>.json contenant uniquement SON
// namespace (clé de premier niveau distincte de common/language/settings).
// Ce script deep-merge tous ces fragments dans fr.json / en.json — c'est le
// SEUL endroit qui écrit les fichiers centraux, ce qui évite tout conflit
// d'écriture entre agents parallèles.
//
// Usage : node scripts/i18n-merge.mjs
import { readFileSync, writeFileSync, readdirSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const i18nDir = join(here, '..', 'src', 'assets', 'i18n');
const fragDir = join(i18nDir, 'fragments');

const isObject = (v) => v && typeof v === 'object' && !Array.isArray(v);

function deepMerge(target, source) {
  for (const key of Object.keys(source)) {
    if (isObject(source[key]) && isObject(target[key])) {
      deepMerge(target[key], source[key]);
    } else {
      target[key] = source[key];
    }
  }
  return target;
}

for (const lang of ['fr', 'en']) {
  const basePath = join(i18nDir, `${lang}.json`);
  const base = JSON.parse(readFileSync(basePath, 'utf8'));

  if (existsSync(fragDir)) {
    const fragments = readdirSync(fragDir)
      .filter((f) => f.endsWith(`.${lang}.json`))
      .sort();
    for (const frag of fragments) {
      const data = JSON.parse(readFileSync(join(fragDir, frag), 'utf8'));
      deepMerge(base, data);
    }
  }

  writeFileSync(basePath, JSON.stringify(base, null, 2) + '\n', 'utf8');
  console.log(`✓ ${lang}.json mis à jour`);
}
