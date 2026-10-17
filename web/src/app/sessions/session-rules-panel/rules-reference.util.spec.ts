import { describe, it, expect } from 'vitest';
import {
  normalizeForSearch, parseRuleSections, searchTerms, filterRuleSections, blocksToMarkdown,
  highlightText, highlightHtml
} from './rules-reference.util';

const RULES = [
  'Ce système se joue avec des d20.',
  '',
  '## Combat',
  "L'initiative se lance au début du combat.",
  '',
  '### Attaque d\'opportunité',
  '',
  'Quand un ennemi quitte votre allonge, vous pouvez attaquer.',
  '',
  '## Magie',
  'Les sorts coûtent des points de mana.',
  '',
  '```',
  '## pas un titre (bloc de code)',
  '```',
  '',
  '## Équipement',
  '| Objet | Prix |',
  '| --- | --- |',
  '| Épée | 10 po |',
].join('\n');

describe('normalizeForSearch', () => {
  it('ignore casse et accents', () => {
    expect(normalizeForSearch('Équipement Écuyer')).toBe('equipement ecuyer');
  });

  it('apostrophe typographique et ligatures', () => {
    expect(normalizeForSearch('L’Œil du cœur')).toBe("l'oeil du coeur");
  });
});

describe('parseRuleSections', () => {
  const sections = parseRuleSections(RULES, 'Généralités');

  it('découpe sur les H2, garde le préambule sous le titre fourni', () => {
    expect(sections.map(s => s.title)).toEqual(['Généralités', 'Combat', 'Magie', 'Équipement']);
  });

  it('ne coupe pas sur un « ## » dans un bloc de code', () => {
    const magie = sections.find(s => s.title === 'Magie')!;
    expect(magie.blocks).toHaveLength(2);
    expect(magie.blocks[1].markdown).toContain('## pas un titre');
  });

  it('rattache un sous-titre isolé au bloc suivant', () => {
    const combat = sections.find(s => s.title === 'Combat')!;
    expect(combat.blocks).toHaveLength(2);
    expect(combat.blocks[1].markdown.startsWith("### Attaque d'opportunité")).toBe(true);
    expect(combat.blocks[1].markdown).toContain('allonge');
  });

  it('garde un tableau en un seul bloc', () => {
    const equip = sections.find(s => s.title === 'Équipement')!;
    expect(equip.blocks).toHaveLength(1);
  });

  it('pas de préambule vide ; markdown vide → aucune section', () => {
    expect(parseRuleSections('## A\ntexte', 'Intro').map(s => s.title)).toEqual(['A']);
    expect(parseRuleSections('', 'Intro')).toEqual([]);
    expect(parseRuleSections(null, 'Intro')).toEqual([]);
  });

  it('accepte les fins de ligne Windows', () => {
    expect(parseRuleSections('## A\r\nun\r\n\r\ndeux', 'I')[0].blocks).toHaveLength(2);
  });
});

describe('listes', () => {
  const md = [
    '## Classes',
    'Classes jouables :',
    '- **Barbare** : d12 PV.',
    '- **Magicien** : d6 PV,',
    '  livre de sorts.',
    '- **Roublard** : d8 PV.',
  ].join('\n');
  const [classes] = parseRuleSections(md, 'I');

  it('un bloc par item de premier niveau, continuation indentée incluse', () => {
    expect(classes.blocks.map(b => b.markdown)).toEqual([
      '- **Barbare** : d12 PV.',
      '- **Magicien** : d6 PV,\n  livre de sorts.',
      '- **Roublard** : d8 PV.',
    ]);
    expect(classes.blocks.every(b => b.context === 'Classes jouables :')).toBe(true);
  });

  it('la recherche ne garde que l\'item, le contexte reste cherchable', () => {
    const r = filterRuleSections([classes], searchTerms('magicien'));
    expect(r[0].blocks.map(b => b.markdown)).toEqual(['- **Magicien** : d6 PV,\n  livre de sorts.']);
    expect(filterRuleSections([classes], searchTerms('jouables roublard'))[0].blocks).toHaveLength(1);
  });

  it('rendu : contexte une seule fois, items consécutifs en liste serrée', () => {
    expect(blocksToMarkdown(classes.blocks)).toBe(
      'Classes jouables :\n\n- **Barbare** : d12 PV.\n- **Magicien** : d6 PV,\n  livre de sorts.\n- **Roublard** : d8 PV.'
    );
    expect(blocksToMarkdown([classes.blocks[2]])).toBe('Classes jouables :\n\n- **Roublard** : d8 PV.');
  });

  it('une liste d\'un seul item reste un bloc normal', () => {
    const [s] = parseRuleSections('## A\nIntro\n- seul', 'I');
    expect(s.blocks).toHaveLength(1);
    expect(s.blocks[0].group).toBeUndefined();
  });
});

describe('searchTerms', () => {
  it('normalise, ignore les mots d\'une lettre et les doublons', () => {
    expect(searchTerms('  Épée a ÉPÉE  sort ')).toEqual(['epee', 'sort']);
    expect(searchTerms('')).toEqual([]);
  });
});

describe('filterRuleSections', () => {
  const sections = parseRuleSections(RULES, 'Généralités');

  it('sans terme : tout', () => {
    expect(filterRuleSections(sections, [])).toHaveLength(4);
  });

  it('ne garde que les blocs qui correspondent', () => {
    const r = filterRuleSections(sections, searchTerms('opportunite'));
    expect(r).toHaveLength(1);
    expect(r[0].section.title).toBe('Combat');
    expect(r[0].blocks).toHaveLength(1);
    expect(r[0].titleMatch).toBe(false);
  });

  it('le titre de section sert de contexte (ET entre les termes)', () => {
    const r = filterRuleSections(sections, searchTerms('combat allonge'));
    expect(r.map(m => m.section.title)).toEqual(['Combat']);
    expect(r[0].blocks.map(b => b.markdown)).toEqual([expect.stringContaining('allonge')]);
    expect(filterRuleSections(sections, searchTerms('magie allonge'))).toEqual([]);
  });

  it('titre correspondant : section entière, placée en tête', () => {
    const r = filterRuleSections(sections, searchTerms('magie'));
    expect(r[0].section.title).toBe('Magie');
    expect(r[0].titleMatch).toBe(true);
    expect(r[0].blocks).toHaveLength(2);
  });

  it('cherche dans les tableaux, sans accents', () => {
    expect(filterRuleSections(sections, searchTerms('epee'))[0].section.title).toBe('Équipement');
  });
});

describe('highlightText', () => {
  it('surligne sans tenir compte des accents ni de la casse, en gardant le texte d\'origine', () => {
    expect(highlightText('Une Épée et une epee', ['epee'])).toBe('Une <mark>Épée</mark> et une <mark>epee</mark>');
  });

  it('fusionne les correspondances qui se chevauchent', () => {
    expect(highlightText('initiative', ['init', 'itia'])).toBe('<mark>initia</mark>tive');
  });

  it('surligne une ligature trouvée par sa forme simple', () => {
    expect(highlightText('Le cœur', ['coeur'])).toBe('Le <mark>cœur</mark>');
  });

  it('échappe le HTML', () => {
    expect(highlightText('a < b & sort', ['sort'])).toBe('a &lt; b &amp; <mark>sort</mark>');
  });

  it('gère une forme décomposée (NFD) dans le texte', () => {
    const decomposed = 'Épée'.normalize('NFD');
    expect(highlightText(decomposed, ['epee'])).toBe(`<mark>${decomposed}</mark>`);
  });
});

describe('highlightHtml', () => {
  it('ne touche ni aux balises ni aux attributs', () => {
    const html = '<p><a href="/sort">Le sort</a> &amp; la <strong>magie</strong></p>';
    expect(highlightHtml(html, ['sort', 'magie'])).toBe(
      '<p><a href="/sort">Le <mark>sort</mark></a> &amp; la <strong><mark>magie</mark></strong></p>'
    );
  });

  it('décode les entités avant de chercher', () => {
    expect(highlightHtml('<p>l&#39;attaque</p>', ["l'attaque"])).toBe("<p><mark>l'attaque</mark></p>");
  });

  it('sans terme : HTML inchangé', () => {
    expect(highlightHtml('<p>x</p>', [])).toBe('<p>x</p>');
  });
});
