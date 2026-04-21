package com.loremind.domain.lorecontext.ports;

import com.loremind.domain.lorecontext.LoreNode;
import java.util.List;
import java.util.Optional;

/**
 * Port de sortie pour la persistance des LoreNodes.
 * Interface définie dans le domaine, implémentée par l'infrastructure.
 */
public interface LoreNodeRepository {

    LoreNode save(LoreNode loreNode);
    
    Optional<LoreNode> findById(String id);
    
    List<LoreNode> findByLoreId(String loreId);
    
    List<LoreNode> findByParentId(String parentId);
    
    List<LoreNode> findAll();

    void deleteById(String id);

    boolean existsById(String id);

    long countByLoreId(String loreId);

    List<LoreNode> searchByName(String query);
}
