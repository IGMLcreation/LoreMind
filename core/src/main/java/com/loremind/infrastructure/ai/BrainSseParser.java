package com.loremind.infrastructure.ai;

import com.loremind.domain.generationcontext.ChatUsage;
import org.springframework.stereotype.Component;

/**
 * Helper d'infrastructure : parse les payloads JSON véhiculés dans les
 * évènements SSE reçus du Brain Python.
 * <p>
 * Implémentation volontairement minimaliste (pas de Jackson ici) car les
 * schémas attendus sont figés et simples : {"token":"..."} et
 * {"system":N,"history":N,"current":N,"max":N}. Si la complexité augmente,
 * remplacer par un ObjectMapper + DTOs.
 */
@Component
public class BrainSseParser {

    /**
     * Parse un JSON {"system":N,"history":N,"current":N,"max":N} en ChatUsage.
     * Renvoie null si le payload est illisible — l'appelant décidera de ne
     * simplement pas propager l'usage (le stream token continue).
     */
    public ChatUsage parseUsage(String json) {
        if (json == null) return null;
        try {
            int system = extractIntField(json, "system");
            int history = extractIntField(json, "history");
            int current = extractIntField(json, "current");
            int max = extractIntField(json, "max");
            return new ChatUsage(system, history, current, max);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parse {"token":"..."} et renvoie la valeur du champ token (chaîne vide
     * ou null si introuvable).
     */
    public String parseToken(String json) {
        if (json == null) return null;
        int idx = json.indexOf("\"token\"");
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx);
        int firstQuote = json.indexOf('"', colon + 1);
        int lastQuote = json.lastIndexOf('"');
        if (firstQuote < 0 || lastQuote <= firstQuote) return null;
        return json.substring(firstQuote + 1, lastQuote)
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private int extractIntField(String json, String field) {
        String needle = "\"" + field + "\"";
        int idx = json.indexOf(needle);
        if (idx < 0) return 0;
        int colon = json.indexOf(':', idx);
        if (colon < 0) return 0;
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        if (end == start) return 0;
        return Integer.parseInt(json.substring(start, end));
    }
}
