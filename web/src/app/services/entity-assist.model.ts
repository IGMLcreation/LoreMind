/**
 * Modèles du Pilier A (co-création « propose → applique »), génériques par type
 * d'entité narrative (arc / chapitre / scène). Miroir des records domaine.
 */

/** Proposition IA pour un champ (clé alignée sur les contrôles du formulaire de l'entité). */
export interface FieldProposal {
  key: string;
  currentValue: string;
  proposedValue: string;
}

export interface EntityFieldPatchProposal {
  target: string;
  targetId: string;
  type: string;
  fields: FieldProposal[];
}
