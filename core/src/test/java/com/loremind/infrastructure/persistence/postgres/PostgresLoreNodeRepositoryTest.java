package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.lorecontext.Lore;
import com.loremind.domain.lorecontext.LoreNode;
import com.loremind.domain.lorecontext.ports.LoreNodeRepository;
import com.loremind.domain.lorecontext.ports.LoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests d'integration pour PostgresLoreNodeRepository.
 * Pattern : @SpringBootTest + @Transactional -> rollback automatique apres
 * chaque test, pas de pollution inter-tests.
 */
@SpringBootTest
@Transactional
class PostgresLoreNodeRepositoryTest {

    @Autowired private LoreNodeRepository nodeRepository;
    @Autowired private LoreRepository loreRepository;

    private String loreId;

    @BeforeEach
    void setUp() {
        Lore lore = loreRepository.save(Lore.builder().name("Lore host").description("").build());
        this.loreId = lore.getId();
    }

    @Test
    void save_assignsId_andFindByIdReturnsNode() {
        LoreNode node = LoreNode.builder()
                .name("Personnages").icon("users").loreId(loreId).build();
        LoreNode saved = nodeRepository.save(node);

        assertNotNull(saved.getId());
        LoreNode found = nodeRepository.findById(saved.getId()).orElseThrow();
        assertEquals("Personnages", found.getName());
        assertEquals("users", found.getIcon());
        assertEquals(loreId, found.getLoreId());
    }

    @Test
    void findByLoreId_returnsOnlyNodesOfThatLore() {
        Lore other = loreRepository.save(Lore.builder().name("Other").description("").build());
        nodeRepository.save(LoreNode.builder().name("A").loreId(loreId).build());
        nodeRepository.save(LoreNode.builder().name("B").loreId(loreId).build());
        nodeRepository.save(LoreNode.builder().name("C").loreId(other.getId()).build());

        List<LoreNode> mine = nodeRepository.findByLoreId(loreId);
        assertEquals(2, mine.size());
    }

    @Test
    void findByParentId_returnsChildrenOfGivenParent() {
        LoreNode parent = nodeRepository.save(LoreNode.builder().name("Parent").loreId(loreId).build());
        nodeRepository.save(LoreNode.builder().name("Child1").parentId(parent.getId()).loreId(loreId).build());
        nodeRepository.save(LoreNode.builder().name("Child2").parentId(parent.getId()).loreId(loreId).build());
        nodeRepository.save(LoreNode.builder().name("Orphan").loreId(loreId).build());

        List<LoreNode> children = nodeRepository.findByParentId(parent.getId());
        assertEquals(2, children.size());
    }

    @Test
    void countByLoreId_countsNodesAccurately() {
        nodeRepository.save(LoreNode.builder().name("A").loreId(loreId).build());
        nodeRepository.save(LoreNode.builder().name("B").loreId(loreId).build());
        nodeRepository.save(LoreNode.builder().name("C").loreId(loreId).build());

        assertEquals(3, nodeRepository.countByLoreId(loreId));
    }

    @Test
    void searchByName_isCaseInsensitiveAndPartial() {
        nodeRepository.save(LoreNode.builder().name("Personnages").loreId(loreId).build());
        nodeRepository.save(LoreNode.builder().name("Lieux").loreId(loreId).build());
        nodeRepository.save(LoreNode.builder().name("Creatures").loreId(loreId).build());

        // Recherche partielle attendue — on valide qu'on trouve bien au moins le hit.
        List<LoreNode> hits = nodeRepository.searchByName("person");
        assertTrue(hits.stream().anyMatch(n -> n.getName().equals("Personnages")));
    }

    @Test
    void deleteById_removesNode_andExistsReturnsFalse() {
        LoreNode saved = nodeRepository.save(LoreNode.builder().name("X").loreId(loreId).build());
        assertTrue(nodeRepository.existsById(saved.getId()));

        nodeRepository.deleteById(saved.getId());

        assertFalse(nodeRepository.existsById(saved.getId()));
        assertTrue(nodeRepository.findById(saved.getId()).isEmpty());
    }

    @Test
    void save_updatesExistingNode_whenIdIsPresent() {
        LoreNode saved = nodeRepository.save(LoreNode.builder().name("old").loreId(loreId).build());
        saved.setName("new");
        nodeRepository.save(saved);

        LoreNode reloaded = nodeRepository.findById(saved.getId()).orElseThrow();
        assertEquals("new", reloaded.getName());
    }
}
