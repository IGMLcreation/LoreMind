package com.loremind.infrastructure.web.config;

import java.util.Set;

/**
 * Langue de l'utilisateur courant, portée par un ThreadLocal le temps d'une
 * requête HTTP entrante.
 * <p>
 * Le frontend Angular envoie son choix de langue (code court {@code fr}/{@code en})
 * via l'entête {@code X-User-Language}. {@link UserLanguageFilter} la capture ici,
 * et les clients du Brain ({@code RestTemplateConfig} pour les appels bloquants,
 * les clients WebClient pour le streaming) la relaient au Brain — qui rédige alors
 * ses réponses IA dans cette langue.
 * <p>
 * Repli systématique sur le français si rien n'est fourni (vieux client, appel interne).
 */
public final class UserLanguageHolder {

    /** Nom de l'entête HTTP relayant la langue, du frontend jusqu'au Brain. */
    public static final String HEADER = "X-User-Language";

    /** Langue par défaut quand l'entête est absent ou non reconnu. */
    public static final String DEFAULT = "fr";

    /** Langues supportées (alignées sur LanguageService Angular et NAMES côté Brain). */
    private static final Set<String> SUPPORTED = Set.of("fr", "en");

    private static final ThreadLocal<String> CURRENT = ThreadLocal.withInitial(() -> DEFAULT);

    private UserLanguageHolder() {
    }

    /**
     * Normalise un code/entête langue arbitraire vers un code supporté.
     * Tolère la casse, les variantes régionales ({@code en-US}) et un
     * {@code Accept-Language} complet ({@code fr-FR,fr;q=0.9}). Repli {@code DEFAULT}.
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT;
        }
        String primary = raw.split(",")[0].split(";")[0].trim().toLowerCase();
        String base = primary.split("-")[0];
        return SUPPORTED.contains(base) ? base : DEFAULT;
    }

    public static void set(String language) {
        CURRENT.set(normalize(language));
    }

    public static String get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
