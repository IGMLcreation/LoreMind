package com.loremind.domain.campaigncontext.ports;

import java.util.List;
import java.util.Map;

/**
 * Port de sortie (Mentor d'écriture) : demande à l'IA de proposer des réponses aux
 * questions d'une méthode guidée à partir de l'idée de départ du MJ. Implémenté par un
 * client du Brain. One-shot ; rien n'est enregistré (le MJ relit et corrige dans l'assistant).
 */
public interface GuidedMethodAssistant {

    /** Choix possible d'une question à choix : valeur technique + libellé lisible. */
    record Choice(String value, String label) {}

    /**
     * Une question de la méthode, décrite par le front (libellés dans la langue du MJ).
     *
     * @param key     clé de réponse (ex. {@code node1.name})
     * @param label   libellé de la question
     * @param hint    aide (exemple de réponse attendue), optionnelle
     * @param step    titre de l'étape, optionnel
     * @param options choix possibles (vide pour une question libre)
     */
    record Question(String key, String label, String hint, String step, List<Choice> options) {}

    /**
     * @param methodName nom de la méthode (« Enquête en nœuds »…)
     * @param principle  principe de la méthode, en une phrase
     * @param idea       idée de départ du MJ (peut être vide)
     * @param context    contexte de la campagne (nom, description, système)
     * @param questions  questions auxquelles répondre
     * @param existing   réponses déjà écrites par le MJ (à respecter)
     * @return une réponse proposée par clé (clés demandées uniquement)
     */
    Map<String, String> answer(String methodName, String principle, String idea, String context,
                               List<Question> questions, Map<String, String> existing);
}
