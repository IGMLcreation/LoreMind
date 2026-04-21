package com.loremind.domain.shared;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utilitaires pour la manipulation défensive des collections.
 * Cœur du domaine - aucune dépendance technique.
 */
public final class CollectionUtils {

    private CollectionUtils() {
    }

    /**
     * Copie défensive d'une Map. Retourne une Map vide si source est null.
     */
    public static <K, V> Map<K, V> copyMap(Map<K, V> source) {
        return source != null ? new HashMap<>(source) : new HashMap<>();
    }

    /**
     * Copie défensive d'une List. Retourne une List vide si source est null.
     */
    public static <T> List<T> copyList(List<T> source) {
        return source != null ? new ArrayList<>(source) : new ArrayList<>();
    }
}
