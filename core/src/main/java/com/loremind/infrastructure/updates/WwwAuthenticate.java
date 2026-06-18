package com.loremind.infrastructure.updates;

import java.util.HashMap;
import java.util.Map;

/**
 * Parseur minimaliste des paramètres d'un challenge HTTP
 * {@code WWW-Authenticate: Bearer realm="...", service="...", scope="..."}
 * (cf. le flux de token du registry Docker dans {@link UpdateCheckService}).
 * <p>
 * Accepte les valeurs entre guillemets ({@code key="value"}) comme non quotées
 * ({@code key=value}), séparées par des virgules et/ou espaces. Fonction PURE.
 */
final class WwwAuthenticate {

    private WwwAuthenticate() {
    }

    /** Parse {@code key="value", key2=value2} en map clé→valeur (sans les guillemets). */
    static Map<String, String> parseParams(String s) {
        Map<String, String> out = new HashMap<>();
        int i = 0;
        int n = s.length();
        while (i < n) {
            while (i < n && (s.charAt(i) == ',' || s.charAt(i) == ' ')) i++;
            int eq = s.indexOf('=', i);
            if (eq < 0) break;
            String key = s.substring(i, eq).trim();
            int valStart = eq + 1;
            String val;
            if (valStart < n && s.charAt(valStart) == '"') {
                int valEnd = s.indexOf('"', valStart + 1);
                if (valEnd < 0) break;
                val = s.substring(valStart + 1, valEnd);
                i = valEnd + 1;
            } else {
                int valEnd = s.indexOf(',', valStart);
                if (valEnd < 0) valEnd = n;
                val = s.substring(valStart, valEnd).trim();
                i = valEnd;
            }
            out.put(key, val);
        }
        return out;
    }
}
