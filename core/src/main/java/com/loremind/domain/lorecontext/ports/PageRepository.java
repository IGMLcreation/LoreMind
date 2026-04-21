package com.loremind.domain.lorecontext.ports;

import com.loremind.domain.lorecontext.Page;
import java.util.List;
import java.util.Optional;

/**
 * Port de sortie pour la persistance des Pages.
 * Interface définie dans le domaine, implémentée par l'infrastructure.
 */
public interface PageRepository {

    Page save(Page page);

    Optional<Page> findById(String id);

    List<Page> findByLoreId(String loreId);

    List<Page> findByNodeId(String nodeId);

    List<Page> findAll();

    void deleteById(String id);

    boolean existsById(String id);

    long countByLoreId(String loreId);

    List<Page> searchByTitle(String query);
}
