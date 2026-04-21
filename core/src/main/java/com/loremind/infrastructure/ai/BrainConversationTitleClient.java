package com.loremind.infrastructure.ai;

import com.loremind.domain.conversationcontext.ConversationMessage;
import com.loremind.domain.conversationcontext.ports.ConversationTitleGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Adaptateur : appelle le Brain POST /summarize/conversation-title pour
 * obtenir un titre court a partir des premiers messages.
 *
 * Fallback volontairement silencieux : si le Brain est indisponible, on
 * renvoie un titre par defaut plutot que de casser l'UX chat.
 */
@Component
public class BrainConversationTitleClient implements ConversationTitleGenerator {

    private static final String PATH = "/summarize/conversation-title";
    private static final String FALLBACK = "Nouvelle conversation";

    private final WebClient webClient;

    public BrainConversationTitleClient(
            WebClient.Builder builder,
            @Value("${brain.base-url}") String baseUrl) {
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    public String generate(List<ConversationMessage> firstMessages) {
        if (firstMessages == null || firstMessages.isEmpty()) {
            return FALLBACK;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messages", firstMessages.stream()
                .map(m -> Map.<String, Object>of(
                        "role", m.getRole(),
                        "content", m.getContent() == null ? "" : m.getContent()))
                .collect(Collectors.toList()));

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = webClient.post()
                    .uri(PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(20))
                    .block();
            if (resp == null) return FALLBACK;
            Object title = resp.get("title");
            if (title == null) return FALLBACK;
            String s = title.toString().trim();
            return s.isEmpty() ? FALLBACK : s;
        } catch (Exception e) {
            return FALLBACK;
        }
    }
}
