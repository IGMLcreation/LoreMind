package com.loremind.domain.lorecontext.ports;

import com.loremind.domain.lorecontext.Template;
import java.util.List;
import java.util.Optional;

/**
 * Port de sortie pour la persistance des Templates.
 * Interface définie dans le domaine, implémentée par l'infrastructure.
 */
public interface TemplateRepository {

    Template save(Template template);

    Optional<Template> findById(String id);

    List<Template> findAll();

    /** Tous les templates rattachés à un Lore donné (pour le panneau sidebar). */
    List<Template> findByLoreId(String loreId);

    void deleteById(String id);

    boolean existsById(String id);

    List<Template> searchByName(String query);
}
