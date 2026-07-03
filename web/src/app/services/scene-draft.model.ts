/**
 * Modèles du Pilier A — capacité « create » : ébauches de scènes pour un chapitre.
 * Miroir des records domaine SceneDraft / SceneDraftProposal.
 */

export interface SceneDraft {
  name: string;
  description?: string;
  playerNarration?: string;
}

export interface SceneDraftProposal {
  chapterId: string;
  scenes: SceneDraft[];
}
