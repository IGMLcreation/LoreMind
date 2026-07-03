package com.loremind.domain.campaigncontext.ports;

import java.util.List;

/**
 * Port de sortie (Pilier A — co-création) : demande à l'IA d'ÉTOFFER les champs d'une
 * entité narrative (arc, chapitre ou scène). Implémenté par un client du Brain. One-shot.
 *
 * <p>Générique par {@code entityType} : le Core est la SOURCE DE VÉRITÉ des champs
 * (clé + libellé), le Brain ne fait que rédiger. Double garde-fou : whitelist stricte
 * de clés côté Core ET côté adapter.</p>
 */
public interface NarrativeFieldAssistant {

    /** Spécification d'un champ à proposer : clé technique + libellé lisible (pour le prompt). */
    record FieldSpec(String key, String label) {}

    /** Un champ proposé par l'IA (clé appartenant à la whitelist fournie). */
    record ProposedField(String key, String value) {}

    /**
     * Génère des propositions de valeurs pour les champs d'une entité narrative.
     *
     * @param entityType  {@code "arc"|"chapter"|"scene"} (pour la formulation du prompt)
     * @param context     contexte narratif compact (état actuel de l'entité + campagne)
     * @param instruction consigne libre optionnelle du MJ (peut être vide/nulle)
     * @param fields      champs autorisés (whitelist) avec leur libellé
     * @return champs proposés (uniquement des clés autorisées, valeurs non vides) ; vide
     *         si l'IA n'a rien de pertinent à proposer
     */
    List<ProposedField> assist(String entityType, String context, String instruction, List<FieldSpec> fields);
}
