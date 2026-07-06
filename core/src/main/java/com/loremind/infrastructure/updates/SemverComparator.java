package com.loremind.infrastructure.updates;

import org.springframework.lang.Nullable;

import java.util.List;

/**
 * Parsing et comparaison de versions semver (stratégie de {@link UpdateCheckService}).
 * <p>
 * Volontairement tolérant : préfixe {@code v}/{@code V} accepté, pré-release
 * ({@code -beta.1}) et build metadata ({@code +build.42}) strippés avant comparaison.
 * Un tag non parsable est simplement ignoré (jamais d'exception). Fonctions PURES.
 */
final class SemverComparator {

    private SemverComparator() {
    }

    /**
     * Parcourt la liste des tags, garde uniquement ceux qui parsent en semver
     * (1 à 3 chiffres séparés par des points, préfixe {@code v} optionnel),
     * retourne le plus élevé, ou {@code null} si aucun n'est valide.
     */
    @Nullable
    static String findMaxSemver(List<String> tags) {
        String maxTag = null;
        int[] maxParts = null;
        for (String t : tags) {
            int[] parts = (t == null || t.isBlank()) ? null : parseSemver(t);
            if (parts == null) continue;
            if (maxParts == null || compareParts(parts, maxParts) > 0) {
                maxParts = parts;
                maxTag = t;
            }
        }
        return maxTag;
    }

    /** @return {@code [major, minor, patch]} ou {@code null} si non parsable. */
    @Nullable
    static int[] parseSemver(String tag) {
        if (tag == null) return null;
        String s = tag.trim();
        if (s.isEmpty()) return null;
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        int dashIdx = s.indexOf('-');
        if (dashIdx > 0) s = s.substring(0, dashIdx);
        int plusIdx = s.indexOf('+');
        if (plusIdx > 0) s = s.substring(0, plusIdx);
        String[] parts = s.split("\\.");
        if (parts.length < 1 || parts.length > 3) return null;
        int[] result = new int[]{0, 0, 0};
        for (int i = 0; i < parts.length; i++) {
            try {
                int v = Integer.parseInt(parts[i]);
                if (v < 0) return null;
                result[i] = v;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return result;
    }

    /** Compare deux versions semver brutes (préfixe toléré). Négatif si {@code a < b}. */
    static int compareSemver(String a, String b) {
        int[] aParts = parseSemver(a);
        int[] bParts = parseSemver(b);
        if (aParts == null || bParts == null) return 0;
        return compareParts(aParts, bParts);
    }

    private static int compareParts(int[] a, int[] b) {
        for (int i = 0; i < 3; i++) {
            int diff = Integer.compare(a[i], b[i]);
            if (diff != 0) return diff;
        }
        return 0;
    }
}
