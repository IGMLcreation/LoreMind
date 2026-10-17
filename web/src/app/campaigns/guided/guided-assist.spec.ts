import { IDEA_KEY, applySuggestions, buildSuggestRequest, hasIdea, isFillable, markTouched } from './guided-assist';
import { INVESTIGATION_METHOD } from './methods/investigation.method';
import { GuidedField } from './guided-method.model';

/** Traduction factice : renvoie la clé, sauf les noms pré-remplis (valeurs d'office). */
const t = (key: string) => key.endsWith('defaults.node1.name') ? 'Première piste' : key;
const field = (key: string): GuidedField =>
  INVESTIGATION_METHOD.steps.flatMap(s => s.fields ?? []).find(f => f.key === key)!;

describe('guided AI assist', () => {
  it('treats empty fields and untouched defaults as free for the AI', () => {
    expect(isFillable(INVESTIGATION_METHOD, field('truth'), {}, t)).toBe(true);
    expect(isFillable(INVESTIGATION_METHOD, field('node1.name'), { 'node1.name': 'Première piste' }, t)).toBe(true);
    expect(isFillable(INVESTIGATION_METHOD, field('node1.nature'), { 'node1.nature': 'location' }, t)).toBe(true);
    expect(isFillable(INVESTIGATION_METHOD, field('node1.nature'), { 'node1.nature': 'npc' }, t)).toBe(false);
    expect(isFillable(INVESTIGATION_METHOD, field('truth'), { truth: 'Le notaire' }, t)).toBe(false);
  });

  it('never considers a field the GM touched as free, even at its default value', () => {
    const answers = markTouched({ 'node1.name': 'Première piste' }, 'node1.name');
    expect(isFillable(INVESTIGATION_METHOD, field('node1.name'), answers, t)).toBe(false);
  });

  it('asks only free fields, sends what the GM wrote as context, and passes the idea', () => {
    const req = buildSuggestRequest(INVESTIGATION_METHOD, { [IDEA_KEY]: ' Une tour ', truth: 'Le notaire est coupable' }, t);
    expect(req.idea).toBe('Une tour');
    expect(req.existing).toEqual({ truth: 'Le notaire est coupable' });
    expect(req.questions.some(q => q.key === 'truth')).toBe(false);
    const nature = req.questions.find(q => q.key === 'node1.nature')!;
    expect(nature.options.map(o => o.value)).toEqual(['location', 'npc', 'event']);
    expect(nature.hint).toBe('');
    expect(req.methodName).toBe('guided.investigation.name');
  });

  it('sends the method as the GM follows it by hand: principle, then the role of each step', () => {
    const req = buildSuggestRequest(INVESTIGATION_METHOD, { [IDEA_KEY]: 'Une tour' }, t);
    const lines = req.principle.split('\n');
    expect(lines[0]).toBe('guided.investigation.tagline');
    expect(lines).toContain('- guided.investigation.steps.node1.title : guided.investigation.steps.node1.why');
    expect(req.principle).toContain('steps.proactive.why');
    expect(req.principle).not.toContain('steps.placement');
    expect(req.principle).not.toContain('steps.review');
    expect(req.principle).not.toContain('.example');
  });

  it('requires an idea before asking the AI', () => {
    expect(hasIdea({})).toBe(false);
    expect(hasIdea({ [IDEA_KEY]: '   ' })).toBe(false);
    expect(hasIdea({ [IDEA_KEY]: 'Une tour qui se rallume' })).toBe(true);
  });

  it('limits the questions to the requested step', () => {
    const req = buildSuggestRequest(INVESTIGATION_METHOD, {}, t, ['node2']);
    expect(req.questions.length).toBeGreaterThan(0);
    expect(req.questions.every(q => q.key.startsWith('node2.'))).toBe(true);
  });

  it('applies proposals to free fields only, and only valid choices', () => {
    const answers = { truth: 'Le notaire', 'node1.nature': 'location' };
    const { answers: next, filled } = applySuggestions(INVESTIGATION_METHOD, answers, {
      truth: 'Autre vérité', 'node1.nature': 'npc', 'node2.nature': 'dragon', title: "L'héritage", unknown: 'x'
    }, t);
    expect(next['truth']).toBe('Le notaire');
    expect(next['node1.nature']).toBe('npc');
    expect(next['node2.nature']).toBeUndefined();
    expect(next['title']).toBe("L'héritage");
    expect(next['unknown']).toBeUndefined();
    expect(filled).toBe(2);
  });
});
