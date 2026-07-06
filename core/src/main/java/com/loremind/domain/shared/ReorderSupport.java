package com.loremind.domain.shared;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;

/**
 * Squelette commun du réordonnancement par glisser-déposer : parcourt la liste
 * ordonnée d'ids, charge chaque entité, lui applique sa position (et toute
 * réaffectation de parent via {@code applyOrder}), puis la sauve. Les ids inconnus
 * sont ignorés. Évite de recopier cette boucle dans chaque service ordonnable.
 */
public final class ReorderSupport {

    private ReorderSupport() {}

    /**
     * @param orderedIds liste ordonnée des ids (null = no-op)
     * @param finder     id → entité, absente si introuvable (typiquement {@code repository::findById})
     * @param applyOrder (entité, index) → pose l'ordre et l'éventuel parent
     * @param saver      persiste l'entité
     */
    public static <T> void reorder(List<String> orderedIds,
                                   Function<String, Optional<T>> finder,
                                   ObjIntConsumer<T> applyOrder,
                                   Consumer<T> saver) {
        if (orderedIds == null) return;
        int i = 0;
        for (String id : orderedIds) {
            int index = i;
            finder.apply(id).ifPresent(entity -> {
                applyOrder.accept(entity, index);
                saver.accept(entity);
            });
            i++;
        }
    }
}
