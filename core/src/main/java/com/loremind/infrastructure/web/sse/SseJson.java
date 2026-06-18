package com.loremind.infrastructure.web.sse;

/**
 * Petits utilitaires d'écriture JSON pour les payloads SSE bâtis à la main par
 * les controllers de streaming (chat IA, notebook…).
 * <p>
 * Volontairement minimaliste (pas de Jackson) : les payloads concernés sont des
 * objets plats à un ou deux champs ({@code {"token":"…"}}, {@code {"message":"…"}})
 * écrits dans la boucle de streaming, où l'on veut éviter l'allocation d'un
 * ObjectMapper par token.
 */
public final class SseJson {

    private SseJson() {
    }

    /**
     * Encadre {@code raw} de guillemets et échappe les caractères JSON dangereux
     * (guillemet, antislash, retours chariot, tabulation, caractères de contrôle).
     * Renvoie {@code "\"\""} (chaîne JSON vide) si {@code raw} est {@code null}.
     */
    public static String escape(String raw) {
        if (raw == null) return "\"\"";
        StringBuilder sb = new StringBuilder(raw.length() + 2);
        sb.append('"');
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
