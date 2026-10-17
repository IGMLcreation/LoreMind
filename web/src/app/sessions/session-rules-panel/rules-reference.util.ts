/**
 * Logique PURE de l'onglet « Règles » du mode jeu : découpage du markdown de règles
 * d'un GameSystem en sections H2 puis en blocs, recherche instantanée (sans IA,
 * hors ligne) insensible à la casse et aux accents, surlignage dans le HTML rendu.
 *
 * Le découpage H2 reste cohérent avec l'éditeur de système et le parser Java
 * (`^##\s+…$`) ; à la différence de ceux-ci, le texte avant le premier H2 n'est pas
 * perdu (on le présente sous un titre fourni par l'appelant) et un `## ` à
 * l'intérieur d'un bloc de code ne coupe pas la section.
 */

/** Bloc lisible d'une section (paragraphe, liste, tableau… séparé par une ligne vide). */
export interface RuleBlock {
  markdown: string;
  /** Texte brut normalisé (minuscules, sans accents ni syntaxe markdown) pour la recherche. */
  searchText: string;
  /** Item de liste : ce qui précède la liste (sous-titre, phrase d'introduction). */
  context?: string;
  /** Item de liste : identifiant de la liste d'origine (regroupement au rendu). */
  group?: number;
}

export interface RuleSection {
  /** Identifiant DOM stable pour l'affichage (ancre du sommaire). */
  id: string;
  title: string;
  /** Titre normalisé pour la recherche. */
  searchTitle: string;
  blocks: RuleBlock[];
}

export interface RuleMatch {
  section: RuleSection;
  /** Blocs à afficher : tous si le titre correspond, sinon seulement ceux qui correspondent. */
  blocks: RuleBlock[];
  titleMatch: boolean;
}

/** Signes combinants (accents) isolés par la décomposition NFD. */
const DIACRITICS = /\p{M}/gu;
const NBSP = String.fromCharCode(0xa0);
const H2 = /^##\s+(\S.*)$/;
const FENCE = /^\s*(```|~~~)/;
const HEADING_ONLY = /^#{3,6}\s+\S/;

/**
 * Minuscules + sans accents : « Écuyer » et « ecuyer » se trouvent mutuellement.
 * Apostrophes typographiques et ligatures (œ, æ) ramenées à leur forme simple :
 * les règles importées d'un PDF en sont pleines, la saisie au clavier jamais.
 */
export function normalizeForSearch(text: string): string {
  return text.normalize('NFD').replace(DIACRITICS, '').toLowerCase()
    .replace(/[‘’ʼ]/g, "'")
    .replace(/œ/g, 'oe')
    .replace(/æ/g, 'ae');
}

/**
 * Retire les cibles de liens/images markdown (`](url)`) — parcours linéaire plutôt
 * qu'une regex à quantificateurs imbriqués (backtracking sur un gros livre de règles).
 */
function stripLinkTargets(markdown: string): string {
  let out = '';
  let pos = 0;
  for (let open = markdown.indexOf('](', pos); open !== -1; open = markdown.indexOf('](', pos)) {
    const close = markdown.indexOf(')', open + 2);
    if (close === -1) break;
    out += markdown.slice(pos, open);
    pos = close + 1;
  }
  return out + markdown.slice(pos);
}

/** Retire la syntaxe markdown courante pour ne chercher que dans le texte lu. */
function plainText(markdown: string): string {
  return stripLinkTargets(markdown)
    .replace(/[*_`~#>|![\]<]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function toBlock(markdown: string): RuleBlock {
  return { markdown, searchText: normalizeForSearch(plainText(markdown)) };
}

/** Début d'item de liste de PREMIER niveau (pas d'indentation). */
const LIST_ITEM = /^(?:[-*+]|\d{1,3}[.)])\s+\S/;

/**
 * Une liste devient un bloc PAR ITEM (la recherche « magicien » ne montre que la ligne
 * du Magicien, pas les onze classes). Ce qui précède la liste (sous-titre, « Exemples : »)
 * devient le contexte de chaque item : cherchable, et réaffiché une fois au rendu.
 */
function splitList(block: string, group: number): RuleBlock[] {
  const lines = block.split('\n');
  const first = lines.findIndex(l => LIST_ITEM.test(l));
  if (first === -1) return [toBlock(block)];
  const items: string[] = [];
  for (const line of lines.slice(first)) {
    if (LIST_ITEM.test(line) || items.length === 0) items.push(line);
    else items[items.length - 1] += `\n${line}`;
  }
  if (items.length < 2) return [toBlock(block)];
  const context = lines.slice(0, first).join('\n').trim() || undefined;
  return items.map(item => ({
    markdown: item,
    searchText: normalizeForSearch(plainText(context ? `${context} ${item}` : item)),
    context,
    group
  }));
}

/**
 * Markdown à rendre pour une suite de blocs : les items consécutifs d'une même liste
 * restent une liste serrée, et le contexte d'une liste n'est affiché qu'une fois.
 */
export function blocksToMarkdown(blocks: RuleBlock[]): string {
  let out = '';
  let previousGroup: number | undefined;
  for (const b of blocks) {
    const sameList = b.group !== undefined && b.group === previousGroup;
    if (!sameList && b.context) out += (out ? '\n\n' : '') + b.context;
    if (out) out += sameList ? '\n' : '\n\n';
    out += b.markdown;
    previousGroup = b.group;
  }
  return out;
}

/** Découpe le contenu d'une section en blocs (lignes vides hors blocs de code). */
function splitBlocks(lines: string[]): RuleBlock[] {
  const raw: string[] = [];
  let current: string[] = [];
  let inFence = false;
  const flush = () => {
    const text = current.join('\n').trim();
    if (text) raw.push(text);
    current = [];
  };
  for (const line of lines) {
    if (FENCE.test(line)) inFence = !inFence;
    if (!inFence && line.trim() === '') { flush(); continue; }
    current.push(line);
  }
  flush();

  // Un sous-titre seul (### …) est rattaché au bloc qui le suit : un résultat de
  // recherche garde ainsi son contexte.
  const merged: string[] = [];
  let pendingHeading = '';
  for (const block of raw) {
    const text = pendingHeading ? `${pendingHeading}\n\n${block}` : block;
    pendingHeading = '';
    if (HEADING_ONLY.test(block) && !block.includes('\n')) pendingHeading = text;
    else merged.push(text);
  }
  if (pendingHeading) merged.push(pendingHeading);
  return merged.flatMap((block, i) => splitList(block, i));
}

/**
 * Découpe le markdown de règles en sections H2.
 * @param preambleTitle titre donné au texte placé avant le premier H2 (s'il y en a).
 */
export function parseRuleSections(markdown: string | null | undefined, preambleTitle: string): RuleSection[] {
  if (!markdown?.trim()) return [];
  const groups: { title: string; lines: string[] }[] = [];
  let current: { title: string; lines: string[] } = { title: preambleTitle, lines: [] };
  let inFence = false;

  for (const line of markdown.replace(/\r\n?/g, '\n').split('\n')) {
    if (FENCE.test(line)) inFence = !inFence;
    const match = inFence ? null : H2.exec(line);
    if (match) {
      groups.push(current);
      current = { title: match[1].trim(), lines: [] };
    } else {
      current.lines.push(line);
    }
  }
  groups.push(current);

  return groups
    .map((g, i) => ({ g, i }))
    // Le préambule n'existe que s'il a du contenu ; une vraie section vide reste (son titre informe).
    .filter(({ g, i }) => i > 0 || g.lines.some(l => l.trim()))
    .map(({ g, i }) => ({
      id: `rules-section-${i}`,
      title: g.title,
      searchTitle: normalizeForSearch(g.title),
      blocks: splitBlocks(g.lines)
    }));
}

/** Termes de recherche normalisés (mots d'au moins 2 caractères, sans doublon). */
export function searchTerms(query: string | null | undefined): string[] {
  const words = normalizeForSearch(query ?? '').split(/\s+/).filter(w => w.length >= 2);
  return [...new Set(words)];
}

const containsAll = (haystack: string, terms: string[]) => terms.every(t => haystack.includes(t));

/**
 * Filtre les sections : un bloc correspond si TOUS les termes figurent dans le titre
 * de sa section ou dans son texte (« combat initiative » trouve le paragraphe sur
 * l'initiative de la section Combat). Sections dont le titre correspond en tête.
 * Sans terme : toutes les sections, tous les blocs.
 */
export function filterRuleSections(sections: RuleSection[], terms: string[]): RuleMatch[] {
  if (terms.length === 0) {
    return sections.map(section => ({ section, blocks: section.blocks, titleMatch: false }));
  }
  const matches: RuleMatch[] = [];
  for (const section of sections) {
    if (containsAll(section.searchTitle, terms)) {
      matches.push({ section, blocks: section.blocks, titleMatch: true });
      continue;
    }
    const blocks = section.blocks.filter(b => containsAll(`${section.searchTitle} ${b.searchText}`, terms));
    if (blocks.length > 0) matches.push({ section, blocks, titleMatch: false });
  }
  return [...matches.filter(m => m.titleMatch), ...matches.filter(m => !m.titleMatch)];
}

// ─────────────── Surlignage ───────────────

const ENTITIES: Record<string, string> = { amp: '&', lt: '<', gt: '>', quot: '"', apos: "'", nbsp: NBSP };

function decodeEntities(text: string): string {
  return text.replace(/&(#x[0-9a-f]+|#\d+|[a-z]+);/gi, (whole, code: string) => {
    if (code[0] === '#') {
      const n = code[1].toLowerCase() === 'x' ? parseInt(code.slice(2), 16) : parseInt(code.slice(1), 10);
      return Number.isFinite(n) ? String.fromCodePoint(n) : whole;
    }
    return ENTITIES[code.toLowerCase()] ?? whole;
  });
}

function escapeHtml(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replaceAll(NBSP, '&nbsp;');
}

/** Surligne les termes dans un texte brut ; renvoie du HTML échappé. */
export function highlightText(text: string, terms: string[]): string {
  if (terms.length === 0 || !text) return escapeHtml(text);
  // Texte normalisé + correspondance index normalisé → index d'origine
  // (un caractère accentué peut se décomposer en plusieurs points de code).
  let norm = '';
  const origStart: number[] = [];
  const origEnd: number[] = [];
  let i = 0;
  for (const ch of text) {
    const n = normalizeForSearch(ch);
    origStart.push(...new Array<number>(n.length).fill(i));
    origEnd.push(...new Array<number>(n.length).fill(i + ch.length));
    norm += n;
    i += ch.length;
  }

  const ranges: [number, number][] = [];
  for (const term of terms) {
    let from = norm.indexOf(term);
    while (from !== -1) {
      ranges.push([origStart[from], origEnd[from + term.length - 1]]);
      from = norm.indexOf(term, from + term.length);
    }
  }
  if (ranges.length === 0) return escapeHtml(text);

  ranges.sort((a, b) => a[0] - b[0]);
  const merged: [number, number][] = [];
  for (const r of ranges) {
    const last = merged[merged.length - 1];
    if (last && r[0] <= last[1]) last[1] = Math.max(last[1], r[1]);
    else merged.push([r[0], r[1]]);
  }

  let out = '';
  let pos = 0;
  for (const [start, end] of merged) {
    out += escapeHtml(text.slice(pos, start)) + '<mark>' + escapeHtml(text.slice(start, end)) + '</mark>';
    pos = end;
  }
  return out + escapeHtml(text.slice(pos));
}

/**
 * Surligne les termes dans du HTML DÉJÀ assaini (sortie DOMPurify) : seuls les nœuds
 * texte sont touchés, jamais les balises ni leurs attributs. N'ajoute que des
 * `<mark>` autour de texte ré-échappé : le résultat reste sûr.
 */
export function highlightHtml(html: string, terms: string[]): string {
  if (terms.length === 0 || !html) return html;
  // Parcours linéaire texte / balise (pas de regex à backtracking sur un gros HTML).
  let out = '';
  let pos = 0;
  while (pos < html.length) {
    const tagStart = html.indexOf('<', pos);
    const textEnd = tagStart === -1 ? html.length : tagStart;
    if (textEnd > pos) out += highlightText(decodeEntities(html.slice(pos, textEnd)), terms);
    if (tagStart === -1) break;
    const tagEnd = html.indexOf('>', tagStart);
    if (tagEnd === -1) { out += html.slice(tagStart); break; }
    out += html.slice(tagStart, tagEnd + 1);
    pos = tagEnd + 1;
  }
  return out;
}
