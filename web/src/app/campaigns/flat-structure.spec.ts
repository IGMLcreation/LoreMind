import { flatContainer } from './flat-structure';
import { Arc, Chapter } from '../services/campaign.model';

describe('flatContainer (mode plat)', () => {
  const arc = (extra: Partial<Arc> = {}): Arc => ({ id: 'a1', name: 'Arc principal', campaignId: 'c', type: 'LINEAR', ...extra } as Arc);
  const chapter = (extra: Partial<Chapter> = {}): Chapter => ({ id: 'ch1', name: 'Chapitre 1', arcId: 'a1', ...extra } as Chapter);

  it('flattens a single empty arc with a single empty chapter', () => {
    expect(flatContainer([arc()], { a1: [chapter()] })?.chapter.id).toBe('ch1');
  });

  it('keeps the structure once the chapter or the arc has written content', () => {
    expect(flatContainer([arc()], { a1: [chapter({ gmNotes: 'Le notaire est coupable' })] })).toBeNull();
    expect(flatContainer([arc()], { a1: [chapter({ description: "L'affaire commence" })] })).toBeNull();
    expect(flatContainer([arc({ hooks: '- Un ami disparaît' })], { a1: [chapter()] })).toBeNull();
    expect(flatContainer([arc({ illustrationImageIds: ['img'] })], { a1: [chapter()] })).toBeNull();
  });

  it('ignores names and blank text', () => {
    expect(flatContainer([arc({ name: 'Ma campagne', stakes: '   ' })], { a1: [chapter({ name: 'Début' })] })).not.toBeNull();
  });

  it('never flattens a HUB arc, several chapters or several arcs (system arc ignored)', () => {
    expect(flatContainer([arc({ type: 'HUB' })], { a1: [chapter()] })).toBeNull();
    expect(flatContainer([arc()], { a1: [chapter(), chapter({ id: 'ch2' })] })).toBeNull();
    expect(flatContainer([arc(), arc({ id: 'a2' })], { a1: [chapter()], a2: [] })).toBeNull();
    expect(flatContainer([arc(), arc({ id: 'sys', type: 'SYSTEM' })], { a1: [chapter()] })).not.toBeNull();
  });
});
