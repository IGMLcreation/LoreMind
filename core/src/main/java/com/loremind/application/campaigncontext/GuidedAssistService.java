package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.ports.GuidedMethodAssistant;
import com.loremind.domain.campaigncontext.ports.GuidedMethodAssistant.Choice;
import com.loremind.domain.campaigncontext.ports.GuidedMethodAssistant.Question;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * IA des méthodes guidées (Mentor d'écriture) : propose des réponses aux questions d'une
 * méthode à partir de l'idée de départ du MJ et de la campagne. Rien n'est enregistré : le
 * front remplit les champs vides et le MJ relit, corrige ou relance.
 *
 * <p>Garde-fous : nombre de questions et longueur des textes bornés (le prompt reste
 * raisonnable) ; en retour, seules les clés demandées sont gardées, et une question à choix
 * n'accepte qu'une des valeurs proposées.</p>
 */
@Service
public class GuidedAssistService {

    static final int MAX_QUESTIONS = 80;
    static final int MAX_TEXT = 4_000;

    private final GuidedMethodAssistant assistant;
    private final CampaignContextFormatter contextFormatter;

    public GuidedAssistService(GuidedMethodAssistant assistant, CampaignContextFormatter contextFormatter) {
        this.assistant = assistant;
        this.contextFormatter = contextFormatter;
    }

    /** Demande à proposer des réponses. @throws IllegalArgumentException aucune question valide */
    public Map<String, String> suggest(String campaignId, String methodName, String principle, String idea,
                                       List<Question> questions, Map<String, String> existing) {
        List<Question> valid = questions == null ? List.of() : questions.stream()
                .filter(Objects::nonNull)
                .filter(q -> q.key() != null && !q.key().isBlank())
                .limit(MAX_QUESTIONS)
                .toList();
        if (valid.isEmpty()) {
            throw new IllegalArgumentException("Aucune question à laquelle répondre.");
        }
        Map<String, String> answers = assistant.answer(cap(methodName), cap(principle), cap(idea),
                contextFormatter.format(campaignId), valid, capAll(existing));
        return keepValid(answers, valid);
    }

    /** Clés demandées uniquement, valeurs non vides ; choix limités aux valeurs proposées. */
    static Map<String, String> keepValid(Map<String, String> answers, List<Question> questions) {
        Map<String, String> out = new LinkedHashMap<>();
        if (answers == null) return out;
        Map<String, Question> byKey = questions.stream()
                .collect(Collectors.toMap(Question::key, q -> q, (a, b) -> a, LinkedHashMap::new));
        for (Map.Entry<String, String> e : answers.entrySet()) {
            Question q = byKey.get(e.getKey());
            String value = e.getValue() == null ? "" : e.getValue().trim();
            if (q == null || value.isEmpty()) continue;
            if (q.options() != null && !q.options().isEmpty()) {
                Set<String> allowed = q.options().stream().map(Choice::value).collect(Collectors.toSet());
                if (!allowed.contains(value)) continue;
            }
            out.put(q.key(), cap(value));
        }
        return out;
    }

    private static Map<String, String> capAll(Map<String, String> existing) {
        Map<String, String> out = new LinkedHashMap<>();
        if (existing == null) return out;
        existing.forEach((k, v) -> {
            if (k != null && v != null && !v.isBlank()) out.put(k, cap(v.trim()));
        });
        return out;
    }

    private static String cap(String s) {
        if (s == null) return "";
        return s.length() > MAX_TEXT ? s.substring(0, MAX_TEXT) : s;
    }
}
