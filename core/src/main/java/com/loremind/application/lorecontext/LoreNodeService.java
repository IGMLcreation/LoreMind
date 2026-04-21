package com.loremind.application.lorecontext;

import com.loremind.domain.lorecontext.LoreNode;
import com.loremind.domain.lorecontext.ports.LoreNodeRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service d'application pour le contexte LoreNode.
 * Orchestre la logique métier en utilisant le Port LoreNodeRepository.
 * Fait partie de la couche Application de l'Architecture Hexagonale.
 */
@Service
public class LoreNodeService {

    private final LoreNodeRepository loreNodeRepository;

    public LoreNodeService(LoreNodeRepository loreNodeRepository) {
        this.loreNodeRepository = loreNodeRepository;
    }

    /**
     * Crée un LoreNode (dossier) à partir d'un "objet changes" porteur des valeurs
     * souhaitées (pattern Parameter Object) : évite les signatures qui gonflent
     * à chaque ajout de champ.
     */
    public LoreNode createLoreNode(LoreNode changes) {
        LoreNode loreNode = LoreNode.builder()
                .name(changes.getName())
                .icon(changes.getIcon())
                .parentId(changes.getParentId())
                .loreId(changes.getLoreId())
                .build();
        return loreNodeRepository.save(loreNode);
    }

    public Optional<LoreNode> getLoreNodeById(String id) {
        return loreNodeRepository.findById(id);
    }

    public List<LoreNode> getAllLoreNodes() {
        return loreNodeRepository.findAll();
    }

    public List<LoreNode> getLoreNodesByLoreId(String loreId) {
        return loreNodeRepository.findByLoreId(loreId);
    }

    public List<LoreNode> getLoreNodesByParentId(String parentId) {
        return loreNodeRepository.findByParentId(parentId);
    }

    public List<LoreNode> searchLoreNodes(String query) {
        if (query == null || query.isBlank()) return List.of();
        return loreNodeRepository.searchByName(query.trim());
    }

    public LoreNode updateLoreNode(String id, LoreNode changes) {
        LoreNode existing = loreNodeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("LoreNode non trouvé avec l'ID: " + id));

        existing.setName(changes.getName());
        existing.setIcon(changes.getIcon());
        existing.setParentId(changes.getParentId());
        // loreId volontairement immuable (un dossier ne migre pas d'un Lore à l'autre).
        return loreNodeRepository.save(existing);
    }

    public void deleteLoreNode(String id) {
        loreNodeRepository.deleteById(id);
    }
}
