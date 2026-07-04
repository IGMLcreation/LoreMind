// Vérificateur de cohérence i18n.
//
// Les traductions vivent en DOUBLE : les fichiers consolidés (src/assets/i18n/fr.json,
// en.json — ceux que charge ngx-translate) et les fragments de maintenance
// (src/assets/i18n/fragments/*.fr.json / *.en.json). Une section peut être alimentée
// par PLUSIEURS fragments (ex. playthroughDetail) : un fragment est donc un
// SOUS-ENSEMBLE du consolidé, pas une copie exacte.
//
// ERREURS (code retour 1) :
//   1. clé de fragment absente du consolidé, ou valeur différente (le consolidé est
//      ce que voit l'utilisateur : un fragment en avance = trad perdue en prod) ;
//   2. deux fragments qui définissent la même clé avec des valeurs contradictoires ;
//   3. parité structurelle fr/en rompue (fr.json vs en.json, et chaque paire de
//      fragments x.fr.json / x.en.json) ;
//   4. JSON invalide ou paire de fragments incomplète.
//
// AVERTISSEMENTS (non bloquants) : clés du consolidé couvertes par aucun fragment
// (dette de maintenance, pas un bug utilisateur).
//
// Usage : node scripts/check-i18n.mjs

import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const webDir = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const i18nDir = path.join(webDir, 'src', 'assets', 'i18n');
const fragmentsDir = path.join(i18nDir, 'fragments');

const errors = [];
const warnings = [];

function loadJson(file) {
  try {
    return JSON.parse(readFileSync(file, 'utf8'));
  } catch (e) {
    errors.push(`${path.relative(webDir, file)} : JSON invalide (${e.message})`);
    return null;
  }
}

const isObject = v => v !== null && typeof v === 'object' && !Array.isArray(v);

/** Feuilles d'un objet : Map "a.b.c" -> valeur (les tableaux sont des feuilles). */
function leaves(obj, prefix = '', out = new Map()) {
  for (const [key, value] of Object.entries(obj)) {
    const p = prefix ? `${prefix}.${key}` : key;
    if (isObject(value)) leaves(value, p, out);
    else out.set(p, value);
  }
  return out;
}

const sameValue = (a, b) => JSON.stringify(a) === JSON.stringify(b);

/** Parité structurelle : mêmes chemins de feuilles des deux côtés. */
function structuralDiff(labelA, a, labelB, b) {
  const pathsA = leaves(a);
  const pathsB = leaves(b);
  for (const p of pathsA.keys()) if (!pathsB.has(p)) errors.push(`${labelB} : clé manquante « ${p} » (présente dans ${labelA})`);
  for (const p of pathsB.keys()) if (!pathsA.has(p)) errors.push(`${labelA} : clé manquante « ${p} » (présente dans ${labelB})`);
}

// --- Chargement -------------------------------------------------------------
const consolidated = {
  fr: loadJson(path.join(i18nDir, 'fr.json')),
  en: loadJson(path.join(i18nDir, 'en.json'))
};
const consolidatedLeaves = {
  fr: consolidated.fr ? leaves(consolidated.fr) : new Map(),
  en: consolidated.en ? leaves(consolidated.en) : new Map()
};
const fragmentFiles = readdirSync(fragmentsDir).filter(f => f.endsWith('.json')).sort();

// --- 1 + 2 : chaque clé de fragment doit exister À L'IDENTIQUE au consolidé ---
const fragmentLeafOwner = { fr: new Map(), en: new Map() };
for (const file of fragmentFiles) {
  const match = file.match(/^(.+)\.(fr|en)\.json$/);
  if (!match) { errors.push(`fragments/${file} : nom inattendu (attendu <nom>.fr.json / <nom>.en.json)`); continue; }
  const lang = match[2];
  const fragment = loadJson(path.join(fragmentsDir, file));
  if (!fragment || !consolidated[lang]) continue;
  for (const [leafPath, value] of leaves(fragment)) {
    const owner = fragmentLeafOwner[lang].get(leafPath);
    if (owner && !sameValue(owner.value, value)) {
      errors.push(`Clé « ${leafPath} » (${lang}) contradictoire entre fragments/${owner.file} et fragments/${file}`);
    }
    fragmentLeafOwner[lang].set(leafPath, { file, value });
    if (!consolidatedLeaves[lang].has(leafPath)) {
      errors.push(`${lang}.json : clé « ${leafPath} » manquante (définie par fragments/${file})`);
    } else if (!sameValue(consolidatedLeaves[lang].get(leafPath), value)) {
      errors.push(`${lang}.json ≠ fragments/${file} : valeur différente pour « ${leafPath} »`);
    }
  }
}

// --- 3 : parité structurelle fr/en -------------------------------------------
if (consolidated.fr && consolidated.en) structuralDiff('fr.json', consolidated.fr, 'en.json', consolidated.en);
const fragmentNames = new Set(fragmentFiles.map(f => f.replace(/\.(fr|en)\.json$/, '')));
for (const name of fragmentNames) {
  const frFile = `${name}.fr.json`;
  const enFile = `${name}.en.json`;
  if (!fragmentFiles.includes(frFile) || !fragmentFiles.includes(enFile)) {
    errors.push(`fragments/${name} : paire fr/en incomplète`);
    continue;
  }
  const fr = loadJson(path.join(fragmentsDir, frFile));
  const en = loadJson(path.join(fragmentsDir, enFile));
  if (fr && en) structuralDiff(`fragments/${frFile}`, fr, `fragments/${enFile}`, en);
}

// --- Avertissements : clés consolidées sans fragment (dette, non bloquant) ----
for (const lang of ['fr']) { // fr suffit : la parité fr/en est déjà vérifiée
  const uncovered = [...consolidatedLeaves[lang].keys()].filter(p => !fragmentLeafOwner[lang].has(p));
  if (uncovered.length > 0) {
    warnings.push(`${uncovered.length} clé(s) de ${lang}.json sans fragment (dette de maintenance), ex. : ${uncovered.slice(0, 5).join(', ')}`);
  }
}

// --- Rapport ------------------------------------------------------------------
for (const w of warnings) console.warn(`i18n (avertissement) : ${w}`);
if (errors.length > 0) {
  console.error(`\ni18n : ${errors.length} incohérence(s) bloquante(s) :\n`);
  for (const e of errors) console.error(`  - ${e}`);
  process.exit(1);
}
console.log(`i18n OK — ${Object.keys(consolidated.fr ?? {}).length} sections, ${consolidatedLeaves.fr.size} clés, ${fragmentFiles.length} fragments.`);
