package com.loremind.application.lorecontext;

import com.loremind.domain.lorecontext.LoreNode;
import com.loremind.domain.lorecontext.Page;
import com.loremind.domain.lorecontext.ports.LoreNodeRepository;
import com.loremind.domain.lorecontext.ports.PageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
    private final PageRepository pageRepository;

    public LoreNodeService(LoreNodeRepository loreNodeRepository, PageRepository pageRepository) {
        this.loreNodeRepository = loreNodeRepository;
        this.pageRepository = pageRepository;
    }

    /**
     * Compte des entités qui seront supprimées en cascade si le dossier est effacé :
     * le dossier lui-même n'est pas compté, seuls les descendants (sous-dossiers
     * récursifs + pages de l'ensemble du sous-arbre).
     */
    public record DeletionImpact(int folders, int pages) {}

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

    /**
     * Calcule l'impact d'une suppression en cascade : nombre de sous-dossiers
     * (récursif, sans compter la racine) et de pages dans l'ensemble du sous-arbre.
     */
    public DeletionImpact getDeletionImpact(String id) {
        List<LoreNode> descendants = collectDescendants(id);
        int pageTotal = pageRepository.findByNodeId(id).size();
        for (LoreNode descendant : descendants) {
            pageTotal += pageRepository.findByNodeId(descendant.getId()).size();
        }
        return new DeletionImpact(descendants.size(), pageTotal);
    }

    /**
     * Supprime le dossier et tout son sous-arbre (sous-dossiers récursifs + pages).
     * Suppression en profondeur d'abord (feuilles → racine) pour limiter les
     * références orphelines en cours de transaction. Les FKs applicatives n'ayant
     * pas de CASCADE en DB, on orchestre la descente ici.
     */
    @Transactional
    public void deleteLoreNode(String id) {
        List<LoreNode> descendants = collectDescendants(id);
        // Descendants retournés en ordre BFS (haut → bas) : on inverse pour
        // supprimer les feuilles en premier, puis on finit par la racine.
        for (int i = descendants.size() - 1; i >= 0; i--) {
            String descendantId = descendants.get(i).getId();
            deletePagesOfNode(descendantId);
            loreNodeRepository.deleteById(descendantId);
        }
        deletePagesOfNode(id);
        loreNodeRepository.deleteById(id);
    }

    private void deletePagesOfNode(String nodeId) {
        for (Page page : pageRepository.findByNodeId(nodeId)) {
            pageRepository.deleteById(page.getId());
        }
    }

    /**
     * Retourne tous les descendants (hors racine) d'un dossier, en ordre BFS.
     * Parcours itératif pour éviter tout risque de débordement de pile sur
     * une arborescence profonde malicieuse.
     */
    private List<LoreNode> collectDescendants(String rootId) {
        List<LoreNode> result = new ArrayList<>();
        List<String> frontier = new ArrayList<>();
        frontier.add(rootId);
        while (!frontier.isEmpty()) {
            List<String> nextFrontier = new ArrayList<>();
            for (String parentId : frontier) {
                for (LoreNode child : loreNodeRepository.findByParentId(parentId)) {
                    result.add(child);
                    nextFrontier.add(child.getId());
                }
            }
            frontier = nextFrontier;
        }
        return result;
    }
}
