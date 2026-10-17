import { WritingRemark } from '../services/writing-review.model';
import { GapAction } from './gap-action.helper';

/**
 * Lien « Voir » d'une remarque de relecture : ouvre l'éditeur concerné avec la
 * section en cause dépliée (`?focus=…`), comme le « Corriger » du readiness.
 */
export function remarkAction(remark: WritingRemark, campaignId: string): GapAction {
  const base = ['/campaigns', campaignId];
  const focus = FOCUS_BY_RULE[remark.ruleId];
  const queryParams = focus ? { focus } : undefined;
  switch (remark.entityType) {
    case 'SCENE':
      return {
        link: [...base, 'arcs', remark.arcId ?? '', 'chapters', remark.chapterId ?? '', 'scenes', remark.entityId, 'edit'],
        queryParams
      };
    case 'CHAPTER':
      // Couloir / rythme : c'est l'enchaînement des scènes qu'il faut voir (vue du chapitre).
      if (!focus) return { link: [...base, 'arcs', remark.arcId ?? '', 'chapters', remark.entityId] };
      return { link: [...base, 'arcs', remark.arcId ?? '', 'chapters', remark.entityId, 'edit'], queryParams };
    case 'ARC':
      return { link: [...base, 'arcs', remark.entityId, 'edit'], queryParams };
    default:
      return { link: base };
  }
}

/** Section de l'éditeur à déplier selon la règle (clé = ruleId complet). */
const FOCUS_BY_RULE: Record<string, string> = {
  'WRITE-002-FAKE-CHOICE': 'branches',
  'WRITE-003-SINGLE-CLUE': 'branches',
  'WRITE-005-NO-STAKES': 'stakes',
  'WRITE-006-LONG-NARRATION': 'narration',
  'WRITE-007-NARRATION-DECIDES': 'narration',
  'WRITE-008-COMBAT-NO-PURPOSE': 'combat',
  'WRITE-009-SINGLE-ENDING': 'choices'
};
