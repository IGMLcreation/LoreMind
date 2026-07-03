import { ReadinessGap } from '../services/readiness.model';

/**
 * Action de correction d'un manque de guidage : lien profond + query params.
 *
 * Le bouton « Corriger » ne se contente plus de NAVIGUER : selon la règle, il ouvre
 * directement le BON OUTIL sur la page cible —
 *  - chapitre vide → l'éditeur du chapitre avec le panneau « Générer des scènes » déployé ;
 *  - manques de scène → l'ÉDITEUR de la scène avec la section fautive dépliée
 *    (combat / branches / lieu explorable) ;
 *  - manques de quête (nœud mort, prérequis cassé, sans nœud) → l'éditeur de la quête.
 */
export interface GapAction {
  link: string[];
  queryParams?: Record<string, string>;
}

export function gapAction(gap: ReadinessGap, campaignId: string): GapAction {
  const base = ['/campaigns', campaignId];
  switch (gap.entityType) {
    case 'SCENE': {
      const link = [...base, 'arcs', gap.arcId ?? '', 'chapters', gap.chapterId ?? '', 'scenes', gap.entityId, 'edit'];
      const focus = sceneFocusFor(gap.ruleId);
      return { link, queryParams: focus ? { focus } : undefined };
    }
    case 'CHAPTER':
      if (gap.ruleId === 'CHAP-001-NO-SCENE') {
        return {
          link: [...base, 'arcs', gap.arcId ?? '', 'chapters', gap.entityId, 'edit'],
          queryParams: { assist: 'draft-scenes' }
        };
      }
      return { link: [...base, 'arcs', gap.arcId ?? '', 'chapters', gap.entityId] };
    case 'ARC':
      return { link: [...base, 'arcs', gap.entityId] };
    case 'QUEST':
      // Nœuds et prérequis s'éditent sur quest-edit — pas sur la fiche de lecture.
      return { link: [...base, 'quests', gap.entityId, 'edit'] };
    case 'NPC':
      return { link: [...base, 'npcs', gap.entityId] };
    case 'ENEMY':
      return { link: [...base, 'enemies', gap.entityId] };
    default:
      return { link: base };
  }
}

/** Section de l'éditeur de scène à déplier selon la règle en défaut. */
function sceneFocusFor(ruleId: string): string | undefined {
  if (ruleId.startsWith('SCENE-011') || ruleId.startsWith('SCENE-012')) return 'combat';
  if (ruleId.startsWith('SCENE-010')) return 'branches';
  if (ruleId.startsWith('SCENE-041') || ruleId.startsWith('SCENE-042')) return 'rooms';
  return undefined;
}
