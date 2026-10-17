package com.loremind.infrastructure.ai;

import com.loremind.domain.campaigncontext.ports.GuidedMethodAssistant;
import com.loremind.domain.campaigncontext.ports.exceptions.NarrativeAssistException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter de sortie : propositions de réponses aux méthodes guidées via le Brain
 * ({@code POST /generate/guided-answers}, one-shot). La langue de l'interface est relayée par
 * l'intercepteur du RestTemplate (en-tête {@code X-User-Language}).
 */
@Component
public class BrainGuidedAnswersClient implements GuidedMethodAssistant {

    private static final String PATH = "/generate/guided-answers";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public BrainGuidedAnswersClient(RestTemplate restTemplate, @Value("${brain.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    @Override
    public Map<String, String> answer(String methodName, String principle, String idea, String context,
                                      List<Question> questions, Map<String, String> existing) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("method_name", nz(methodName));
        req.put("method_principle", nz(principle));
        req.put("idea", nz(idea));
        req.put("context", nz(context));
        req.put("fields", toPayload(questions));
        req.put("existing", existing == null ? Map.of() : existing);
        return parse(call(req));
    }

    static List<Map<String, Object>> toPayload(List<Question> questions) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Question q : questions) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("key", q.key());
            f.put("label", nz(q.label()).isEmpty() ? q.key() : q.label());
            f.put("hint", nz(q.hint()));
            f.put("step", nz(q.step()));
            List<Map<String, String>> options = new ArrayList<>();
            if (q.options() != null) {
                for (Choice c : q.options()) {
                    if (c != null && c.value() != null) options.add(Map.of("value", c.value(), "label", nz(c.label())));
                }
            }
            f.put("options", options);
            out.add(f);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(Map<String, Object> req) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> resp;
        try {
            resp = restTemplate.postForObject(baseUrl + PATH, new HttpEntity<>(req, headers), Map.class);
        } catch (ResourceAccessException e) {
            throw new NarrativeAssistException("Le Brain est injoignable (timeout ou arrêté).", e);
        } catch (RestClientResponseException e) {
            throw new NarrativeAssistException(
                    "Le Brain a répondu HTTP " + e.getStatusCode().value() + " : " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new NarrativeAssistException("Erreur inattendue lors de l'appel au Brain.", e);
        }
        if (resp == null) throw new NarrativeAssistException("Le Brain a renvoyé une réponse vide.");
        return resp;
    }

    private static Map<String, String> parse(Map<String, Object> resp) {
        Map<String, String> out = new LinkedHashMap<>();
        if (resp.get("answers") instanceof Map<?, ?> m) {
            m.forEach((k, v) -> {
                if (k != null && v != null) out.put(k.toString(), v.toString());
            });
        }
        return out;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
